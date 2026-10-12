"""Compara el modelo original (.pt, Ultralytics) contra el exportado (.onnx, onnxruntime).

Uso: python paridad.py <carpeta de imágenes> [n=30] [ruta al .pt]
Escribe paridad.md, paridad.csv y referencia.json junto a este archivo.
"""
import csv
import json
import sys
from pathlib import Path

import cv2
import numpy as np
from ultralytics import YOLO

import yolo_onnx

AQUI = Path(__file__).parent
PT = Path(sys.argv[3]) if len(sys.argv) > 3 else AQUI / "pesos" / "yolov12s_best.pt"
ONNX = PT.with_suffix(".onnx")
CONF, IOU_NMS, IOU_PAREJA, BORDE = 0.25, 0.7, 0.9, 0.02

carpeta = Path(sys.argv[1])
n = int(sys.argv[2]) if len(sys.argv) > 2 else 30
imagenes = sorted(p for p in carpeta.iterdir() if p.suffix.lower() in (".jpg", ".jpeg", ".png"))[:n]

original = YOLO(str(PT))
sesion, nombres, lado = yolo_onnx.cargar(ONNX)
assert {int(k): v for k, v in nombres.items()} == original.names, "las clases del ONNX no son las del .pt"


def iou(a, b):
    x1, y1, x2, y2 = max(a[0], b[0]), max(a[1], b[1]), min(a[2], b[2]), min(a[3], b[3])
    inter = max(0, x2 - x1) * max(0, y2 - y1)
    return inter / ((a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter + 1e-9)


filas, referencia = [], []
for ruta in imagenes:
    img = cv2.imread(str(ruta))
    # rect=False: mismo letterbox cuadrado que el ONNX, para que la única diferencia sea la exportación
    r = original.predict(img, conf=CONF, iou=IOU_NMS, imgsz=lado, rect=False, verbose=False)[0]
    a = [
        {"clase": int(c), "confianza": float(s), "caja": [float(v) for v in caja]}
        for caja, s, c in zip(r.boxes.xyxy.tolist(), r.boxes.conf.tolist(), r.boxes.cls.tolist())
    ]
    b = yolo_onnx.detectar(sesion, lado, img, CONF, IOU_NMS)

    # emparejar: cada caja de A con la de B de la misma clase y mayor IoU, sin repetir
    libres, ious, dconf, sin_pareja = list(range(len(b))), [], [], []
    for da in a:
        mejor, mejor_iou = None, 0.0
        for j in libres:
            if b[j]["clase"] == da["clase"]:
                v = iou(da["caja"], b[j]["caja"])
                if v > mejor_iou:
                    mejor, mejor_iou = j, v
        if mejor is not None and mejor_iou >= IOU_PAREJA:
            libres.remove(mejor)
            ious.append(mejor_iou)
            dconf.append(abs(da["confianza"] - b[mejor]["confianza"]))
        else:
            sin_pareja.append(da["confianza"])
    sin_pareja += [b[j]["confianza"] for j in libres]
    fuera_borde = [c for c in sin_pareja if abs(c - CONF) > BORDE]

    filas.append({
        "imagen": ruta.name, "cajas_pt": len(a), "cajas_onnx": len(b), "emparejadas": len(ious),
        "iou_medio": float(np.mean(ious)) if ious else None,
        "dif_conf_max": float(max(dconf)) if dconf else None,
        "sin_pareja": len(sin_pareja), "sin_pareja_fuera_borde": len(fuera_borde),
        "clases_pt": sorted(d["clase"] for d in a), "clases_onnx": sorted(d["clase"] for d in b),
    })
    referencia.append({"imagen": ruta.name, "ancho": img.shape[1], "alto": img.shape[0], "detecciones": b})

tot = {k: sum(f[k] for f in filas) for k in ("cajas_pt", "cajas_onnx", "emparejadas", "sin_pareja", "sin_pareja_fuera_borde")}
ious_todos = [f["iou_medio"] for f in filas if f["iou_medio"] is not None]
pesos = [f["emparejadas"] for f in filas if f["iou_medio"] is not None]
iou_global = float(np.average(ious_todos, weights=pesos)) if ious_todos else None
dif_global = max((f["dif_conf_max"] for f in filas if f["dif_conf_max"] is not None), default=None)
cumple = tot["sin_pareja_fuera_borde"] == 0 and (iou_global is None or iou_global >= 0.98)

fmt = lambda v, d=4: "—" if v is None else f"{v:.{d}f}"
md = [
    "# Paridad .pt (Ultralytics) contra .onnx (onnxruntime)", "",
    f"- Modelo: `{PT.name}` → `{ONNX.name}`, entrada {lado} px, FP32",
    f"- Umbral de confianza {CONF}, IoU de NMS {IOU_NMS}; pareja = misma clase e IoU ≥ {IOU_PAREJA}",
    f"- {len(filas)} imágenes de `{carpeta}`", "",
    "| Imagen | Cajas .pt | Cajas .onnx | Emparejadas | IoU medio | Dif. conf. máx. | Sin pareja | Clases iguales |",
    "|---|---:|---:|---:|---:|---:|---:|:---:|",
]
for f in filas:
    md.append(f"| {f['imagen'][:28]} | {f['cajas_pt']} | {f['cajas_onnx']} | {f['emparejadas']} | {fmt(f['iou_medio'])} | "
              f"{fmt(f['dif_conf_max'])} | {f['sin_pareja']} | {'sí' if f['clases_pt'] == f['clases_onnx'] else 'NO'} |")
md.append(f"| **Total** | **{tot['cajas_pt']}** | **{tot['cajas_onnx']}** | **{tot['emparejadas']}** | **{fmt(iou_global)}** | "
          f"**{fmt(dif_global)}** | **{tot['sin_pareja']}** | |")
md += ["", f"Cajas sin pareja lejos del umbral (más de ±{BORDE}): {tot['sin_pareja_fuera_borde']}",
       "", f"**Resultado: {'CUMPLE' if cumple else 'NO CUMPLE'} el criterio de paridad.**", ""]
(AQUI / "paridad.md").write_text("\n".join(md), encoding="utf-8", newline="\n")

with open(AQUI / "paridad.csv", "w", newline="", encoding="utf-8") as fh:
    w = csv.DictWriter(fh, fieldnames=list(filas[0].keys()))
    w.writeheader()
    w.writerows(filas)

(AQUI / "referencia.json").write_text(
    json.dumps({"modelo": ONNX.name, "lado": lado, "conf": CONF, "iou_nms": IOU_NMS, "nombres": original.names,
                "imagenes": referencia}, ensure_ascii=False, indent=1),
    encoding="utf-8", newline="\n")

print("\n".join(md))
sys.exit(0 if cumple else 1)
