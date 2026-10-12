"""Exporta un .pt de Ultralytics a ONNX (FP32, sin cuantizar, sin NMS dentro del grafo).

Uso: python exportar.py [ruta al .pt] [imgsz]
El .onnx queda junto al .pt. Los nombres de clase y el tamaño de entrada van en los metadatos.
"""
import sys
from pathlib import Path

from ultralytics import YOLO

ruta = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).parent / "pesos" / "yolov12s_best.pt")
imgsz = int(sys.argv[2]) if len(sys.argv) > 2 else 640

modelo = YOLO(str(ruta))
salida = modelo.export(format="onnx", imgsz=imgsz, opset=17, simplify=True, dynamic=False, half=False, nms=False)
print("exportado:", salida)
