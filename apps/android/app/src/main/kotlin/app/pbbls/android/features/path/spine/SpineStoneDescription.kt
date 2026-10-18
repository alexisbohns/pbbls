package app.pbbls.android.features.path.spine

import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup

/**
 * The resolved templates a stone's description is set from, so the builder is
 * pure and the test needs no resources.
 *
 * @property stone `path_spine_stone_a11y`: name, date, time, kind, emotion, with-names.
 * @property blank `path_spine_blank_a11y`: name, date, time, with-names.
 * @property withNames `path_spine_with_names`: the names, one argument.
 * @property untitled what a nameless moment is called.
 */
internal data class StoneDescriptionStrings(
    val stone: String,
    val blank: String,
    val withNames: String,
    val untitled: String,
)

/**
 * The stone's `contentDescription` (spec §Accessibility), shared by the stone's hit
 * box and the card or entry: "Flag march with Nadia. 5 October 2026, 10:15. Large gem,
 * Pride. With Nadia. Opens the pebble." A moment that is not a pebble yet says so
 * instead of a kind and an emotion; a solitary pebble names nobody.
 *
 * @param names who was there, already in the people line's order; empty when solitary.
 */
internal fun stoneDescription(
    name: String?,
    date: String,
    time: String,
    kind: String,
    emotion: String,
    names: List<String>,
    blank: Boolean,
    strings: StoneDescriptionStrings,
): String {
    val title = name?.takeIf { it.isNotBlank() } ?: strings.untitled
    val with = if (names.isEmpty()) "" else strings.withNames.format(names.joinToString(", "))
    val text =
        if (blank) {
            strings.blank.format(title, date, time, with)
        } else {
            strings.stone.format(title, date, time, kind, emotion, with)
        }
    // An empty with-names leaves two spaces around its slot.
    return text.replace("  ", " ")
}

/** The nine stone kinds, "Small lava stone" to "Large gem", by the stone's own size and polarity. */
@StringRes
internal fun stoneKindRes(
    size: ValenceSizeGroup,
    polarity: ValencePolarity,
): Int =
    when (size) {
        ValenceSizeGroup.SMALL ->
            when (polarity) {
                ValencePolarity.LOWLIGHT -> R.string.path_spine_stone_small_lowlight
                ValencePolarity.NEUTRAL -> R.string.path_spine_stone_small_neutral
                ValencePolarity.HIGHLIGHT -> R.string.path_spine_stone_small_highlight
            }
        ValenceSizeGroup.MEDIUM ->
            when (polarity) {
                ValencePolarity.LOWLIGHT -> R.string.path_spine_stone_medium_lowlight
                ValencePolarity.NEUTRAL -> R.string.path_spine_stone_medium_neutral
                ValencePolarity.HIGHLIGHT -> R.string.path_spine_stone_medium_highlight
            }
        ValenceSizeGroup.LARGE ->
            when (polarity) {
                ValencePolarity.LOWLIGHT -> R.string.path_spine_stone_large_lowlight
                ValencePolarity.NEUTRAL -> R.string.path_spine_stone_large_neutral
                ValencePolarity.HIGHLIGHT -> R.string.path_spine_stone_large_highlight
            }
    }
