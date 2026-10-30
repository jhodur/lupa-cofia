"""Mide el modelo sobre un conjunto de prueba y escoge las fotos para la demo.

Uso: python evaluar.py <carpeta del split con images/ y labels/> <carpeta de salida>

Las etiquetas de BRACOL en Roboflow vienen en orden Cercospora, Miner, Phoma, Rust; el modelo usa
rust, miner, phoma, cercospora. Aquí se reordenan antes de medir.
Escribe metricas.json y detecciones_test.json en la carpeta de salida.
"""
import json
import shutil
import sys
from pathlib import Path

import cv2
import numpy as np
from ultralytics import YOLO

import yolo_onnx

AQUI = Path(__file__).parent
PT = AQUI / "pesos" / "yolov12s_best.pt"
ONNX = AQUI / "pesos" / "yolov12s_best.onnx"
REMAP = {0: 3, 1: 1, 2: 2, 3: 0}  # índice de Roboflow -> índice del modelo
CONF, IOU_MATCH = 0.25, 0.5

origen, salida = Path(sys.argv[1]), Path(sys.argv[2])
salida.mkdir(parents=True, exist_ok=True)

# Copia del split con las etiquetas reordenadas
ds = salida / "ds"
(ds / "images" / "test").mkdir(parents=True, exist_ok=True)
(ds / "labels" / "test").mkdir(parents=True, exist_ok=True)
imagenes = sorted(p for p in (origen / "images").iterdir() if p.suffix.lower() in (".jpg", ".jpeg", ".png"))
gt = {}
for img in imagenes:
    shutil.copy(img, ds / "images" / "test" / img.name)
    filas, cajas = [], []
    lab = origen / "labels" / (img.stem + ".txt")
    if lab.exists():
        for linea in lab.read_text().splitlines():
            v = linea.split()
            if len(v) != 5:  # polígonos u otra cosa: no se esperan en detección
                continue
            c = REMAP[int(v[0])]
            filas.append(" ".join([str(c)] + v[1:]))
            cajas.append((c, *map(float, v[1:])))
    (ds / "labels" / "test" / lab.name).write_text("\n".join(filas))
    gt[img.name] = cajas

modelo = YOLO(str(PT))
nombres = modelo.names
yaml = ds / "data.yaml"
yaml.write_text(f"path: {ds.as_posix()}\ntrain: images/test\nval: images/test\ntest: images/test\nnames: {json.dumps(list(nombres.values()))}\n")

m = modelo.val(data=str(yaml), split="test", imgsz=640, batch=8, device="cpu", plots=False, verbose=False,
               project=str(salida), name="val", exist_ok=True)
ultralytics = {
    "map50": float(m.box.map50), "map50_95": float(m.box.map),
    "precision": float(m.box.mp), "recall": float(m.box.mr),
    "por_clase": {nombres[int(c)]: {
        "precision": float(m.box.p[i]), "recall": float(m.box.r[i]),
        "map50": float(m.box.ap50[i]), "map50_95": float(m.box.ap[i]),
    } for i, c in enumerate(m.box.ap_class_index)},
}


def iou(a, b):
    x1, y1, x2, y2 = max(a[0], b[0]), max(a[1], b[1]), min(a[2], b[2]), min(a[3], b[3])
    inter = max(0, x2 - x1) * max(0, y2 - y1)
    return inter / ((a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter + 1e-9)


# Punto de operación real de la app: umbral 0,25 con el mismo algoritmo de Detector.kt
sesion, _, lado = yolo_onnx.cargar(ONNX)
tp, fp, fn = [dict.fromkeys(nombres, 0) for _ in range(3)]
por_imagen = []
for ruta in imagenes:
    img = cv2.imread(str(ruta))
    alto, ancho = img.shape[:2]
    dets = yolo_onnx.detectar(sesion, lado, img, CONF, 0.7)
    cajas_gt = [(c, (cx - w / 2) * ancho, (cy - h / 2) * alto, (cx + w / 2) * ancho, (cy + h / 2) * alto)
                for c, cx, cy, w, h in gt[ruta.name]]
    libres = list(range(len(cajas_gt)))
    fi = {"tp": 0, "fp": 0}
    for d in dets:
        mejor, mejor_iou = None, 0.0
        for j in libres:
            if cajas_gt[j][0] == d["clase"]:
                v = iou(d["caja"], cajas_gt[j][1:])
                if v > mejor_iou:
                    mejor, mejor_iou = j, v
        if mejor is not None and mejor_iou >= IOU_MATCH:
            libres.remove(mejor)
            tp[d["clase"]] += 1
            fi["tp"] += 1
        else:
            fp[d["clase"]] += 1
            fi["fp"] += 1
    for j in libres:
        fn[cajas_gt[j][0]] += 1
    por_imagen.append({"imagen": ruta.name, "ancho": ancho, "alto": alto, "gt": [c[0] for c in cajas_gt], "cajas_gt": cajas_gt,
                       "fn": len(libres), **fi, "detecciones": dets})

operacion = {}
for c, n in nombres.items():
    a = tp[c] + fn[c]
    b = tp[c] + fp[c]
    operacion[n] = {"lesiones": a, "tp": tp[c], "fp": fp[c], "fn": fn[c],
                    "recall": tp[c] / a if a else None, "precision": tp[c] / b if b else None}
total_tp, total_fp, total_fn = sum(tp.values()), sum(fp.values()), sum(fn.values())
operacion["todas"] = {"lesiones": total_tp + total_fn, "tp": total_tp, "fp": total_fp, "fn": total_fn,
                      "recall": total_tp / (total_tp + total_fn), "precision": total_tp / (total_tp + total_fp)}

r = {"imagenes": len(imagenes), "sin_lesiones": sum(1 for p in por_imagen if not p["gt"]),
     "sin_lesiones_y_sin_cajas": sum(1 for p in por_imagen if not p["gt"] and not p["detecciones"]),
     "ultralytics_conf_optima": ultralytics, "app_conf_0_25_iou_0_5": operacion}
(salida / "metricas.json").write_text(json.dumps(r, ensure_ascii=False, indent=1), encoding="utf-8")
(salida / "detecciones_test.json").write_text(json.dumps(por_imagen, ensure_ascii=False), encoding="utf-8")
print(json.dumps(r, ensure_ascii=False, indent=1))
