package app.pbbls.android.features.path.spine

import app.pbbls.android.features.path.spine.logic.WeekWordsBuilder
import java.util.Locale

/**
 * The opening summary's resolved formats: the plural choice is the caller's (`<plurals>`
 * on the device, a lambda in a test), so the sentence itself stays pure.
 *
 * @property noPebbles "no pebbles".
 * @property pebbles the pebbles form for a count, with `%1$s` for the number in words.
 * @property people the people form for a count, leading comma included, with `%1$s`.
 */
internal data class OpeningStrings(
    val noPebbles: String,
    val pebbles: (count: Int) -> String,
    val people: (count: Int) -> String,
)

/**
 * The web's `summary` message, by hand: "Eleven pebbles, five people.", "No pebbles.",
 * "Un galet, une personne." — the counts choose the forms, the words fill them, and the
 * sentence opens in capitals. No people, no clause.
 */
internal fun openingSummary(
    pebbleCount: Int,
    peopleCount: Int,
    pebblesWords: String,
    peopleWords: String,
    strings: OpeningStrings,
    locale: Locale,
): String {
    val pebbles = if (pebbleCount == 0) strings.noPebbles else strings.pebbles(pebbleCount).format(locale, pebblesWords)
    val people = if (peopleCount == 0) "" else strings.people(peopleCount).format(locale, peopleWords)
    return WeekWordsBuilder.sentenceCase("$pebbles$people.", locale)
}
