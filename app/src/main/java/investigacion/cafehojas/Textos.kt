package investigacion.cafehojas

// Todo lo que lee el caficultor está aquí. Frases cortas, sin tecnicismos y sin recomendar nada.

object Textos {
    const val TITULO = "Hojas de café"
    const val SUBTITULO = "Programa de prueba"

    const val COMO_TOMAR_TITULO = "Para una buena foto"
    val COMO_TOMAR = listOf(
        "Una sola hoja.",
        "Que la hoja llene casi toda la foto.",
        "Bien enfocada, sin moverse.",
        "Sin sombra encima.",
        "Si puede, sobre un fondo claro: un papel o una tela blanca.",
    )
    const val FONDO_CLARO = "Este programa aprendió con hojas sobre fondo blanco. Con otros fondos se equivoca más."

    const val TOMAR_FOTO = "Tomar foto"
    const val ELEGIR_GALERIA = "Elegir de la galería"
    const val ANALIZANDO = "Revisando la foto…"

    const val SIN_MANCHAS = "No encontré manchas en esta foto."
    const val SIN_MANCHAS_ACLARACION = "Eso no quiere decir que la hoja esté sana."
    const val AVISO = "Esto es una prueba. El programa se equivoca. " +
        "No dice qué hacer con el cultivo: esa decisión la toma usted con su técnico."

    const val PREGUNTA = "¿Le parece correcto el resultado?"
    const val SI = "Sí, correcto"
    const val NO = "No, está mal"
    const val NOTA = "Nota corta (si quiere)"
    const val LISTO = "Listo"

    private val enEspanol = mapOf(
        "rust" to "roya",
        "miner" to "minador",
        "phoma" to "phoma",
        "cercospora" to "cercospora",
    )

    const val MODO_EXTENSIONISTA = "Modo extensionista"

    /**
     * Semáforo de confiabilidad para el caficultor, según la confianza más alta de esa enfermedad en la foto.
     * Cortes iniciales: se calibran cuando haya fotos de campo etiquetadas por un experto.
     */
    enum class Nivel(val texto: String) {
        ALTA("Confianza alta"),
        MEDIA("Confianza media: revíselo con su técnico"),
        BAJA("Confianza baja: puede ser un error"),
    }

    const val CORTE_ALTA = 0.60f
    const val CORTE_MEDIA = 0.40f

    fun nivel(confianza: Float) = when {
        confianza >= CORTE_ALTA -> Nivel.ALTA
        confianza >= CORTE_MEDIA -> Nivel.MEDIA
        else -> Nivel.BAJA
    }

    /** Nombre para mostrar. Si el modelo trae una clase que no conocemos, se muestra tal cual. */
    fun clase(nombreModelo: String) = enEspanol[nombreModelo.lowercase()] ?: nombreModelo

    fun resumen(nombreModelo: String, cantidad: Int) =
        "Posible ${clase(nombreModelo)}: $cantidad ${if (cantidad == 1) "mancha" else "manchas"}"
}
