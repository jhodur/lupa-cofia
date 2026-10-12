package investigacion.cafehojas

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private sealed interface Pantalla {
    data object Inicio : Pantalla
    data object Analizando : Pantalla
    class Resultado(val analisis: Analisis, val volverA: Pantalla) : Pantalla
    data object Historial : Pantalla
    data object Ajustes : Pantalla
}

private val Verde = Color(0xFF1B5E20)
private val Negro = Color(0xFF000000)
private val Rojo = Color(0xFFB00020)

// Un color por clase, elegidos para verse sobre una hoja verde
private val ColoresClase = listOf(
    Color(0xFFFF6D00), Color(0xFF2962FF), Color(0xFFAA00FF), Color(0xFFD50000), Color(0xFF00B8D4), Color(0xFFFFD600),
)

private fun colorDe(clase: Int) = ColoresClase[clase.mod(ColoresClase.size)]

private fun uriDe(ctx: Context, archivo: File): Uri =
    FileProvider.getUriForFile(ctx, "${ctx.packageName}.archivos", archivo)

@Composable
fun App() {
    val ctx = LocalContext.current.applicationContext
    val almacen = remember { Almacen(ctx) }
    val modelos = remember { Modelos(ctx) }
    val scope = rememberCoroutineScope()
    var pantalla by remember { mutableStateOf<Pantalla>(Pantalla.Inicio) }
    var error by remember { mutableStateOf<String?>(null) }

    fun analizar(uri: Uri) {
        error = null
        pantalla = Pantalla.Analizando
        scope.launch {
            pantalla = try {
                val analisis = withContext(Dispatchers.Default) { almacen.analizar(uri, modelos.activo(), modelos.umbral) }
                Pantalla.Resultado(analisis, Pantalla.Inicio)
            } catch (e: Throwable) {
                error = "No se pudo revisar la foto. Intente otra vez."
                Pantalla.Inicio
            }
        }
    }

    // Archivo fijo: si el teléfono cierra la app mientras la cámara está abierta, al volver la foto sigue ahí
    val captura = remember { File(ctx.cacheDir, "camara/captura.jpg").apply { parentFile?.mkdirs() } }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) analizar(Uri.fromFile(captura))
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) analizar(uri)
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Verde, onPrimary = Color.White, surface = Color.White, onSurface = Negro)) {
        Box(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
            when (val p = pantalla) {
                Pantalla.Inicio -> PantallaInicio(
                    error = error,
                    onCamara = {
                        try {
                            camara.launch(uriDe(ctx, captura))
                        } catch (e: Exception) {
                            error = "No se pudo abrir la cámara. Use \"${Textos.ELEGIR_GALERIA}\"."
                        }
                    },
                    onGaleria = {
                        try {
                            galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } catch (e: Exception) {
                            error = "No se pudo abrir la galería."
                        }
                    },
                    onHistorial = { pantalla = Pantalla.Historial },
                    onAjustes = { pantalla = Pantalla.Ajustes },
                )
                Pantalla.Analizando -> Column(
                    Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(Modifier.size(72.dp), color = Verde, strokeWidth = 8.dp)
                    Spacer(Modifier.size(24.dp))
                    Text(Textos.ANALIZANDO, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Negro)
                }
                is Pantalla.Resultado -> {
                    BackHandler { pantalla = p.volverA }
                    PantallaResultado(p.analisis, almacen) { pantalla = p.volverA }
                }
                Pantalla.Historial -> {
                    BackHandler { pantalla = Pantalla.Inicio }
                    PantallaHistorial(almacen, onAbrir = { pantalla = Pantalla.Resultado(it, Pantalla.Historial) }) {
                        pantalla = Pantalla.Inicio
                    }
                }
                Pantalla.Ajustes -> {
                    BackHandler { pantalla = Pantalla.Inicio }
                    PantallaAjustes(modelos) { pantalla = Pantalla.Inicio }
                }
            }
        }
    }
}

@Composable
private fun BotonGrande(texto: String, principal: Boolean = true, onClick: () -> Unit) {
    val forma = RoundedCornerShape(12.dp)
    val modificador = Modifier.fillMaxWidth().heightIn(min = 72.dp)
    if (principal) {
        Button(onClick, modificador, shape = forma, colors = ButtonDefaults.buttonColors(containerColor = Verde)) {
            Text(texto, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(onClick, modificador, shape = forma, border = BorderStroke(3.dp, Negro)) {
            Text(texto, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Negro)
        }
    }
}

@Composable
private fun Volver(titulo: String, onVolver: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onVolver) { Text("← Volver", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Verde) }
        Spacer(Modifier.size(8.dp))
        Text(titulo, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Negro)
    }
}

@Composable
private fun Aviso() {
    Text(
        Textos.AVISO, fontSize = 18.sp, color = Negro, lineHeight = 24.sp,
        modifier = Modifier.fillMaxWidth().border(3.dp, Negro, RoundedCornerShape(12.dp)).padding(14.dp),
    )
}

