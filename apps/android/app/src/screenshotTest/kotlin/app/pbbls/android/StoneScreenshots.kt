package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.ui.render.BlankStone
import app.pbbls.android.core.ui.render.PebbleStone
import com.android.tools.screenshot.PreviewTest

/**
 * The on-device stone (#1048 Part 3): every valence's shape seed with and
 * without a derived glyph in the deleted engine's slot, coloured through the
 * joy palette as a Path row colours it, and the blank stone (R4) of an
 * uncompleted moment.
 */
private val joyPalette: EmotionPalette =
    requireNotNull(
        EmotionPalette.fromHex(
            primaryHex = "#7B5E99FF",
            secondaryHex = "#AE91CCFF",
            lightHex = "#F2EFF5FF",
            surfaceHex = "#7B5E991A",
            darkHex = "#2A2138FF",
            shadedHex = "#4A3A5CFF",
        ),
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StoneGrid(glyph: PebbleGlyph?) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Valence.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { valence ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PebbleStone(
                            valence = valence,
                            glyph = glyph,
                            colors = joyPalette.pebbleFrameColors(valence.intensity),
                            modifier = Modifier.size(96.dp),
                        )
                        Text(
                            text = valence.name.lowercase(),
                            style = MaterialTheme.typography.labelMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Stones_AllValences_Glyph() {
    PebblesTheme { StoneGrid(glyph = MomentPreviewFixtures.pebbleGlyph()) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun Stones_AllValences_NoGlyph() {
    PebblesTheme { StoneGrid(glyph = null) }
}

@PreviewTest
@Preview(showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Stone_Blank() {
    PebblesTheme { BlankStone(modifier = Modifier.padding(16.dp).size(96.dp)) }
}
