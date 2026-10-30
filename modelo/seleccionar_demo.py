"""Escoge fotos para la demo a partir de detecciones_test.json y arma recortes por enfermedad.

Uso: python seleccionar_demo.py <carpeta de evaluar.py> <carpeta de imágenes> <carpeta de salida>
"""
import json
import shutil
import sys
from pathlib import Path

import cv2

evald, imgs, salida = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
datos = json.loads((evald / "detecciones_test.json").read_text(encoding="utf-8"))
NOMBRES = {0: "roya", 1: "minador", 2: "phoma", 3: "cercospora"}
COLOR = {0: (0, 109, 255), 1: (255, 98, 41), 2: (255, 0, 170), 3: (0, 0, 213)}  # BGR, como la app
salida.mkdir(parents=True, exist_ok=True)
(salida / "con-recuadros").mkdir(exist_ok=True)
(salida / "recortes").mkdir(exist_ok=True)


def maximo(p, c):
    v = [d["confianza"] for d in p["detecciones"] if d["clase"] == c]
    return max(v) if v else 0.0


elegidas = []
for c, nombre in NOMBRES.items():
    # Sin errores, solo esa enfermedad y con confianza lejos de los cortes del semáforo
    cand = [p for p in datos if p["gt"] and set(p["gt"]) == {c} and p["fp"] == 0 and p["fn"] == 0 and maximo(p, c) >= 0.68]
    cand.sort(key=lambda p: (-len(p["gt"]) if c == 0 else 0, -maximo(p, c)))
    print(nombre, "candidatas limpias:", len(cand))
    for k, p in enumerate(cand[:2]):
        elegidas.append((f"{nombre}-{k + 1}-verde", p))

medias = [p for p in datos if p["gt"] and p["fp"] == 0 and p["detecciones"]
          and all(0.45 <= d["confianza"] <= 0.55 for d in p["detecciones"])]
print("amarillas:", len(medias))
for k, p in enumerate(medias[:1]):
    elegidas.append((f"semaforo-amarillo-{k + 1}", p))

limpias = [p for p in datos if not p["gt"] and not p["detecciones"]]
print("sin manchas:", len(limpias))
if limpias:
    elegidas.append(("sin-manchas", limpias[0]))

resumen = []
for nombre, p in elegidas:
    origen = imgs / p["imagen"]
    shutil.copy(origen, salida / f"{nombre}.jpg")
    img = cv2.imread(str(origen))
    for d in p["detecciones"]:
        x1, y1, x2, y2 = map(int, d["caja"])
        cv2.rectangle(img, (x1, y1), (x2, y2), COLOR[d["clase"]], 6)
    cv2.imwrite(str(salida / "con-recuadros" / f"{nombre}.jpg"), img)
    resumen.append({"foto": nombre, "original": p["imagen"], "lesiones_etiquetadas": len(p["gt"]),
                    "detecciones": [(NOMBRES[d["clase"]], round(d["confianza"], 2)) for d in p["detecciones"]]})
(salida / "fotos-demo.json").write_text(json.dumps(resumen, ensure_ascii=False, indent=1), encoding="utf-8")
print(json.dumps(resumen, ensure_ascii=False))

# Recortes para el pitch: la lesión etiquetada más grande de cada clase, en una imagen de esa sola enfermedad
for c, nombre in NOMBRES.items():
    mejor = None
    for p in datos:
        if set(p["gt"]) != {c} or p["fp"] or p["fn"]:
            continue
        for g in p["cajas_gt"]:
            area = (g[3] - g[1]) * (g[4] - g[2])
            if mejor is None or area > mejor[0]:
                mejor = (area, p, g)
    _, p, g = mejor
    img = cv2.imread(str(imgs / p["imagen"]))
    alto, ancho = img.shape[:2]
    cx, cy = (g[1] + g[3]) / 2, (g[2] + g[4]) / 2
    lado = int(min(max((g[3] - g[1]), (g[4] - g[2])) * 2.2, alto))
    lado = max(lado, 320)
    x = int(min(max(cx - lado / 2, 0), ancho - lado))
    y = int(min(max(cy - lado / 2, 0), alto - lado))
    cv2.imwrite(str(salida / "recortes" / f"{nombre}.jpg"), cv2.resize(img[y:y + lado, x:x + lado], (600, 600)),
                [cv2.IMWRITE_JPEG_QUALITY, 90])
    print("recorte", nombre, p["imagen"][:20], lado)
