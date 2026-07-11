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
    }
}
