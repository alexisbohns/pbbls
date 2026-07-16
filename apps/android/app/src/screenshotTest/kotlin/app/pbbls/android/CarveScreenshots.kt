package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.glyph.carve.CarveCanvas
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest

/**
 * Carve studio previews (M43 #584): the canvas empty and with committed
 * strokes (re-rendered through the shared GlyphImage pipeline), light and
 * dark — the canvas itself stays hard-white in both (design D2). The full
 * screen reads services; the canvas is the pure review surface.
 */
private val committedStrokes =
    listOf(
        GlyphStroke(d = "M30,30 Q60,10 95,15 Q140,20 170,60 L170,90", width = 6.0),
        GlyphStroke(d = "M40,140 L160,140", width = 6.0),
        GlyphStroke(d = "M100,100 L100,100", width = 6.0),
    )

@Composable
private fun CarveGallery() {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            Modifier
                .background(system.background)
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CarveCanvas(strokes = emptyList(), onStrokeCommitted = {})
        CarveCanvas(strokes = committedStrokes, onStrokeCommitted = {})
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun CarveGalleryLight() {
    PebblesTheme { CarveGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CarveGalleryDark() {
    PebblesTheme { CarveGallery() }
}
