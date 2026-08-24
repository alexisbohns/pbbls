package app.pbbls.android.features.path.models

/**
 * Size axis of a valence — drives outline asset choice and render sizing.
 * [key] is the lowercase asset-name segment (`outline_<size>_<polarity>`).
 */
enum class ValenceSizeGroup(
    val key: String,
) {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large"),
    ;

    companion object {
        /**
         * Top to bottom, the order the valence roll stacks sizes in — a big
         * event sits above a small one, so reaching the smaller ones means
         * scrolling down the ladder. Deliberately *not* [entries], which runs
         * small → large.
         */
        val ladder: List<ValenceSizeGroup> = listOf(LARGE, MEDIUM, SMALL)
    }
}

/** Polarity axis of a valence. [key] as in [ValenceSizeGroup.key]. */
enum class ValencePolarity(
    val key: String,
) {
    LOWLIGHT("lowlight"),
    NEUTRAL("neutral"),
    HIGHLIGHT("highlight"),
}

/**
 * The nine `(positiveness, intensity)` combinations — mirrors iOS
 * `Valence.swift`. Read-only in this milestone: only the derivation from DB
 * values is needed; the picker labels/images stay iOS-side until the create
 * flow ports.
 */
enum class Valence(
    val positiveness: Int,
    val intensity: Int,
) {
    LOWLIGHT_SMALL(-1, 1),
    LOWLIGHT_MEDIUM(-1, 2),
    LOWLIGHT_LARGE(-1, 3),
    NEUTRAL_SMALL(0, 1),
    NEUTRAL_MEDIUM(0, 2),
    NEUTRAL_LARGE(0, 3),
    HIGHLIGHT_SMALL(1, 1),
    HIGHLIGHT_MEDIUM(1, 2),
    HIGHLIGHT_LARGE(1, 3),
    ;

    val sizeGroup: ValenceSizeGroup
        get() =
            when (intensity) {
                1 -> ValenceSizeGroup.SMALL
                2 -> ValenceSizeGroup.MEDIUM
                else -> ValenceSizeGroup.LARGE
            }

    val polarity: ValencePolarity
        get() =
            when (positiveness) {
                -1 -> ValencePolarity.LOWLIGHT
                0 -> ValencePolarity.NEUTRAL
                else -> ValencePolarity.HIGHLIGHT
            }

    companion object {
        /**
         * Mirrors `Pebble.valence` on iOS: DB CHECK constraints guarantee the
         * pair, so an out-of-range value is decode drift — fall back to
         * [NEUTRAL_MEDIUM]. Deliberately log-free (pure JVM-tested code;
         * `android.util.Log` throws off-device) — `PathService` logs any
         * out-of-range pair after fetch.
         */
        fun fromOrDefault(
            positiveness: Int,
            intensity: Int,
        ): Valence =
            entries.firstOrNull { it.positiveness == positiveness && it.intensity == intensity }
                ?: NEUTRAL_MEDIUM

        /**
         * Null-returning variant for drafts (M47), where the pair may be absent
         * entirely. Deliberately does NOT fall back to [NEUTRAL_MEDIUM]: a draft
         * with no valence picked yet must hydrate the picker as unset rather than
         * claim a value the user never chose.
         */
        fun orNull(
            positiveness: Int?,
            intensity: Int?,
        ): Valence? {
            if (positiveness == null || intensity == null) return null
            return entries.firstOrNull { it.positiveness == positiveness && it.intensity == intensity }
        }
    }
}

/*
 * The roll — step geometry for the two-axis valence picker, porting the same
 * extension on iOS `Valence.swift`. Polarity runs left → right, size runs top
 * → bottom (`ValenceSizeGroup.ladder`).
 *
 * Pure index arithmetic, kept off the view so the roll's behaviour is asserted
 * without a gesture (`ValenceRollTest`).
 */

/**
 * The one case at a given cell. Total by construction: the 3×3 grid is
 * covered, which `ValenceRollTest` pins down.
 */
fun valenceAt(
    polarity: ValencePolarity,
    size: ValenceSizeGroup,
): Valence = Valence.entries.first { it.polarity == polarity && it.sizeGroup == size }

/**
 * The valence at the given indices, clamped to the grid — the roll stops at
 * the edges rather than wrapping, so a hard swipe cannot loop the user past
 * the end and back to where they started.
 */
fun valenceAt(
    polarityIndex: Int,
    sizeIndex: Int,
): Valence =
    valenceAt(
        polarity = ValencePolarity.entries[polarityIndex.coerceIn(0, ValencePolarity.entries.lastIndex)],
        size = ValenceSizeGroup.ladder[sizeIndex.coerceIn(0, ValenceSizeGroup.ladder.lastIndex)],
    )

/** Position on the polarity axis, in [ValencePolarity.entries] order. */
val Valence.polarityIndex: Int
    get() = ValencePolarity.entries.indexOf(polarity)

/** Position on the size axis, in the roll's top-to-bottom [ValenceSizeGroup.ladder] order. */
val Valence.sizeIndex: Int
    get() = ValenceSizeGroup.ladder.indexOf(sizeGroup)

/**
 * The polarity one step to the left, null at the end. Drives the faded
 * neighbour word the roll shows on that side.
 */
val Valence.polarityBefore: ValencePolarity?
    get() = ValencePolarity.entries.getOrNull(polarityIndex - 1)

/** The polarity one step to the right, null at the end. */
val Valence.polarityAfter: ValencePolarity?
    get() = ValencePolarity.entries.getOrNull(polarityIndex + 1)
