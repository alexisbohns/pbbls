package app.pbbls.android.core.common

import java.util.Locale

/**
 * Numbers spelled out, for the Spine's opening ("the forty-first week", "The
 * fifth to the eleventh of October", "& twelve others"). ICU plurals cover the
 * nouns but not spelled-out numbers, so these are tables. Mirrors
 * `apps/web/lib/i18n/number-words.ts`.
 *
 * Range: cardinals 0-60, ordinals 1-53 (week numbers and days of the month).
 * Outside it, every function falls back to digits rather than inventing words.
 *
 * French gender only shows on 1 in this range ("un/une", "premier/première",
 * and the "et un/et une" of 21, 31, 41, 51), so a [Gender] argument covers it.
 * English ignores it.
 */
object NumberWords {
    enum class Gender { MASCULINE, FEMININE }

    private const val CARDINAL_MAX = 60
    private const val ORDINAL_MAX = 53
    private const val DAY_MAX = 31

    private val EN_UNITS =
        listOf(
            "zero",
            "one",
            "two",
            "three",
            "four",
            "five",
            "six",
            "seven",
            "eight",
            "nine",
            "ten",
            "eleven",
            "twelve",
            "thirteen",
            "fourteen",
            "fifteen",
            "sixteen",
            "seventeen",
            "eighteen",
            "nineteen",
        )
    private val EN_TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty")

    private val EN_ORDINAL_UNITS =
        listOf(
            "zeroth",
            "first",
            "second",
            "third",
            "fourth",
            "fifth",
            "sixth",
            "seventh",
            "eighth",
            "ninth",
            "tenth",
            "eleventh",
            "twelfth",
            "thirteenth",
            "fourteenth",
            "fifteenth",
            "sixteenth",
            "seventeenth",
            "eighteenth",
            "nineteenth",
        )
    private val EN_ORDINAL_TENS =
        listOf("", "", "twentieth", "thirtieth", "fortieth", "fiftieth", "sixtieth")

    private val FR_UNITS =
        listOf(
            "zéro",
            "un",
            "deux",
            "trois",
            "quatre",
            "cinq",
            "six",
            "sept",
            "huit",
            "neuf",
            "dix",
            "onze",
            "douze",
            "treize",
            "quatorze",
            "quinze",
            "seize",
            "dix-sept",
            "dix-huit",
            "dix-neuf",
        )
    private val FR_TENS = listOf("", "", "vingt", "trente", "quarante", "cinquante", "soixante")

    private fun isFrench(locale: Locale) = locale.language == "fr"

    private fun enCardinal(n: Int): String {
        if (n < 20) return EN_UNITS[n]
        val tens = n / 10
        val unit = n % 10
        return if (unit == 0) EN_TENS[tens] else "${EN_TENS[tens]}-${EN_UNITS[unit]}"
    }

    private fun enOrdinal(n: Int): String {
        if (n < 20) return EN_ORDINAL_UNITS[n]
        val tens = n / 10
        val unit = n % 10
        return if (unit == 0) EN_ORDINAL_TENS[tens] else "${EN_TENS[tens]}-${EN_ORDINAL_UNITS[unit]}"
    }

    private fun frCardinal(
        n: Int,
        gender: Gender,
    ): String {
        val un = if (gender == Gender.FEMININE) "une" else "un"
        if (n == 1) return un
        if (n < 20) return FR_UNITS[n]
        val tens = n / 10
        val unit = n % 10
        return when (unit) {
            0 -> FR_TENS[tens]
            1 -> "${FR_TENS[tens]} et $un"
            else -> "${FR_TENS[tens]}-${FR_UNITS[unit]}"
        }
    }

    /** The ordinal is the cardinal plus "-ième", with the few spelling changes. */
    private fun frOrdinal(
        n: Int,
        gender: Gender,
    ): String {
        if (n == 1) return if (gender == Gender.FEMININE) "première" else "premier"
        val cardinal = frCardinal(n, Gender.MASCULINE)
        return when {
            cardinal.endsWith("cinq") -> "${cardinal}uième"
            cardinal.endsWith("neuf") -> "${cardinal.dropLast(1)}vième"
            cardinal.endsWith("e") -> "${cardinal.dropLast(1)}ième"
            else -> "${cardinal}ième"
        }
    }

    /** "twelve" / "douze". Feminine matters in French for 1, 21, 31, 41, 51. */
    fun cardinal(
        n: Int,
        locale: Locale,
        gender: Gender = Gender.MASCULINE,
    ): String {
        if (n !in 0..CARDINAL_MAX) return n.toString()
        return if (isFrench(locale)) frCardinal(n, gender) else enCardinal(n)
    }

    /** "forty-first" / "quarante et unième". Weeks are feminine in French. */
    fun ordinal(
        n: Int,
        locale: Locale,
        gender: Gender = Gender.MASCULINE,
    ): String {
        if (n !in 1..ORDINAL_MAX) return n.toString()
        return if (isFrench(locale)) frOrdinal(n, gender) else enOrdinal(n)
    }

    /**
     * A day of the month as a title sets it: English reads ordinals ("the fifth"),
     * French reads cardinals with "premier" for the 1st ("du premier au sept").
     */
    fun dayOfMonth(
        day: Int,
        locale: Locale,
    ): String {
        if (day !in 1..DAY_MAX) return day.toString()
        if (isFrench(locale)) return if (day == 1) "premier" else frCardinal(day, Gender.MASCULINE)
        return enOrdinal(day)
    }
}
