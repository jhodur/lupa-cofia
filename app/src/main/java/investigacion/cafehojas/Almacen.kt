package investigacion.cafehojas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

/** Lado largo máximo de la foto que se analiza y se guarda. Cubre de sobra un modelo de 1.280 px. */
const val LADO_MAX_FOTO = 2560

class Analisis(
    val id: String,
    val fecha: String,
    val modeloArchivo: String,
    val modeloSha: String,
    val modeloLado: Int,
    val umbral: Float,
    val msPrediccion: Long,
    val msInferencia: Long,
    val anchoFoto: Int,
    val altoFoto: Int,
    val clases: Map<Int, String>,
    /** Todas las detecciones desde PISO_CONFIANZA. Lo que vio el usuario son las que superan [umbral]. */
    val detecciones: List<Deteccion>,
    var valoracion: String? = null,
    var nota: String = "",
) {
    fun visibles() = detecciones.filter { it.confianza > umbral }

    /** Conteo por clase de lo que se mostró, en el orden de clases del modelo. */
    fun conteo(): List<Pair<String, Int>> =
        visibles().groupingBy { it.clase }.eachCount().toSortedMap().map { (clase, n) -> (clases[clase] ?: "clase $clase") to n }
}

class Almacen(private val ctx: Context) {
    private val carpeta = File(ctx.filesDir, "analisis").apply { mkdirs() }

