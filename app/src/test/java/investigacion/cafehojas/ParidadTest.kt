package investigacion.cafehojas

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Corre el Detector de la app sobre las mismas 30 imágenes de modelo/paridad.py y compara contra
 * modelo/referencia.json. Valida letterbox, decodificación de la salida y NMS escritos en Kotlin.
 * No valida el decodificador JPEG de Android ni el rendimiento en teléfono.
 */
class ParidadTest {
    private val conf = 0.25f
    private val borde = 0.02f

    // javax.imageio existe en el JDK que corre la prueba pero no en android.jar, contra el que se compila
    private fun leerImagen(archivo: File): Triple<IntArray, Int, Int> {
        val img = Class.forName("javax.imageio.ImageIO").getMethod("read", File::class.java).invoke(null, archivo)
        val ancho = img.javaClass.getMethod("getWidth").invoke(img) as Int
        val alto = img.javaClass.getMethod("getHeight").invoke(img) as Int
        val i = Int::class.javaPrimitiveType
        val pixeles = img.javaClass.getMethod("getRGB", i, i, i, i, IntArray::class.java, i, i)
            .invoke(img, 0, 0, ancho, alto, null, 0, ancho) as IntArray
        return Triple(pixeles, ancho, alto)
    }

    @Test
    fun kotlinReproduceLaReferenciaDePython() {
        val modelo = File("src/main/assets/modelo").listFiles { f -> f.extension == "onnx" }!!.single()
        val referencia = JSONObject(File("../modelo/referencia.json").readText())
        val imagenes = referencia.getJSONArray("imagenes")

        var totalRef = 0; var totalKt = 0; var emparejadas = 0; var fueraDeBorde = 0
        var sumaIou = 0.0; var difMax = 0f
        var msInferencia = 0L; var msTotal = 0L

        Detector(modelo.path).use { detector ->
            for (k in 0 until imagenes.length()) {
                val item = imagenes.getJSONObject(k)
                val (pixeles, ancho, alto) = leerImagen(File("src/test/resources/paridad", item.getString("imagen")))
                val prediccion = detector.detectar(pixeles, ancho, alto)
                msInferencia += prediccion.msInferencia; msTotal += prediccion.msTotal
                val mias = prediccion.detecciones.filter { it.confianza > conf }

                val arr = item.getJSONArray("detecciones")
                val ref = (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                    val c = it.getJSONArray("caja")
                    Deteccion(it.getInt("clase"), it.getDouble("confianza").toFloat(),
                        c.getDouble(0).toFloat(), c.getDouble(1).toFloat(), c.getDouble(2).toFloat(), c.getDouble(3).toFloat())
                }

                val libres = mias.toMutableList()
                val sinPareja = ArrayList<Float>()
                for (r in ref) {
                    val mejor = libres.filter { it.clase == r.clase }.maxByOrNull { iou(it, r) }
                    if (mejor != null && iou(mejor, r) >= 0.9f) {
                        libres.remove(mejor)
                        emparejadas++
                        sumaIou += iou(mejor, r)
                        difMax = maxOf(difMax, abs(mejor.confianza - r.confianza))
                    } else sinPareja.add(r.confianza)
                }
                sinPareja.addAll(libres.map { it.confianza })
                fueraDeBorde += sinPareja.count { abs(it - conf) > borde }
                totalRef += ref.size; totalKt += mias.size
                println("%-30s ref=%2d kotlin=%2d sinPareja=%d".format(item.getString("imagen").take(30), ref.size, mias.size, sinPareja.size))
            }
        }

        println("TOTAL ref=$totalRef kotlin=$totalKt emparejadas=$emparejadas " +
            "iouMedio=%.4f difConfMax=%.4f fueraDeBorde=$fueraDeBorde".format(sumaIou / maxOf(emparejadas, 1), difMax))
        println("TIEMPO en este computador (no en teléfono): inferencia=${msInferencia / imagenes.length()} ms, " +
            "total=${msTotal / imagenes.length()} ms por imagen")
        assertEquals("cajas sin pareja lejos del umbral", 0, fueraDeBorde)
        assertTrue("IoU medio bajo", sumaIou / maxOf(emparejadas, 1) >= 0.98)
    }
}
