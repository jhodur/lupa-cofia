# Paridad .pt (Ultralytics) contra .onnx (onnxruntime)

- Modelo: `yolov12s_best.pt` → `yolov12s_best.onnx`, entrada 640 px, FP32
- Umbral de confianza 0.25, IoU de NMS 0.7; pareja = misma clase e IoU ≥ 0.9
- 30 imágenes de `C:\Users\User\Downloads\archive\BRACOL_REVIEWED_ANNOTATIONS\BRACOL_REVIEWED\test\images`

| Imagen | Cajas .pt | Cajas .onnx | Emparejadas | IoU medio | Dif. conf. máx. | Sin pareja | Clases iguales |
|---|---:|---:|---:|---:|---:|---:|:---:|
| 1007_jpg.rf.f92c7c16fe6892d3 | 0 | 0 | 0 | — | — | 0 | sí |
| 1037_jpg.rf.09316ebde4d29323 | 0 | 0 | 0 | — | — | 0 | sí |
| 1081_jpg.rf.7933dc5a02d6e5cb | 0 | 0 | 0 | — | — | 0 | sí |
| 1095_jpg.rf.895d36302a433d95 | 4 | 4 | 4 | 1.0000 | 0.0000 | 0 | sí |
| 1104_jpg.rf.786c705f9ad5c7b4 | 4 | 4 | 4 | 1.0000 | 0.0000 | 0 | sí |
| 1116_jpg.rf.784b7bf80ca8de8a | 0 | 0 | 0 | — | — | 0 | sí |
| 1118_jpg.rf.2c5a26f4f6a82b6b | 0 | 0 | 0 | — | — | 0 | sí |
| 1152_jpg.rf.b944af329b8139c4 | 0 | 0 | 0 | — | — | 0 | sí |
| 1194_jpg.rf.b835490a8d53924f | 0 | 0 | 0 | — | — | 0 | sí |
| 1209_jpg.rf.221330ff317e4561 | 3 | 3 | 3 | 1.0000 | 0.0000 | 0 | sí |
| 1218_jpg.rf.d06ff480d5b549ba | 0 | 0 | 0 | — | — | 0 | sí |
| 1232_jpg.rf.6991f4b48f8f8f20 | 5 | 5 | 5 | 1.0000 | 0.0000 | 0 | sí |
| 1245_jpg.rf.d4787f03d4025e5f | 5 | 5 | 5 | 1.0000 | 0.0000 | 0 | sí |
| 1262_jpg.rf.f48ab16b0d28cbca | 11 | 11 | 11 | 1.0000 | 0.0000 | 0 | sí |
| 1286_jpg.rf.b572635e8ed7b80b | 5 | 5 | 5 | 1.0000 | 0.0000 | 0 | sí |
| 1306_jpg.rf.4b40177008314073 | 15 | 15 | 15 | 1.0000 | 0.0000 | 0 | sí |
| 1402_jpg.rf.d6b51d743da7746e | 6 | 6 | 6 | 1.0000 | 0.0000 | 0 | sí |
| 1453_jpg.rf.3d704b0e2864da0d | 1 | 1 | 1 | 1.0000 | 0.0000 | 0 | sí |
| 1470_jpg.rf.83a932e65ac5602a | 27 | 27 | 27 | 1.0000 | 0.0000 | 0 | sí |
| 1474_jpg.rf.ac9bd3d113778b0d | 9 | 9 | 9 | 1.0000 | 0.0000 | 0 | sí |
| 1476_jpg.rf.88d428940db6ba60 | 10 | 10 | 10 | 1.0000 | 0.0000 | 0 | sí |
| 1479_jpg.rf.6e94847caa454994 | 8 | 8 | 8 | 1.0000 | 0.0000 | 0 | sí |
| 1487_jpg.rf.cc5d838a3e29c277 | 3 | 3 | 3 | 1.0000 | 0.0000 | 0 | sí |
| 1490_jpg.rf.8f438abfd41aadc0 | 2 | 2 | 2 | 1.0000 | 0.0000 | 0 | sí |
| 149_jpg.rf.8b6778190dea26f8b | 13 | 13 | 13 | 1.0000 | 0.0000 | 0 | sí |
| 1528_jpg.rf.a4e0c43ec467f34f | 11 | 11 | 11 | 1.0000 | 0.0000 | 0 | sí |
| 1529_jpg.rf.ae740bd9e83910f0 | 8 | 8 | 8 | 1.0000 | 0.0000 | 0 | sí |
| 153_jpg.rf.6e0bc93a0324ee7db | 35 | 35 | 35 | 1.0000 | 0.0000 | 0 | sí |
| 1541_jpg.rf.e27aea524471cefb | 9 | 9 | 9 | 1.0000 | 0.0000 | 0 | sí |
| 1544_jpg.rf.d2fe2b7068f811c7 | 22 | 22 | 22 | 1.0000 | 0.0000 | 0 | sí |
| **Total** | **216** | **216** | **216** | **1.0000** | **0.0000** | **0** | |

Cajas sin pareja lejos del umbral (más de ±0.02): 0

**Resultado: CUMPLE el criterio de paridad.**