    /**
     * Lee la foto ya derecha y con el lado largo acotado. El Bitmap que sale no lleva metadatos,
     * así que al guardarlo no queda ubicación ni datos de la cámara aunque el original los tuviera.
     */
    fun leerFoto(uri: Uri): Bitmap {
        val cr = ctx.contentResolver
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, limites) }
        var muestra = 1
        while (max(limites.outWidth, limites.outHeight) / (muestra * 2) >= LADO_MAX_FOTO) muestra *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestra; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val cruda = cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opciones) }
            ?: error("No se pudo abrir la foto")

        val exif = cr.openInputStream(uri)!!.use { ExifInterface(it) }
        val m = Matrix()
        val escala = min(1f, LADO_MAX_FOTO.toFloat() / max(cruda.width, cruda.height))
        if (escala < 1f) m.postScale(escala, escala)
        if (exif.isFlipped) m.postScale(-1f, 1f)
        if (exif.rotationDegrees != 0) m.postRotate(exif.rotationDegrees.toFloat())
        if (m.isIdentity) return cruda
        val derecha = Bitmap.createBitmap(cruda, 0, 0, cruda.width, cruda.height, m, true)
        // en gama baja la memoria es justa: la foto cruda puede pesar más de 40 MB
        if (derecha !== cruda) cruda.recycle()
        return derecha
    }

    /** Corre el modelo sobre la foto y deja guardados la foto y el resultado. */
    fun analizar(uri: Uri, modelo: ModeloActivo, umbral: Float): Analisis {
        val foto = leerFoto(uri)
        val pixeles = IntArray(foto.width * foto.height)
        foto.getPixels(pixeles, 0, foto.width, 0, 0, foto.width, foto.height)
        val prediccion = modelo.detector.detectar(pixeles, foto.width, foto.height)

        val ahora = OffsetDateTime.now()
        val analisis = Analisis(
            id = ahora.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")),
            fecha = ahora.truncatedTo(ChronoUnit.SECONDS).toString(),
            modeloArchivo = modelo.nombreVisible(),
            modeloSha = modelo.sha,
            modeloLado = modelo.detector.lado,
            umbral = umbral,
            msPrediccion = prediccion.msTotal,
            msInferencia = prediccion.msInferencia,
            anchoFoto = foto.width,
            altoFoto = foto.height,
            clases = modelo.detector.nombres,
            detecciones = prediccion.detecciones,
        )
        val dir = File(carpeta, analisis.id).apply { mkdirs() }
        File(dir, "foto.jpg").outputStream().use { foto.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        guardar(analisis)
        return analisis
    }

    fun guardar(a: Analisis) {
        val json = JSONObject()
            .put("id", a.id)
            .put("fecha", a.fecha)
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("dispositivo", "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
            .put("modelo", JSONObject()
                .put("archivo", a.modeloArchivo)
                .put("sha256", a.modeloSha)
                .put("lado", a.modeloLado)
                .put("clases", JSONObject().apply { a.clases.forEach { (i, n) -> put(i.toString(), n) } }))
            .put("umbral_confianza", a.umbral.toDouble())
            .put("piso_confianza", PISO_CONFIANZA.toDouble())
            .put("iou_nms", IOU_NMS.toDouble())
            .put("ms_prediccion", a.msPrediccion)
            .put("ms_inferencia", a.msInferencia)
            .put("foto", JSONObject().put("archivo", "foto.jpg").put("ancho", a.anchoFoto).put("alto", a.altoFoto))
            .put("detecciones", JSONArray().apply {
                a.detecciones.forEach { d ->
                    put(JSONObject()
                        .put("clase_id", d.clase)
                        .put("clase", a.clases[d.clase] ?: "")
                        .put("confianza", d.confianza.toDouble())
                        .put("caja", JSONArray(listOf(d.x1.toDouble(), d.y1.toDouble(), d.x2.toDouble(), d.y2.toDouble())))
                        .put("mostrada", d.confianza > a.umbral))
                }
            })
            .put("valoracion", a.valoracion ?: JSONObject.NULL)
            .put("nota", a.nota)
        File(File(carpeta, a.id), "analisis.json").writeText(json.toString(1))
    }

    fun listar(): List<Analisis> =
        (carpeta.listFiles { f -> f.isDirectory } ?: emptyArray())
            .sortedByDescending { it.name }
            .mapNotNull { dir -> runCatching { leer(File(dir, "analisis.json")) }.getOrNull() }

    private fun leer(archivo: File): Analisis {
        val j = JSONObject(archivo.readText())
        val modelo = j.getJSONObject("modelo")
        val clases = modelo.getJSONObject("clases")
        val foto = j.getJSONObject("foto")
        val dets = j.getJSONArray("detecciones")
        return Analisis(
            id = j.getString("id"),
            fecha = j.getString("fecha"),
            modeloArchivo = modelo.getString("archivo"),
            modeloSha = modelo.getString("sha256"),
            modeloLado = modelo.getInt("lado"),
            umbral = j.getDouble("umbral_confianza").toFloat(),
            msPrediccion = j.getLong("ms_prediccion"),
            msInferencia = j.getLong("ms_inferencia"),
            anchoFoto = foto.getInt("ancho"),
            altoFoto = foto.getInt("alto"),
            clases = clases.keys().asSequence().associate { it.toInt() to clases.getString(it) },
            detecciones = (0 until dets.length()).map { dets.getJSONObject(it) }.map {
                val c = it.getJSONArray("caja")
                Deteccion(it.getInt("clase_id"), it.getDouble("confianza").toFloat(),
                    c.getDouble(0).toFloat(), c.getDouble(1).toFloat(), c.getDouble(2).toFloat(), c.getDouble(3).toFloat())
            },
            valoracion = if (j.isNull("valoracion")) null else j.getString("valoracion"),
            nota = j.optString("nota"),
        )
    }

    /** Foto guardada, reducida para pantalla. */
    fun fotoParaPantalla(a: Analisis, ladoMax: Int = 1600): Bitmap? {
        var muestra = 1
        while (max(a.anchoFoto, a.altoFoto) / (muestra * 2) >= ladoMax) muestra *= 2
        return BitmapFactory.decodeFile(File(File(carpeta, a.id), "foto.jpg").path,
            BitmapFactory.Options().apply { inSampleSize = muestra })
    }

    /** Todos los análisis en un solo .zip: una carpeta por análisis (foto + json) y un resumen.csv. */
    fun exportarZip(): File {
        val todos = listar()
        val dir = File(ctx.cacheDir, "exportar").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val sello = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        val zip = File(dir, "cafe-hojas-$sello.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { salida ->
            salida.putNextEntry(ZipEntry("resumen.csv"))
            salida.write(resumenCsv(todos).toByteArray())
            salida.closeEntry()
            for (a in todos) {
                for (nombre in listOf("foto.jpg", "analisis.json")) {
                    val archivo = File(File(carpeta, a.id), nombre)
                    if (!archivo.exists()) continue
                    salida.putNextEntry(ZipEntry("analisis/${a.id}/$nombre"))
                    archivo.inputStream().use { it.copyTo(salida) }
                    salida.closeEntry()
                }
            }
        }
        return zip
    }

    private fun resumenCsv(todos: List<Analisis>): String {
        val clases = todos.flatMap { it.clases.values }.distinct()
        val celda = { v: Any? -> "\"" + (v?.toString() ?: "").replace("\"", "\"\"").replace("\n", " ") + "\"" }
        val filas = ArrayList<String>()
        filas += (listOf("id", "fecha", "modelo", "sha256", "lado", "umbral", "ms_prediccion", "ms_inferencia",
            "ancho", "alto", "valoracion", "nota") + clases.map { "n_$it" }).joinToString(",")
        for (a in todos) {
            val conteo = a.conteo().toMap()
            filas += (listOf(a.id, a.fecha, a.modeloArchivo, a.modeloSha, a.modeloLado, a.umbral, a.msPrediccion,
                a.msInferencia, a.anchoFoto, a.altoFoto, a.valoracion, a.nota) + clases.map { conteo[it] ?: 0 })
                .joinToString(",") { celda(it) }
        }
        // BOM para que Excel abra las tildes bien
        return Char(0xFEFF).toString() + filas.joinToString("\r\n") + "\r\n"
    }
}
