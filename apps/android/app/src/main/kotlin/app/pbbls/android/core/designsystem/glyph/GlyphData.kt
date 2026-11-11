package app.pbbls.android.core.designsystem.glyph

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A path as a form draws it: filled (non-zero or even-odd), stroked, or both. */
@Serializable
data class RenderedPathData(
    val d: String,
    val fill: Boolean,
    val evenOdd: Boolean,
    val stroke: Boolean,
)

/** A path's role (spec D2). */
@Serializable
enum class GlyphRole {
    @SerialName("body")
    BODY,

    @SerialName("mark")
    MARK,

    @SerialName("step")
    STEP,
}

/** One path of a glyph with its role, as `pbbls_glyphs.json` stores it. */
@Serializable
data class GlyphPathData(
    val d: String,
    val role: GlyphRole? = null,
    val part: String? = null,
    val step: Int? = null,
    val hidden: Boolean? = null,
    /** A mark or step with lines inside it: its carved fill, drawn instead of [d] when it fills. */
    val fill: String? = null,
    /** The paths [fill] is carved through, left out while it fills. */
    val cuts: List<Int>? = null,
)

/** A solid or parts fill worked out at build time: indices are into `paths`. */
@Serializable
data class CarvedFillData(
    val d: String,
    val keep: List<Int>,
    val lit: List<Int>,
)

/** A glyph as `pbbls_glyphs.json` stores it (spec §2), the contract the generator writes. */
@Serializable
data class GlyphData(
    val paths: List<GlyphPathData>,
    val parts: List<String> = emptyList(),
    val steps: Int = 0,
    val carved: Map<String, CarvedFillData> = emptyMap(),
    val dotted: List<RenderedPathData>? = null,
    val overrides: Map<String, List<RenderedPathData>> = emptyMap(),
) {
    val hasMark: Boolean get() = paths.any { it.role == GlyphRole.MARK }
    val hasBody: Boolean get() = paths.any { it.role == GlyphRole.BODY }

    /** The forms this glyph has, as keys, in the generator's order (`formKeysOf` in `forms.ts`). */
    val formKeys: List<String> by lazy {
        val fills =
            buildList {
                add(GlyphFill.Regular)
                if (hasBody) add(GlyphFill.Solid)
                partCombinations(parts).forEach { add(GlyphFill.Parts(it)) }
                for (k in 1..steps) add(GlyphFill.Progress(k))
            }
        val keys =
            fills
                .flatMap { fill ->
                    if (hasMark) listOf(GlyphForm(fill).key, GlyphForm(fill, marked = true).key) else listOf(GlyphForm(fill).key)
                }.toMutableList()
        if (dotted != null) keys.add(if (hasMark) 2 else 1, "dotted")
        keys
    }

    companion object {
        val EMPTY = GlyphData(paths = emptyList())

        /** Every non-empty combination of parts short of all of them, smallest first, in source order (`partCombinations` in `roles.ts`). */
        fun partCombinations(names: List<String>): List<List<String>> {
            val n = names.size
            if (n < 2) return emptyList()
            // `sortedBy` is stable, like JavaScript's sort: the bit order holds within a size.
            return (1 until (1 shl n) - 1)
                .map { bits -> names.filterIndexed { i, _ -> bits and (1 shl i) != 0 } }
                .sortedBy { it.size }
        }
    }
}

/** The resource as a whole. */
@Serializable
data class GlyphFile(
    val version: Int,
    val states: Map<String, List<String>>,
    val glyphs: Map<String, GlyphData>,
) {
    companion object {
        val EMPTY = GlyphFile(version = 1, states = emptyMap(), glyphs = emptyMap())
    }
}
