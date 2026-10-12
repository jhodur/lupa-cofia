# Hojas de café — app Android de prueba

Prototipo de investigación. Toma la foto de una hoja de café, corre en el teléfono un detector
YOLOv12-s entrenado sobre BRACOL y muestra las lesiones que parece tener (roya, minador, phoma,
cercospora). Guarda cada foto con lo que dijo el modelo para evaluarlo después con datos de campo.

No es un producto, no recomienda tratamientos y no se publica en ninguna tienda.

- **Sin conexión.** El APK no tiene permiso de internet ni ningún otro permiso.
- **Sin datos personales.** Sin cuentas, sin nombres, sin teléfono, sin ubicación. Las fotos se
  guardan re-codificadas, sin metadatos EXIF (la cámara pudo haber escrito GPS en el original).
- **Solo Android** (8.0 o superior), teléfonos ARM de 32 y 64 bits.

## Estructura

```
modelo/                     Python: exportación y prueba de paridad
  inspeccionar.py           qué trae un .pt (clases, tamaño, versión)
  exportar.py               .pt → .onnx
  yolo_onnx.py              pre y posproceso de referencia (el mismo algoritmo que Detector.kt)
  paridad.py                compara .pt contra .onnx
  paridad.md / .csv         resultado de la última corrida
  referencia.json           detecciones de referencia de las 30 imágenes
app/src/main/java/investigacion/cafehojas/
  Detector.kt               modelo ONNX: letterbox, inferencia, decodificación, NMS
  Modelos.kt                modelo activo y umbral
  Almacen.kt                foto, guardado de análisis, exportación
  Pantallas.kt              interfaz (Compose)
  Textos.kt                 todo lo que lee el usuario
app/src/main/assets/modelo/ el .onnx incluido en el APK
app/src/test/               prueba de paridad del Detector en Kotlin
```

## Compilar

Requisitos: JDK 17 y Android SDK con `platforms;android-35` y `build-tools;35.0.0`.
No hace falta Android Studio.

1. Crear `local.properties` en la raíz con la ruta del SDK, por ejemplo `sdk.dir=C:/android-sdk`.
2. Apuntar `JAVA_HOME` al JDK 17.
3. Compilar:

```
gradlew.bat assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. Se instala copiándolo al teléfono y
abriéndolo (hay que permitir "instalar aplicaciones desconocidas"), o con `adb install`.

Pruebas y comprobaciones:

```
gradlew.bat testDebugUnitTest      (paridad del Detector en Kotlin contra referencia.json)
gradlew.bat lintDebug
C:\android-sdk\build-tools\35.0.0\aapt2 dump permissions app\build\outputs\apk\debug\app-debug.apk
```

La última no debe listar `android.permission.INTERNET`.

## Reemplazar el modelo

El modelo debe ser un `.onnx` exportado por Ultralytics, de detección, sin NMS dentro del grafo
(salida `[1, 4 + clases, N]`). El tamaño de entrada y los nombres de las clases se leen del archivo,
así que un modelo de 1.280 px entra sin tocar el código.

**Exportar un `.pt`:**

```
cd modelo
python -m venv .venv
.venv\Scripts\pip install ultralytics onnx onnxslim onnxruntime
.venv\Scripts\python inspeccionar.py ruta\al\modelo.pt
.venv\Scripts\python exportar.py ruta\al\modelo.pt 1280
.venv\Scripts\python paridad.py ruta\a\imagenes 30 ruta\al\modelo.pt
```

No usar un modelo en la app sin que `paridad.py` diga CUMPLE.

**Cargarlo en un teléfono que ya tiene la app:** copiar el `.onnx` al teléfono →
Ajustes → "Cargar otro modelo (.onnx)". Si el archivo no sirve, la app lo dice y conserva el anterior.

**Dejarlo incluido en el APK:** reemplazar el archivo de `app/src/main/assets/modelo/` (debe haber
un solo `.onnx`), copiar el nuevo `referencia.json` y volver a compilar. `testDebugUnitTest` lo valida.

Si aparece una clase nueva, agregar su nombre en español en `Textos.kt`; si no, se muestra el
nombre que trae el modelo.

## Qué guarda y qué exporta

Cada análisis queda en el almacenamiento privado de la app. "Fotos revisadas" → "Compartir todo en
un archivo" o "Guardar todo en el teléfono" genera un `.zip`:

```
resumen.csv                         una fila por análisis, con conteo por clase
analisis/<id>/foto.jpg              la foto analizada (lado largo máx. 2.560 px, sin EXIF)
analisis/<id>/analisis.json
```

Campos de `analisis.json`:

| Campo | Contenido |
|---|---|
| `id`, `fecha` | identificador y fecha-hora local |
| `modelo` | `archivo`, `sha256` (primeros 8 caracteres: es la versión), `lado`, `clases` |
| `umbral_confianza` | umbral con el que se mostró el resultado |
| `detecciones` | `clase_id`, `clase`, `confianza`, `caja` `[x1, y1, x2, y2]` en píxeles de `foto.jpg`, `mostrada` |
| `ms_prediccion` | tiempo total: reescalado + inferencia + NMS |
| `ms_inferencia` | solo la ejecución del modelo |
| `valoracion` | `correcto`, `incorrecto` o `null` |
| `nota` | texto libre del usuario (máx. 200 caracteres) |
| `dispositivo`, `app_version` | marca, modelo y versión de Android; versión de la app |

`detecciones` incluye **todo lo que el modelo encontró con confianza mayor a 0,05**, no solo lo que
se mostró. `mostrada` indica cuáles superaban el umbral. Así se puede evaluar cualquier umbral
después sin volver a correr el modelo: el NMS va de mayor a menor confianza, por lo que filtrar
después da exactamente las mismas cajas que correr con ese umbral.

## Decisiones técnicas

- **ONNX Runtime en lugar de TFLite.** `.pt → .onnx` es un solo paso y los módulos de atención de
  YOLOv12 salen como operaciones estándar. TFLite habría añadido otra conversión (onnx2tf), que es
  donde este modelo suele romperse. FP32, sin cuantizar.
- **`onnxruntime-android` fijado en 1.23.2.** Desde la 1.30 la librería incluye telemetría que se
  inicia sola y pide permiso de internet. No subir de versión sin revisar eso.
- **Reescalado propio en Kotlin** (bilineal, igual que `cv2.INTER_LINEAR`), para que el resultado
  no dependa del reescalado de cada versión de Android y se pueda probar en el computador.

## Licencia y datos

El modelo se entrenó y exportó con [Ultralytics](https://github.com/ultralytics/ultralytics), con
licencia AGPL-3.0. Este repositorio es un prototipo privado de investigación y se distribuye bajo
**AGPL-3.0** (ver `LICENSE`). No mezclar con código comercial.

Las imágenes de `app/src/test/resources/paridad/` provienen de BRACOL (versión revisada en
Roboflow, "bracol-validado", CC BY 4.0).

Lo que falta por verificar está en `PENDIENTES.md`.
