package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.glyph.views.GlyphBanner
import app.pbbls.android.features.glyph.views.GlyphBannerSubtitle
import app.pbbls.android.features.glyph.views.GlyphView
import app.pbbls.android.features.glyph.views.GlyphViewCase
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import com.android.tools.screenshot.PreviewTest

/**
 * Glyph chrome previews (#565): every [GlyphViewCase] against the iOS
 * preview stroke (the same `d` as `GlyphView.swift`'s previews) plus the
 * [GlyphBanner] meta and byline variants — light and dark. Compare against
 * the iOS `GlyphView` "All cases" preview for the #459/#515 spec table.
 */
private val previewStrokes =
    listOf(
        GlyphStroke(
            d = "M40,40 C80,20 120,20 160,40 S180,120 160,160 S80,180 40,160 S20,80 40,40",
            width = 6.0,
        ),
    )

@Composable
private fun GlyphGallery() {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            Modifier
                .background(system.background)
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(17.dp)) {
            LabeledCase(GlyphViewCase.PROFILE, "PROFILE")
            LabeledCase(GlyphViewCase.CARVE, "CARVE")
            LabeledCase(GlyphViewCase.CREATE, "CREATE")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(17.dp)) {
            LabeledCase(GlyphViewCase.SELECTED, "SELECTED")
            LabeledCase(GlyphViewCase.UNSELECTED, "UNSELECTED")
            LabeledCase(GlyphViewCase.DEFAULT, "DEFAULT")
        }
        GlyphBanner(
            title = "Alexis",
            strokes = previewStrokes,
            titleStyle = PebblesTypography.largeTitleHand,
            subtitle = GlyphBannerSubtitle.Meta("Member since July 2026"),
        )
        GlyphBanner(
            title = "Creature",
            strokes = previewStrokes,
            subtitle = GlyphBannerSubtitle.Byline(name = "Galadriel"),
        )
        // Glyph-less profile falls back to the CARVE placeholder.
        GlyphBanner(title = "Pebbler", subtitle = GlyphBannerSubtitle.Meta("Member since July 2026"))
    }
}

@Composable
private fun LabeledCase(
    case: GlyphViewCase,
    label: String,
) {
    val system = PebblesTheme.colors.system
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val strokes = if (case == GlyphViewCase.CARVE || case == GlyphViewCase.CREATE) null else previewStrokes
        GlyphView(case = case, strokes = strokes)
        PebblesText(label, PebblesTypography.meta, color = system.secondary)
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun GlyphChromeLight() {
    PebblesTheme { GlyphGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun GlyphChromeDark() {
    PebblesTheme { GlyphGallery() }
}