@Composable
private fun PantallaInicio(
    error: String?, onCamara: () -> Unit, onGaleria: () -> Unit, onHistorial: () -> Unit, onAjustes: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Textos.TITULO, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Negro)
                Text(Textos.SUBTITULO, fontSize = 18.sp, color = Negro)
            }
            // discreto: quien usa la app en la finca no necesita entrar aquí
            TextButton(onAjustes) { Text("Ajustes", fontSize = 14.sp, color = Color(0xFF555555)) }
        }
        if (error != null) Text(error, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Rojo)

        Column(
            Modifier.fillMaxWidth().background(Color(0xFFF1F8E9), RoundedCornerShape(12.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(Textos.COMO_TOMAR_TITULO, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Negro)
            Textos.COMO_TOMAR.forEach { Text("•  $it", fontSize = 20.sp, color = Negro, lineHeight = 26.sp) }
            Text(Textos.FONDO_CLARO, fontSize = 18.sp, color = Negro, lineHeight = 24.sp, modifier = Modifier.padding(top = 6.dp))
        }

        BotonGrande(Textos.TOMAR_FOTO, onClick = onCamara)
        BotonGrande(Textos.ELEGIR_GALERIA, principal = false, onClick = onGaleria)
        BotonGrande("Fotos revisadas", principal = false, onClick = onHistorial)
        Aviso()
    }
}

@Composable
private fun FotoConCajas(a: Analisis, foto: Bitmap?) {
    val visibles = a.visibles()
    Box(Modifier.fillMaxWidth().aspectRatio(a.anchoFoto.toFloat() / a.altoFoto).background(Color(0xFFEEEEEE))) {
        if (foto != null) Image(foto.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / a.anchoFoto
            for (d in visibles) {
                val origen = Offset(d.x1 * k, d.y1 * k)
                val tamano = Size((d.x2 - d.x1) * k, (d.y2 - d.y1) * k)
                // borde blanco debajo para que la caja se vea sobre cualquier fondo
                drawRect(Color.White, origen, tamano, style = Stroke(6.dp.toPx()))
                drawRect(colorDe(d.clase), origen, tamano, style = Stroke(3.dp.toPx()))
            }
        }
    }
}

@Composable
private fun Resumen(a: Analisis) {
    val conteo = a.visibles().groupingBy { it.clase }.eachCount().toSortedMap()
    if (conteo.isEmpty()) {
        Text(Textos.SIN_MANCHAS, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Negro)
        Text(Textos.SIN_MANCHAS_ACLARACION, fontSize = 20.sp, color = Negro)
        return
    }
    for ((clase, n) in conteo) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(colorDe(clase)).border(2.dp, Negro))
            Spacer(Modifier.size(12.dp))
            Text(Textos.resumen(a.clases[clase] ?: "clase $clase", n), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Negro)
        }
    }
}

@Composable
private fun PantallaResultado(a: Analisis, almacen: Almacen, onListo: () -> Unit) {
    var foto by remember(a.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(a.id) { foto = withContext(Dispatchers.IO) { almacen.fotoParaPantalla(a) } }
    var valoracion by remember(a.id) { mutableStateOf(a.valoracion) }
    var nota by remember(a.id) { mutableStateOf(a.nota) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FotoConCajas(a, foto)
        Resumen(a)
        Aviso()

        Text(Textos.PREGUNTA, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Negro)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((valor, texto, color) in listOf(Triple("correcto", Textos.SI, Verde), Triple("incorrecto", Textos.NO, Rojo))) {
                val elegido = valoracion == valor
                OutlinedButton(
                    onClick = { valoracion = valor; a.valoracion = valor; almacen.guardar(a) },
                    modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(3.dp, color),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (elegido) color else Color.White),
                ) {
                    Text(texto, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (elegido) Color.White else color)
                }
            }
        }
        OutlinedTextField(
            value = nota,
            onValueChange = { nota = it.take(200); a.nota = nota; almacen.guardar(a) },
            label = { Text(Textos.NOTA, fontSize = 16.sp) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, color = Negro),
            modifier = Modifier.fillMaxWidth(),
        )
        BotonGrande(Textos.LISTO, onClick = onListo)
        Text(
            "Tardó ${"%.1f".format(a.msPrediccion / 1000.0)} s · ${a.modeloArchivo} (${a.modeloSha}) · ${a.fecha.take(16).replace("T", " ")}",
            fontSize = 13.sp, color = Color(0xFF555555),
        )
    }
}

