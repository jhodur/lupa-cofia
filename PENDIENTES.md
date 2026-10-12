# Lo que no funciona o no se pudo verificar

Estado al 12 de octubre de 2026, versión 0.1.0.

## La app nunca se ha abierto en un teléfono

No había teléfono de prueba ni emulador. Lo verificado es: que compila, que pasa `lint` sin errores,
que el APK no declara permisos, y que el detector en Kotlin reproduce las detecciones de referencia
corriendo en el computador. **Todo lo que es interfaz está escrito pero sin probar en ejecución:**

- Que la app abra sin cerrarse.
- Tomar foto con la cámara del sistema y recibirla de vuelta.
- Elegir de la galería.
- Que las cajas queden dibujadas en su sitio sobre la foto.
- Marcar correcto / incorrecto y escribir la nota.
- Compartir y guardar el `.zip`.
- Cargar otro modelo desde Ajustes.

El primer uso en un teléfono real debe tomarse como una prueba de humo, no como una salida a campo.

## Sin medir

- **Tiempo de predicción en teléfono a 640 px.** No hay medida. En el computador de desarrollo
  (Intel i7-13620H) son unos 90 ms de inferencia y 100 ms en total por imagen; eso no dice nada de
  un Android de gama baja, donde hay que esperar segundos. La app guarda `ms_prediccion` y
  `ms_inferencia` en cada análisis: el dato sale del primer `.zip` exportado.
- **Memoria.** Una foto de 2.560 px ocupa unos 40 MB entre imagen y píxeles mientras se analiza.
  Se activó `largeHeap`, pero en un teléfono de 2 GB de RAM puede fallar; si falla, la app muestra
  "No se pudo revisar la foto" en vez de cerrarse (tampoco probado).
- **Modelo de 1.280 px.** La app lo acepta sin cambios de código, pero no existe todavía, así que no
  se ha exportado, ni probado su paridad, ni medido. Cuesta unas 4 veces más cómputo y memoria.

## Paridad: qué cubre y qué no

- `.pt` contra `.onnx` en Python: 216 de 216 cajas, IoU 1,0000, diferencia de confianza 0,0000 (`modelo/paridad.md`).
- Detector en Kotlin contra esa referencia: 216 de 216 cajas, IoU medio 0,9963, diferencia máxima de
  confianza 0,013. La diferencia viene del decodificador JPEG de Java y del redondeo del reescalado.
- **No cubierto:** el decodificador JPEG de Android y la librería nativa ARM de onnxruntime. Lo
  esperable es una diferencia del mismo orden, pero no está medido. Para medirlo: analizar las 30
  imágenes de `app/src/test/resources/paridad/` desde la galería del teléfono, exportar el `.zip`
  y comparar con `modelo/referencia.json`. Ese script de comparación no está escrito.
- Las 30 imágenes son de laboratorio (fondo blanco, 2048×1024). No hay ninguna foto de campo.
- En 10 de las 30 el modelo no detecta nada (igual en `.pt` y `.onnx`). No se revisó contra las
  etiquetas si son hojas sin lesión o lesiones que no ve; eso es desempeño del modelo, no paridad.

## Sin revisar con usuarios

- Legibilidad a pleno sol. Se usó fondo blanco, texto negro de 18 sp o más y botones de 72 dp, pero
  nadie lo ha mirado al sol.
- Los textos no los ha leído ningún caficultor ni técnico.
- "Posible minador: 2 manchas": el minador deja galerías, no manchas. Se dejó una sola palabra para
  todas las clases.

## Limitaciones conocidas

- El APK pesa 69 MB (modelo 37 MB sin cuantizar + onnxruntime para 32 y 64 bits) y al abrirse por
  primera vez copia el modelo, otros 37 MB.
- Es un APK de depuración, firmado con la llave de depuración de este computador. Un APK compilado
  en otro equipo no se instala encima: hay que desinstalar primero, **y eso borra los análisis
  guardados**. Exportar antes de reinstalar.
- No hay forma de borrar análisis desde la app (a propósito: el dato es lo que importa).
- La hora se guarda con el desfase horario del teléfono (por ejemplo `-05:00`).
- Se guarda marca, modelo y versión de Android del teléfono en cada análisis, para interpretar los
  tiempos. No identifica a la persona, pero no se pidió expresamente.
- La orientación está fija en vertical.
- "Compartir" abre el menú del sistema: si el usuario elige WhatsApp o correo, esa otra app sí usa
  la red. La app misma no puede.
- `onnxruntime-android` quedó en 1.23.2 y no en la última, porque desde la 1.30 trae telemetría.
