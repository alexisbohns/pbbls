package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.ui.GlyphBanner
import app.pbbls.android.core.ui.GlyphBannerSubtitle
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase
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
    Column(
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surface)
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
            titleStyle = PebblesTheme.hand.largeTitleHand,
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val strokes = if (case == GlyphViewCase.CARVE || case == GlyphViewCase.CREATE) null else previewStrokes
        GlyphView(case = case, strokes = strokes)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
