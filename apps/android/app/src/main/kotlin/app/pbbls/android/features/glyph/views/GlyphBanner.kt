package app.pbbls.android.features.glyph.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Centered identity banner: a glyph above a title + optional subtitle — ports
 * iOS `Features/Glyph/Views/GlyphBanner.swift`. Shared by the profile header
 * (glyph, display name, "Member since …") and the glyph swap drawer (glyph,
 * name, creator byline). Falls back to the [GlyphViewCase.CARVE] placeholder
 * when there are no strokes.
 */
@Composable
fun GlyphBanner(
    title: String,
    modifier: Modifier = Modifier,
    strokes: List<GlyphStroke>? = null,
    viewBox: String = "0 0 200 200",
    // Profile uses largeTitleHand; the swap drawer keeps the serif-slot title.
    titleStyle: TextStyle = PebblesTypography.title,
    subtitle: GlyphBannerSubtitle? = null,
) {
    val system = PebblesTheme.colors.system
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xxl),
    ) {
        if (strokes.isNullOrEmpty()) {
            GlyphView(case = GlyphViewCase.CARVE, side = 96.dp)
        } else {
            GlyphView(case = GlyphViewCase.PROFILE, strokes = strokes, viewBox = viewBox, side = 96.dp)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs),
        ) {
            PebblesText(text = title, style = titleStyle, color = system.foreground)
            when (subtitle) {
                null -> Unit
                is GlyphBannerSubtitle.Meta ->
                    PebblesText(
                        text = subtitle.text,
                        style = PebblesTypography.meta,
                        color = system.secondary,
                    )
                is GlyphBannerSubtitle.Byline ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        PebblesText(
                            text = stringResource(R.string.glyph_banner_by),
                            style = PebblesTypography.meta,
                            color = system.secondary,
                        )
                        // A name is always hand — issue #515.
                        PebblesText(
                            text = subtitle.name,
                            style = PebblesTypography.bodyLeadHand,
                            color = system.secondary,
                        )
                    }
            }
        }
    }
}

/** A byline renders its name in the handwritten face; meta is a plain small-caps line. */
sealed interface GlyphBannerSubtitle {
    data class Meta(
        val text: String,
    ) : GlyphBannerSubtitle

    data class Byline(
        val name: String,
    ) : GlyphBannerSubtitle
}