@Composable
private fun PantallaHistorial(almacen: Almacen, onAbrir: (Analisis) -> Unit, onVolver: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<Analisis>?>(null) }
    LaunchedEffect(Unit) { lista = withContext(Dispatchers.IO) { almacen.listar() } }
    var mensaje by remember { mutableStateOf<String?>(null) }

    val guardarEn = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { destino ->
        if (destino != null) scope.launch {
            mensaje = try {
                withContext(Dispatchers.IO) {
                    val zip = almacen.exportarZip()
                    ctx.contentResolver.openOutputStream(destino)!!.use { salida -> zip.inputStream().use { it.copyTo(salida) } }
                }
                "Archivo guardado."
            } catch (e: Exception) {
                "No se pudo guardar el archivo."
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Volver("Fotos revisadas", onVolver)
        val todos = lista
        when {
            todos == null -> Text("Cargando…", fontSize = 20.sp, color = Negro)
            todos.isEmpty() -> Text("Todavía no hay fotos revisadas.", fontSize = 20.sp, color = Negro)
            else -> {
                Text("${todos.size} en total", fontSize = 18.sp, color = Negro)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(todos, key = { it.id }) { a ->
                        Column(
                            Modifier.fillMaxWidth().border(2.dp, Negro, RoundedCornerShape(10.dp))
                                .clickable { onAbrir(a) }.padding(12.dp),
                        ) {
                            Text(a.fecha.take(16).replace("T", "  "), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Negro)
                            val conteo = a.conteo()
                            Text(
                                if (conteo.isEmpty()) "Sin manchas encontradas"
                                else conteo.joinToString(" · ") { (clase, n) -> "${Textos.clase(clase)}: $n" },
                                fontSize = 18.sp, color = Negro,
                            )
                            when (a.valoracion) {
                                "correcto" -> Text("Marcado: correcto", fontSize = 16.sp, color = Verde, fontWeight = FontWeight.Bold)
                                "incorrecto" -> Text("Marcado: está mal", fontSize = 16.sp, color = Rojo, fontWeight = FontWeight.Bold)
                                else -> Text("Sin marcar", fontSize = 16.sp, color = Color(0xFF555555))
                            }
                        }
                    }
                }
                mensaje?.let { Text(it, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Negro) }
                BotonGrande("Compartir todo en un archivo", principal = false) {
                    scope.launch {
                        try {
                            val zip = withContext(Dispatchers.IO) { almacen.exportarZip() }
                            val uri = uriDe(ctx, zip)
                            val envio = Intent(Intent.ACTION_SEND)
                                .setType("application/zip")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            // el permiso de lectura viaja con el ClipData hasta la app que reciba el archivo
                            envio.clipData = ClipData.newRawUri("", uri)
                            ctx.startActivity(Intent.createChooser(envio, "Compartir"))
                        } catch (e: Exception) {
                            mensaje = "No se pudo preparar el archivo."
                        }
                    }
                }
                BotonGrande("Guardar todo en el teléfono", principal = false) {
                    guardarEn.launch("cafe-hojas-${todos.first().id.take(8)}.zip")
                }
            }
        }
    }
}

@Composable
private fun PantallaAjustes(modelos: Modelos, onVolver: () -> Unit) {
    val scope = rememberCoroutineScope()
    var umbral by remember { mutableFloatStateOf(modelos.umbral) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var activo by remember { mutableStateOf<ModeloActivo?>(null) }
    LaunchedEffect(recarga) {
        activo = withContext(Dispatchers.Default) { runCatching { modelos.activo() }.getOrNull() }
    }

    val elegirModelo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            mensaje = "Cargando modelo…"
            scope.launch {
                mensaje = try {
                    withContext(Dispatchers.Default) { modelos.cargarOtro(uri) }
                    "Modelo cargado."
                } catch (e: Throwable) {
                    "Ese archivo no sirve como modelo. Se conserva el anterior.\n${e.message ?: ""}"
                }
                recarga++
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Volver("Ajustes", onVolver)

        Text("Umbral de confianza: ${(umbral * 100).roundToInt()} %", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Negro)
        Slider(
            value = umbral,
            onValueChange = { umbral = (it * 20).roundToInt() / 20f },
            onValueChangeFinished = { modelos.umbral = umbral },
            valueRange = 0.05f..0.95f,
            steps = 17,
        )
        Text(
            "Solo se muestran las manchas con confianza mayor a este valor. Vale para las fotos nuevas; " +
                "las ya revisadas conservan el umbral con el que se vieron.",
            fontSize = 16.sp, color = Negro,
        )

        Text("Modelo", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Negro)
        val m = activo
        Text(
            if (m == null) "Cargando…"
            else "${m.nombreVisible()} (${m.sha})${if (m.incluido) " · incluido en la app" else ""}\n" +
                "Entrada: ${m.detector.lado} px\nClases: ${m.detector.nombres.toSortedMap().values.joinToString(", ")}",
            fontSize = 16.sp, color = Negro,
        )
        mensaje?.let { Text(it, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Negro) }
        BotonGrande("Cargar otro modelo (.onnx)", principal = false) { elegirModelo.launch(arrayOf("*/*")) }
        if (m != null && !m.incluido) {
            BotonGrande("Volver al modelo incluido", principal = false) {
                scope.launch {
                    withContext(Dispatchers.Default) { modelos.volverAlIncluido() }
                    mensaje = "Se volvió al modelo incluido."
                    recarga++
                }
            }
        }
        Text("Versión ${BuildConfig.VERSION_NAME} · sin conexión · no recoge datos personales", fontSize = 13.sp, color = Color(0xFF555555))
    }
}
