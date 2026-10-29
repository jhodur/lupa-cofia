package investigacion.cafehojas

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest

class ModeloActivo(val detector: Detector, val archivo: String, val sha: String, val incluido: Boolean)

/** Qué modelo está en uso y con qué umbral se muestran las manchas. */
class Modelos(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
    private val carpeta = File(ctx.filesDir, "modelos").apply { mkdirs() }
    private var activo: ModeloActivo? = null

    var umbral: Float
        get() = prefs.getFloat("umbral", 0.25f)
        set(v) = prefs.edit().putFloat("umbral", v).apply()

    /** Caficultor (por defecto): semáforo, sin porcentajes ni valoración. Extensionista: porcentajes y valoración. */
    var extensionista: Boolean
        get() = prefs.getBoolean("extensionista", false)
        set(v) = prefs.edit().putBoolean("extensionista", v).apply()

    /** Carga perezosa: la primera llamada tarda (copia el modelo incluido y abre la sesión). */
    @Synchronized
    fun activo(): ModeloActivo {
        activo?.let { return it }
        val elegido = prefs.getString("modelo", null)?.let { File(carpeta, it) }?.takeIf { it.exists() }
        val cargado = try {
            abrir(elegido ?: incluido(), incluido = elegido == null)
        } catch (e: Exception) {
            if (elegido == null) throw e
            // el modelo cargado a mano dejó de servir: se vuelve al incluido
            prefs.edit().remove("modelo").apply()
            abrir(incluido(), incluido = true)
        }
        activo = cargado
        return cargado
    }

    /** Copia el archivo elegido, comprueba que abre y que tiene la forma esperada, y lo deja activo. */
    @Synchronized
    fun cargarOtro(uri: Uri): ModeloActivo {
        val nombre = (nombreDe(uri) ?: "modelo.onnx").replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destino = File(carpeta, nombre)
        val temporal = File(carpeta, "$nombre.tmp")
        ctx.contentResolver.openInputStream(uri)!!.use { entrada -> temporal.outputStream().use { entrada.copyTo(it) } }
        val nuevo = try {
            abrir(temporal, incluido = false, nombre = nombre)
        } catch (e: Exception) {
            temporal.delete()
            throw e
        }
        // abrió bien: se cierra para poder renombrar y se reabre desde el nombre definitivo
        nuevo.detector.close()
        activo?.detector?.close()
        activo = null
        destino.delete()
        temporal.renameTo(destino)
        prefs.edit().putString("modelo", nombre).apply()
        return activo()
    }

    @Synchronized
    fun volverAlIncluido(): ModeloActivo {
        activo?.detector?.close()
        activo = null
        prefs.edit().remove("modelo").apply()
        return activo()
    }

    private fun abrir(archivo: File, incluido: Boolean, nombre: String = archivo.name) =
        ModeloActivo(Detector(archivo.path), nombre, sha256(archivo).take(8), incluido)

    /** El modelo de assets/modelo/ se copia una vez a disco para abrirlo por ruta, sin tenerlo entero en memoria. */
    private fun incluido(): File {
        val nombre = ctx.assets.list("modelo")!!.single { it.endsWith(".onnx") }
        // la fecha de instalación en el nombre hace que cada APK nuevo vuelva a copiar su modelo
        val instalado = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime
        val destino = File(carpeta, "incluido_${instalado}_$nombre")
        if (!destino.exists()) {
            carpeta.listFiles { f -> f.name.startsWith("incluido_") }?.forEach { it.delete() }
            val temporal = File(carpeta, "${destino.name}.tmp")
            ctx.assets.open("modelo/$nombre").use { entrada -> temporal.outputStream().use { entrada.copyTo(it) } }
            temporal.renameTo(destino)
        }
        return destino
    }

    private fun nombreDe(uri: Uri): String? =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun sha256(archivo: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        archivo.inputStream().use { entrada ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = entrada.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Nombre del modelo incluido sin el prefijo interno, para mostrar y guardar. */
fun ModeloActivo.nombreVisible() = if (incluido) archivo.substringAfter("_").substringAfter("_") else archivo
