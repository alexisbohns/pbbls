package app.pbbls.android.features.path.models

import androidx.compose.ui.graphics.Color

/**
 * The colors of an emotion category palette — mirrors iOS `EmotionPalette.swift`.
 * Initialized from the 8-digit `#RRGGBBAA` hex strings stored on
 * `emotion_categories` (hand-entered in Supabase Studio — may carry stray
 * whitespace, trimmed at this boundary). [fromHex] returns `null` if any hex
 * fails to parse; callers treat the palette as unavailable and fall back to the
 * brand accent.
 *
 * [dark] is the #599 addition: the small/medium Petroglyph backfill in dark
 * mode (the "palette.dark" role). The sibling `shaded_color` column also exists
 * on the view but has no Android consumer yet, so it is intentionally not
 * modelled here.
 */
data class EmotionPalette(
    val primary: Color,
    val secondary: Color,
    val light: Color,
    val surface: Color,
    val dark: Color,
    val primaryHex: String,
    val secondaryHex: String,
    val lightHex: String,
    val surfaceHex: String,
    val darkHex: String,
) {
    /** Pebble stroke color: `primary` in light mode, `secondary` in dark. */
    fun stroke(isDark: Boolean): Color = if (isDark) secondary else primary

    /**
     * 6-digit `#RRGGBB` for SVG-text injection — SVG engines misparse the
     * 8-digit DB form; strokes are always opaque so dropping the alpha bytes
     * is lossless.
     */
    fun strokeHex(isDark: Boolean): String = rgbHex(if (isDark) secondaryHex else primaryHex)

    /**
     * Single source of truth for the intensity → role mapping (mirrors iOS):
     * - intensity 3 (large): `light` stroke + opaque `primary` fill.
     * - intensity 1 / 2: `secondary` stroke + `surface` fill (surface is
     *   seeded at ~10% alpha, so the silhouette reads as a faint wash).
     */
    fun pebbleFrameColors(intensity: Int): PebbleFrameColors =
        when (intensity) {
            3 ->
                PebbleFrameColors(
                    strokeHex = rgbHex(lightHex),
                    fillHex = rgbHex(primaryHex),
                    fillOpacity = alphaComponent(primaryHex),
                )
            else ->
                PebbleFrameColors(
                    strokeHex = rgbHex(secondaryHex),
                    fillHex = rgbHex(surfaceHex),
                    fillOpacity = alphaComponent(surfaceHex),
                )
        }

    companion object {
        /** Builds a palette from the DB hex columns; null if any fails. */
        fun fromHex(
            primaryHex: String,
            secondaryHex: String,
            lightHex: String,
            surfaceHex: String,
            darkHex: String,
        ): EmotionPalette? {
            val trimmedPrimary = primaryHex.trim()
            val trimmedSecondary = secondaryHex.trim()
            val trimmedLight = lightHex.trim()
            val trimmedSurface = surfaceHex.trim()
            val trimmedDark = darkHex.trim()
            return EmotionPalette(
                primary = parseColor(trimmedPrimary) ?: return null,
                secondary = parseColor(trimmedSecondary) ?: return null,
                light = parseColor(trimmedLight) ?: return null,
                surface = parseColor(trimmedSurface) ?: return null,
                dark = parseColor(trimmedDark) ?: return null,
                primaryHex = trimmedPrimary,
                secondaryHex = trimmedSecondary,
                lightHex = trimmedLight,
                surfaceHex = trimmedSurface,
                darkHex = trimmedDark,
            )
        }

        /**
         * Parses `#RRGGBB` / `#RRGGBBAA` (leading `#` optional, surrounding
         * whitespace tolerated) into a Compose [Color]; null on anything
         * else. Note the DB stores RGBA byte order while Compose wants ARGB —
         * the alpha byte moves from the tail to the head.
         */
        fun parseColor(hex: String): Color? {
            val trimmed = hex.trim().removePrefix("#")
            val value = trimmed.toLongOrNull(16) ?: return null
            return when (trimmed.length) {
                6 -> Color(0xFF000000L or value)
                8 -> {
                    val alpha = value and 0xFF
                    val rgb = value ushr 8
                    Color((alpha shl 24) or rgb)
                }
                else -> null
            }
        }

        /** `#RRGGBBAA` → `#RRGGBB`; 6-digit and unrecognized input pass through. */
        internal fun rgbHex(hex: String): String = if (hex.length == 9) hex.take(7) else hex

        /** Alpha byte of `#RRGGBBAA` as 0..1; 1 for 6-digit or unparseable input. */
        internal fun alphaComponent(hex: String): Float {
            if (hex.length != 9) return 1f
            val byte = hex.takeLast(2).toIntOrNull(16) ?: return 1f
            return byte / 255f
        }
    }
}

/**
 * Render-ready colors handed to the pebble render stack — all sanitized for
 * direct SVG injection: [strokeHex] and [fillHex] are 6-digit `#RRGGBB`, and
 * the fill alpha rides separately in [fillOpacity] because it cannot ride
 * along in a 6-digit hex.
 */
data class PebbleFrameColors(
    val strokeHex: String,
    val fillHex: String,
    val fillOpacity: Float,
)
