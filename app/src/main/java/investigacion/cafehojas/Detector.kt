package investigacion.cafehojas

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Port de modelo/yolo_onnx.py. Si se cambia aquí, hay que cambiarlo allá (y volver a correr la paridad).
// Sin imports de Android a propósito: la misma clase corre en la prueba del computador.

class Deteccion(val clase: Int, val confianza: Float, val x1: Float, val y1: Float, val x2: Float, val y2: Float)

class Prediccion(val detecciones: List<Deteccion>, val msInferencia: Long, val msTotal: Long)

class Letterbox(val datos: FloatArray, val r: Double, val izq: Int, val arriba: Int)

const val RELLENO = 114
const val IOU_NMS = 0.7f
const val MAX_DETECCIONES = 300

/**
 * Piso de confianza con el que se corre y se guarda. El umbral que elige el usuario solo filtra lo
 * que se muestra: como el NMS va de mayor a menor confianza, filtrar después da las mismas cajas
 * que correr directamente con ese umbral.
 */
const val PISO_CONFIANZA = 0.05f

class Detector(rutaModelo: String) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val sesion: OrtSession = env.createSession(rutaModelo, OrtSession.SessionOptions())
    private val entrada: String = sesion.inputNames.first()

    val meta: Map<String, String> = sesion.metadata.customMetadata
    val lado: Int
    val nombres: Map<Int, String>

    init {
        val forma = (sesion.inputInfo.getValue(entrada).info as TensorInfo).shape
        require(forma.size == 4 && forma[1] == 3L && forma[2] > 0 && forma[2] == forma[3]) {
            "Entrada no esperada: ${forma.toList()} (se espera [1, 3, lado, lado])"
        }
        lado = forma[2].toInt()
        // Ultralytics guarda los nombres como un dict de Python: {0: 'rust', 1: 'miner', ...}
        nombres = Regex("""(\d+)\s*:\s*['"]([^'"]*)['"]""").findAll(meta["names"] ?: "")
            .associate { it.groupValues[1].toInt() to it.groupValues[2] }
        require(nombres.isNotEmpty()) { "El modelo no trae los nombres de las clases en sus metadatos" }
        val salida = (sesion.outputInfo.values.first().info as TensorInfo).shape
        require(salida.size == 3 && salida[1] == 4L + nombres.size) {
            "Salida no esperada: ${salida.toList()} (se espera [1, ${4 + nombres.size}, N])"
        }
    }

    /** pixeles: ARGB por fila, como los entrega Bitmap.getPixels. Las cajas salen en píxeles de esa imagen. */
    fun detectar(pixeles: IntArray, ancho: Int, alto: Int, conf: Float = PISO_CONFIANZA): Prediccion {
        val t0 = System.nanoTime()
        val lb = letterbox(pixeles, ancho, alto, lado)
        var msInferencia = 0L
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(lb.datos), longArrayOf(1, 3, lado.toLong(), lado.toLong()))
        val detecciones = tensor.use {
            val t1 = System.nanoTime()
            sesion.run(mapOf(entrada to tensor)).use { resultado ->
                msInferencia = (System.nanoTime() - t1) / 1_000_000
                val salida = resultado[0] as OnnxTensor
                val forma = salida.info.shape
                val buf = salida.floatBuffer
                val arr = FloatArray(buf.remaining())
                buf.get(arr)
                posproceso(arr, forma[1].toInt(), forma[2].toInt(), lb, ancho, alto, conf)
            }
        }
        return Prediccion(detecciones, msInferencia, (System.nanoTime() - t0) / 1_000_000)
    }

    override fun close() = sesion.close()
}

