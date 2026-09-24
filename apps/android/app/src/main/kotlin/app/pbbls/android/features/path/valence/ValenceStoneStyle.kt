package app.pbbls.android.features.path.valence

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.core.graphics.toColorInt
import app.pbbls.android.core.model.ValencePolarity

/**
 * Backdrop wash and ink for one polarity of valence stone — ports iOS
 * `ValenceStoneStyle.swift`.
 *
 * Mirrors the roles `EmotionPalette.pebbleFrameColors` hands a real pebble: the
 * backdrop is a soft silhouette *behind* the artwork, and the ink is what the
 * artwork's lines are drawn in. Highlight is the interesting one: backdrop and
 * ink are the *same* gradient at two intensities, so the lines read as the
 * vivid edge of a soft wash rather than as a separate colour.
 *
 * [backdropAlpha] rides beside the brush because a `ShaderBrush` carries no
 * opacity of its own — the way iOS spells `highlightWash.opacity(0.35)`.
 */
internal data class ValenceStoneStyle(
    val backdrop: Brush,
    val ink: Brush,
    val backdropAlpha: Float = 1f,
)

/**
 * The nine stones' materials, resolved from the active [ColorScheme].
 *
 * Selection **inverts the two roles**: the wash becomes the solid and the ink
 * goes pale, so the chosen stone reads as filled in rather than merely less
 * faded than its neighbours. It is the same treatment
 * `EmotionPalette.pebbleFrameColors(intensity = 3)` gives a hero pebble on the
 * Path — a `light` stroke over an opaque `primary` fill.
 */
internal object ValenceStoneStyles {
    /**
     * Joy's `surface_color` at 10%, copied for the same reason as the gradient
     * samples: `EmotionPaletteService` needs the network and is not loaded when
     * this view first draws. Emotion data, so it is parsed from its hex like
     * the mesh rather than written as a theme literal.
     */
    private val joySurface =
        Color(ValenceMesh.JOY_SURFACE_HEX.toColorInt()).copy(alpha = 0.10f)

    private val wash = MeshShaderBrush(ValenceMesh.washHexes)
    private val selectedWash = MeshShaderBrush(ValenceMesh.selectedWashHexes)
    private val ink = MeshShaderBrush(ValenceMesh.inkHexes)

    fun style(
        polarity: ValencePolarity,
        isSelected: Boolean,
        isDark: Boolean,
        scheme: ColorScheme,
    ): ValenceStoneStyle =
        if (isSelected) {
            when (polarity) {
                // The inverse pair is M3's own "flip the surface" role, which is
                // exactly what selection does to the grey stone.
                ValencePolarity.LOWLIGHT -> {
                    ValenceStoneStyle(SolidColor(scheme.inverseSurface), SolidColor(scheme.inverseOnSurface))
                }

                ValencePolarity.NEUTRAL -> {
                    ValenceStoneStyle(SolidColor(scheme.primary), SolidColor(scheme.onPrimary))
                }

                // White rather than `onPrimary`, and against a wash taken to
                // full strength: on the resting peach the artwork had almost
                // nothing to push against. The mesh is baked from fixed samples
                // and does not follow the theme, so its ink cannot either.
                ValencePolarity.HIGHLIGHT -> {
                    ValenceStoneStyle(selectedWash, SolidColor(Color.White))
                }
            }
        } else {
            when (polarity) {
                ValencePolarity.LOWLIGHT -> {
                    ValenceStoneStyle(
                        SolidColor(scheme.surfaceContainerHighest),
                        SolidColor(scheme.onSurfaceVariant),
                    )
                }

                // `primary` rather than the paired `onPrimaryContainer`: the
                // artwork is what makes the neutral stone rose, and the two
                // tones sit far enough apart for line art (3:1).
                ValencePolarity.NEUTRAL -> {
                    ValenceStoneStyle(SolidColor(scheme.primaryContainer), SolidColor(scheme.primary))
                }

                ValencePolarity.HIGHLIGHT -> {
                    ValenceStoneStyle(restingWash(isDark), ink, restingWashAlpha(isDark))
                }
            }
        }

    /**
     * Fill for the headline word naming the picked valence. Highlight carries
     * the same gradient its stone does, so the word and the stone read as one
     * thing. Lowlight goes darker than its stone ink: at headline size a grey
     * word looks disabled rather than quiet.
     */
    fun headlineInk(
        polarity: ValencePolarity,
        scheme: ColorScheme,
    ): Brush =
        when (polarity) {
            ValencePolarity.LOWLIGHT -> SolidColor(scheme.onSurface)
            ValencePolarity.NEUTRAL -> SolidColor(scheme.primary)
            ValencePolarity.HIGHLIGHT -> ink
        }

    /**
     * The unselected highlight stone's wash.
     *
     * Light mode keeps the sampled gradient at low opacity: over a light page
     * it stays the pastel it was sampled from. Dark mode cannot — the same
     * gradient over black goes muddy and opaque, and the highlight stone ends
     * up looking nothing like its neighbours, which wear flat container
     * colours (neutral's `primaryContainer`). So dark mode joins that
     * convention rather than fighting it, in Joy's own warm gold, which keeps
     * highlight distinct from neutral's rose. That gold is emotion data (like
     * the mesh samples), not a theme role: no scheme role means "joy".
     */
    private fun restingWash(isDark: Boolean): Brush = if (isDark) SolidColor(joySurface) else wash

    private fun restingWashAlpha(isDark: Boolean): Float = if (isDark) 1f else 0.35f
}

/**
 * A baked mesh gradient as a Compose [Brush].
 *
 * The pixels come from [ValenceMesh.bake]; the shader stretches that small
 * square over whatever it fills, with linear filtering so the upscale stays
 * smooth (`FILTER_MODE_LINEAR` is API 31, well under minSdk 33). `ShaderBrush`
 * memoizes the shader per size, so a stone pays for its own shader once.
 *
 * The bitmap is built lazily and kept for the process: three gradients at
 * [ValenceMesh.RESOLUTION]² are ~48KB in total, and rebuilding one per
 * composition would be sixteen distance calculations per pixel per frame.
 */
private class MeshShaderBrush(
    private val hexes: List<String>,
) : ShaderBrush() {
    private val bitmap: Bitmap by lazy {
        Bitmap.createBitmap(
            ValenceMesh.bake(hexes),
            ValenceMesh.RESOLUTION,
            ValenceMesh.RESOLUTION,
            Bitmap.Config.ARGB_8888,
        )
    }

    // `android.graphics.Shader` rather than the Compose alias for it: they are
    // the same class, and only the platform name lets `Shader.TileMode` resolve.
    override fun createShader(size: Size): Shader =
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            setLocalMatrix(
                Matrix().apply {
                    setScale(size.width / bitmap.width, size.height / bitmap.height)
                },
            )
        }
}
