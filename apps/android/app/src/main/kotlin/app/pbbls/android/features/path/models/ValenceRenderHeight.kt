package app.pbbls.android.features.path.models

/**
 * Render height (in dp) for the pebble render header in the edit form — ports
 * iOS `ValenceSizeGroup.renderHeight` (Valence.swift). Small pebbles render
 * smaller so a small render doesn't dominate a medium/large one (iOS issue
 * #286). Returned as an Int so the mapping stays pure/JVM-testable; the
 * composable appends `.dp`.
 */
val ValenceSizeGroup.renderHeightDp: Int
    get() =
        when (this) {
            ValenceSizeGroup.SMALL -> 180
            ValenceSizeGroup.MEDIUM -> 220
            ValenceSizeGroup.LARGE -> 260
        }