/** Escala manteniendo proporción (bilineal, como cv2.INTER_LINEAR) y rellena hasta lado x lado. Devuelve CHW, RGB, 0..1. */
fun letterbox(pixeles: IntArray, ancho: Int, alto: Int, lado: Int): Letterbox {
    val r = min(lado.toDouble() / alto, lado.toDouble() / ancho)
    val nuevoAncho = Math.rint(ancho * r).toInt()
    val nuevoAlto = Math.rint(alto * r).toInt()
    val izq = Math.rint((lado - nuevoAncho) / 2.0 - 0.1).toInt()
    val arriba = Math.rint((lado - nuevoAlto) / 2.0 - 0.1).toInt()

    val plano = lado * lado
    val datos = FloatArray(3 * plano) { RELLENO / 255f }
    val mismoTamano = nuevoAncho == ancho && nuevoAlto == alto

    // índices y pesos por columna, calculados una sola vez
    val x0 = IntArray(nuevoAncho)
    val x1 = IntArray(nuevoAncho)
    val wx = FloatArray(nuevoAncho)
    val escalaX = ancho.toDouble() / nuevoAncho
    for (x in 0 until nuevoAncho) {
        val f = (x + 0.5) * escalaX - 0.5
        var s = floor(f).toInt()
        var w = (f - s).toFloat()
        if (s < 0) { s = 0; w = 0f }
        if (s >= ancho - 1) { s = ancho - 1; w = 0f }
        x0[x] = s; x1[x] = min(s + 1, ancho - 1); wx[x] = w
    }
    val escalaY = alto.toDouble() / nuevoAlto

    for (y in 0 until nuevoAlto) {
        val destino = (y + arriba) * lado + izq
        if (mismoTamano) {
            val origen = y * ancho
            for (x in 0 until nuevoAncho) {
                val p = pixeles[origen + x]
                datos[destino + x] = ((p shr 16) and 0xFF) / 255f
                datos[plano + destino + x] = ((p shr 8) and 0xFF) / 255f
                datos[2 * plano + destino + x] = (p and 0xFF) / 255f
            }
            continue
        }
        val f = (y + 0.5) * escalaY - 0.5
        var s = floor(f).toInt()
        var wy = (f - s).toFloat()
        if (s < 0) { s = 0; wy = 0f }
        if (s >= alto - 1) { s = alto - 1; wy = 0f }
        val fila0 = s * ancho
        val fila1 = min(s + 1, alto - 1) * ancho
        for (x in 0 until nuevoAncho) {
            val a = pixeles[fila0 + x0[x]]
            val b = pixeles[fila0 + x1[x]]
            val c = pixeles[fila1 + x0[x]]
            val d = pixeles[fila1 + x1[x]]
            val w = wx[x]
            for (canal in 0..2) {
                val desp = 16 - 8 * canal
                val va = (a shr desp) and 0xFF
                val vb = (b shr desp) and 0xFF
                val vc = (c shr desp) and 0xFF
                val vd = (d shr desp) and 0xFF
                val sup = va + (vb - va) * w
                val inf = vc + (vd - vc) * w
                // cv2 devuelve enteros de 8 bits: se redondea igual antes de normalizar
                datos[canal * plano + destino + x] = (sup + (inf - sup) * wy + 0.5f).toInt() / 255f
            }
        }
    }
    return Letterbox(datos, r, izq, arriba)
}

/** salida: [filas, n] aplanado, con cx, cy, w, h en píxeles de la entrada y un puntaje por clase. */
fun posproceso(salida: FloatArray, filas: Int, n: Int, lb: Letterbox, ancho: Int, alto: Int, conf: Float): List<Deteccion> {
    val nc = filas - 4
    val porClase = HashMap<Int, MutableList<Deteccion>>()
    for (i in 0 until n) {
        var clase = -1
        var mejor = conf
        for (c in 0 until nc) {
            val v = salida[(4 + c) * n + i]
            if (v > mejor) { mejor = v; clase = c }
        }
        if (clase < 0) continue
        val cx = salida[i]
        val cy = salida[n + i]
        val w = salida[2 * n + i]
        val h = salida[3 * n + i]
        porClase.getOrPut(clase) { ArrayList() }
            .add(Deteccion(clase, mejor, cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2))
    }

    val quedan = ArrayList<Deteccion>()
    for (candidatas in porClase.values) {
        candidatas.sortByDescending { it.confianza }  // sort estable, como en la referencia
        val elegidas = ArrayList<Deteccion>()
        for (d in candidatas) {
            if (elegidas.none { iou(it, d) > IOU_NMS }) elegidas.add(d)
        }
        quedan.addAll(elegidas)
    }
    quedan.sortByDescending { it.confianza }

    // de coordenadas de la entrada a coordenadas de la foto original
    val r = lb.r.toFloat()
    return quedan.take(MAX_DETECCIONES).map { d ->
        Deteccion(
            d.clase, d.confianza,
            ((d.x1 - lb.izq) / r).coerceIn(0f, ancho.toFloat()),
            ((d.y1 - lb.arriba) / r).coerceIn(0f, alto.toFloat()),
            ((d.x2 - lb.izq) / r).coerceIn(0f, ancho.toFloat()),
            ((d.y2 - lb.arriba) / r).coerceIn(0f, alto.toFloat()),
        )
    }
}

fun iou(a: Deteccion, b: Deteccion): Float {
    val inter = max(0f, min(a.x2, b.x2) - max(a.x1, b.x1)) * max(0f, min(a.y2, b.y2) - max(a.y1, b.y1))
    val union = (a.x2 - a.x1) * (a.y2 - a.y1) + (b.x2 - b.x1) * (b.y2 - b.y1) - inter
    return inter / (union + 1e-9f)
}
