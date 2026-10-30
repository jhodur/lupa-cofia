"""Métrica por hoja: ¿el modelo dice que la hoja tiene la enfermedad, sí o no?

No cuenta manchas: basta con una detección de esa clase en la foto.
Uso: python metricas_por_hoja.py <carpeta de evaluar.py>
"""
import json
import sys
from pathlib import Path

carpeta = Path(sys.argv[1])
datos = json.loads((carpeta / "detecciones_test.json").read_text(encoding="utf-8"))
NOMBRES = {0: "roya", 1: "minador", 2: "phoma", 3: "cercospora"}


def medir(tiene, dice):
    vp = sum(1 for t, d in zip(tiene, dice) if t and d)
    fn = sum(1 for t, d in zip(tiene, dice) if t and not d)
    fp = sum(1 for t, d in zip(tiene, dice) if not t and d)
    vn = sum(1 for t, d in zip(tiene, dice) if not t and not d)
    return {"con": vp + fn, "sin": fp + vn, "vp": vp, "fn": fn, "fp": fp, "vn": vn,
            "sensibilidad": vp / (vp + fn) if vp + fn else None,
            "especificidad": vn / (vn + fp) if vn + fp else None,
            "precision": vp / (vp + fp) if vp + fp else None,
            "exactitud": (vp + vn) / len(tiene)}


r = {}
for umbral in (0.25, 0.40, 0.60):
    fila = {}
    for c, nombre in NOMBRES.items():
        tiene = [c in p["gt"] for p in datos]
        dice = [any(d["clase"] == c and d["confianza"] > umbral for d in p["detecciones"]) for p in datos]
        fila[nombre] = medir(tiene, dice)
    tiene = [bool(p["gt"]) for p in datos]
    dice = [any(d["confianza"] > umbral for d in p["detecciones"]) for p in datos]
    fila["cualquier enfermedad"] = medir(tiene, dice)
    r[str(umbral)] = fila

(carpeta / "metricas_por_hoja.json").write_text(json.dumps(r, ensure_ascii=False, indent=1), encoding="utf-8")
for umbral, fila in r.items():
    print(f"\numbral {umbral} ({len(datos)} hojas)")
    print(f"{'':22}{'con':>4}{'sin':>5}{'vp':>5}{'fn':>4}{'fp':>4}{'vn':>5}{'sens':>7}{'espec':>7}{'prec':>7}{'exact':>7}")
    for n, m in fila.items():
        print(f"{n:22}{m['con']:>4}{m['sin']:>5}{m['vp']:>5}{m['fn']:>4}{m['fp']:>4}{m['vn']:>5}"
              f"{m['sensibilidad']:>7.2f}{m['especificidad']:>7.2f}{m['precision']:>7.2f}{m['exactitud']:>7.2f}")
