package app.pbbls.android.core.designsystem.glyph

/** What a form fills (spec D3): nothing, the body, some of its parts, the first k steps, or the drawn dotted line. */
sealed interface GlyphFill {
    data object Regular : GlyphFill

    data object Solid : GlyphFill

    data object Dotted : GlyphFill

    data class Parts(
        val names: List<String>,
    ) : GlyphFill

    data class Progress(
        val k: Int,
    ) : GlyphFill
}

/**
 * A form (spec D3): a fill, plus the mark when the glyph has one. Its [key] is
 * the generator's form key (`solid-marked`, `parts-moon`, `progress-2`).
 */
data class GlyphForm(
    val fill: GlyphFill = GlyphFill.Regular,
    val marked: Boolean = false,
) {
    val fillKey: String
        get() =
            when (fill) {
                GlyphFill.Regular -> "regular"
                GlyphFill.Solid -> "solid"
                GlyphFill.Dotted -> "dotted"
                is GlyphFill.Parts -> "parts-" + fill.names.joinToString("+")
                is GlyphFill.Progress -> "progress-${fill.k}"
            }

    /** `regular` marked is `marked`; any other fill takes a `-marked` suffix. */
    val key: String
        get() =
            when {
                !marked -> fillKey
                fillKey == "regular" -> "marked"
                else -> "$fillKey-marked"
            }

    companion object {
        val REGULAR = GlyphForm()
        val MARKED = GlyphForm(marked = true)
        val SOLID = GlyphForm(GlyphFill.Solid)
        val DOTTED = GlyphForm(GlyphFill.Dotted)

        private val WORD = Regex("[a-z]+")
        private val COUNT = Regex("[1-9][0-9]*")

        /** The form a key names, or null when it is not a form key (`parseFormKey` in `forms.ts`). */
        fun fromKey(key: String): GlyphForm? {
            if (key == "marked") return MARKED
            val marked = key.endsWith("-marked")
            val head = if (marked) key.removeSuffix("-marked") else key
            return when {
                head == "regular" -> REGULAR.takeIf { !marked }
                head == "dotted" -> DOTTED.takeIf { !marked }
                head == "solid" -> GlyphForm(GlyphFill.Solid, marked)
                head.startsWith("parts-") ->
                    head
                        .removePrefix("parts-")
                        .split("+")
                        .takeIf { names -> names.all { WORD.matches(it) } }
                        ?.let { GlyphForm(GlyphFill.Parts(it), marked) }
                head.startsWith("progress-") ->
                    head
                        .removePrefix("progress-")
                        .takeIf { COUNT.matches(it) }
                        ?.toIntOrNull()
                        ?.let { GlyphForm(GlyphFill.Progress(it), marked) }
                else -> null
            }
        }
    }
}
