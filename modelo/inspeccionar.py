"""Muestra qué trae el archivo de pesos: clases, tamaño de entrenamiento y versión.

Uso: python inspeccionar.py [ruta al .pt]
"""
import sys
from pathlib import Path

from ultralytics import YOLO

ESPERADO = {0: "rust", 1: "miner", 2: "phoma", 3: "cercospora"}

ruta = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).parent / "pesos" / "yolov12s_best.pt")
modelo = YOLO(str(ruta))
ckpt = modelo.ckpt or {}
args = ckpt.get("train_args", {}) or {}

print("archivo        :", ruta.name, f"({ruta.stat().st_size / 1e6:.1f} MB)")
print("tarea          :", modelo.task)
print("clases         :", modelo.names)
print("imgsz entreno  :", args.get("imgsz"))
print("modelo base    :", args.get("model"))
print("datos          :", args.get("data"))
print("ultralytics    :", ckpt.get("version"))
print("fecha          :", ckpt.get("date"))
print("parametros     :", sum(p.numel() for p in modelo.model.parameters()))
print("precision pesos:", next(modelo.model.parameters()).dtype)

nombres = {i: str(n).lower() for i, n in modelo.names.items()}
if nombres == ESPERADO:
    print("\nOK: el orden de clases coincide con el esperado.")
else:
    print("\nATENCION: el orden de clases NO coincide con el esperado:", ESPERADO)
    sys.exit(1)
