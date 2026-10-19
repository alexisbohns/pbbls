package app.pbbls.android.features.path.spine.logic

import app.pbbls.android.core.model.ValencePolarity

/** A name's type rank: size in sp, weight on the variable axis, cut, tracking in em. */
data class NameType(
    val fontSizeSp: Float,
    val fontWeight: Int,
    val italic: Boolean,
    val letterSpacingEm: Float,
)

data class EntryType(
    val name: NameType,
    val lineHeight: Float,
    /** The stone's centre, in dp from the entry's top: the name's first line. */
    val anchorDp: Float,
)

/**
 * Port of `entry-type.ts` (#1052, spec §No-photo entry, §Contents, §Card
 * anatomy), phone values only (spec D4). Intensity sets the size, valence sets
 * the cut, so neither ever rests on colour alone.
 */
object EntryTypes {
    private val ENTRY_SIZE = mapOf(1 to 19f, 2 to 24f, 3 to 31f)
    private val ENTRY_WEIGHT =
        mapOf(ValencePolarity.HIGHLIGHT to 600, ValencePolarity.NEUTRAL to 460, ValencePolarity.LOWLIGHT to 340)
    private val CONTENTS_WEIGHT = mapOf(1 to 400, 2 to 470, 3 to 620)
    private val CARD_TITLE_WEIGHT =
        mapOf(ValencePolarity.HIGHLIGHT to 600, ValencePolarity.NEUTRAL to 480, ValencePolarity.LOWLIGHT to 360)

    private const val LOWLIGHT_CONTENTS_WEIGHT = 350
    private const val CONTENTS_SIZE = 16.5f
    private const val LINE_HEIGHT = 1.12f
    private const val ANCHOR_RATIO = 0.6f
    private const val TRACKING = -0.012f
    private const val LOWLIGHT_TRACKING = -0.004f

    private fun checkIntensity(intensity: Int) {
        require(intensity in 1..3) { "intensity must be 1..3, was $intensity" }
    }

    /** A no-photo entry's name: no box, set as type. */
    fun entry(
        intensity: Int,
        polarity: ValencePolarity,
    ): EntryType {
        checkIntensity(intensity)
        val fontSize = ENTRY_SIZE.getValue(intensity)
        val lowlight = polarity == ValencePolarity.LOWLIGHT
        return EntryType(
            name =
                NameType(
                    fontSizeSp = fontSize,
                    fontWeight = ENTRY_WEIGHT.getValue(polarity),
                    italic = lowlight,
                    letterSpacingEm = if (lowlight) LOWLIGHT_TRACKING else TRACKING,
                ),
            lineHeight = LINE_HEIGHT,
            anchorDp = fontSize * ANCHOR_RATIO,
        )
    }

    /** A contents row's name: weight by intensity, lowlights in a light italic. */
    fun contentsName(
        intensity: Int,
        polarity: ValencePolarity,
    ): NameType {
        checkIntensity(intensity)
        val lowlight = polarity == ValencePolarity.LOWLIGHT
        return NameType(
            fontSizeSp = CONTENTS_SIZE,
            fontWeight = if (lowlight) LOWLIGHT_CONTENTS_WEIGHT else CONTENTS_WEIGHT.getValue(intensity),
            italic = lowlight,
            letterSpacingEm = 0f,
        )
    }

    /**
     * A card's title cut by valence, as (weight, italic). Its size is in card
     * units, which only the card's own width knows, so it is not returned here.
     */
    fun cardTitleCut(polarity: ValencePolarity): Pair<Int, Boolean> =
        CARD_TITLE_WEIGHT.getValue(polarity) to (polarity == ValencePolarity.LOWLIGHT)
}
