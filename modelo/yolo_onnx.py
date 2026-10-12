"""Pre y posproceso de YOLO sobre ONNX escrito a mano (numpy + cv2).

Es la referencia del algoritmo que usa la app en Kotlin (Detector.kt): si se cambia aquí,
hay que cambiarlo allá.
"""
import ast

import cv2
import numpy as np
import onnxruntime as ort

RELLENO = 114


def cargar(ruta_onnx):
    sesion = ort.InferenceSession(str(ruta_onnx), providers=["CPUExecutionProvider"])
    meta = sesion.get_modelmeta().custom_metadata_map
    nombres = ast.literal_eval(meta["names"])
    entrada = sesion.get_inputs()[0]
    lado = int(entrada.shape[2])
    return sesion, nombres, lado


def letterbox(img_bgr, lado):
    """Escala manteniendo proporción y rellena hasta lado x lado. Devuelve imagen, escala y desplazamiento."""
    alto, ancho = img_bgr.shape[:2]
    r = min(lado / alto, lado / ancho)
    nuevo_ancho, nuevo_alto = round(ancho * r), round(alto * r)
    dw, dh = (lado - nuevo_ancho) / 2, (lado - nuevo_alto) / 2
    if (ancho, alto) != (nuevo_ancho, nuevo_alto):
        img_bgr = cv2.resize(img_bgr, (nuevo_ancho, nuevo_alto), interpolation=cv2.INTER_LINEAR)
    arriba, abajo = round(dh - 0.1), round(dh + 0.1)
    izq, der = round(dw - 0.1), round(dw + 0.1)
    img = cv2.copyMakeBorder(img_bgr, arriba, abajo, izq, der, cv2.BORDER_CONSTANT, value=(RELLENO,) * 3)
    return img, r, izq, arriba


def iou_uno_a_muchos(caja, cajas):
    x1 = np.maximum(caja[0], cajas[:, 0])
    y1 = np.maximum(caja[1], cajas[:, 1])
    x2 = np.minimum(caja[2], cajas[:, 2])
    y2 = np.minimum(caja[3], cajas[:, 3])
    inter = np.clip(x2 - x1, 0, None) * np.clip(y2 - y1, 0, None)
    a = (caja[2] - caja[0]) * (caja[3] - caja[1])
    b = (cajas[:, 2] - cajas[:, 0]) * (cajas[:, 3] - cajas[:, 1])
    return inter / (a + b - inter + 1e-9)


def nms(cajas, puntajes, umbral_iou):
    orden = np.argsort(-puntajes, kind="stable")
    quedan = []
    while orden.size:
        i = orden[0]
        quedan.append(i)
        if orden.size == 1:
            break
        resto = orden[1:]
        orden = resto[iou_uno_a_muchos(cajas[i], cajas[resto]) <= umbral_iou]
    return quedan


def posproceso(salida, r, izq, arriba, ancho, alto, conf=0.25, umbral_iou=0.7, max_det=300):
    """salida: [4+nc, N] con cx, cy, w, h en píxeles de la entrada y un puntaje por clase."""
    pred = salida.T  # [N, 4+nc]
    puntajes = pred[:, 4:]
    clase = puntajes.argmax(1)
    confianza = puntajes.max(1)
    ok = confianza > conf
    pred, clase, confianza = pred[ok], clase[ok], confianza[ok]
    cx, cy, w, h = pred[:, 0], pred[:, 1], pred[:, 2], pred[:, 3]
    cajas = np.stack([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2], 1)

    detecciones = []
    for c in np.unique(clase):
        idx = np.where(clase == c)[0]
        for k in nms(cajas[idx], confianza[idx], umbral_iou):
            i = idx[k]
            # de coordenadas de la entrada a coordenadas de la foto original
            x1 = float(np.clip((cajas[i, 0] - izq) / r, 0, ancho))
            y1 = float(np.clip((cajas[i, 1] - arriba) / r, 0, alto))
            x2 = float(np.clip((cajas[i, 2] - izq) / r, 0, ancho))
            y2 = float(np.clip((cajas[i, 3] - arriba) / r, 0, alto))
            detecciones.append({"clase": int(c), "confianza": float(confianza[i]), "caja": [x1, y1, x2, y2]})
    detecciones.sort(key=lambda d: -d["confianza"])
    return detecciones[:max_det]


def detectar(sesion, lado, img_bgr, conf=0.25, umbral_iou=0.7):
    alto, ancho = img_bgr.shape[:2]
    img, r, izq, arriba = letterbox(img_bgr, lado)
    tensor = img[:, :, ::-1].transpose(2, 0, 1)[None].astype(np.float32) / 255.0
    salida = sesion.run(None, {sesion.get_inputs()[0].name: np.ascontiguousarray(tensor)})[0][0]
    return posproceso(salida, r, izq, arriba, ancho, alto, conf, umbral_iou)
