package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.souls.SoulChooserMode
import app.pbbls.android.core.ui.souls.SoulChooserState
import app.pbbls.android.features.profile.SoulsListContent
import app.pbbls.android.features.profile.SoulsListUiState
import com.android.tools.screenshot.PreviewTest

/*
 * The People tab (#1519, souls spec S10) against mock 13 (browse) and 11 (search). The delete menu
 * is a popup, which a static render does not draw.
 */

private val spiral = GlyphStroke(d = "M30,30 C60,10 140,10 170,30 S190,140 170,170 S60,190 30,170 S10,60 30,30", width = 6.0)
private val wave = GlyphStroke(d = "M 20 120 Q 100 40 180 120", width = 6.0)
private val peak = GlyphStroke(d = "M 40 150 L 100 50 L 160 150", width = 6.0)
private val crossA = GlyphStroke(d = "M 50 50 L 150 150", width = 6.0)
private val crossB = GlyphStroke(d = "M 150 50 L 50 150", width = 6.0)

private fun person(
    name: String,
    count: Int,
    strokes: List<GlyphStroke>,
) = SoulWithGlyph(
    id = "id-$name-$count",
    name = name,
    glyphId = "g-$name",
    glyph = Glyph(id = "g-$name", strokes = strokes, viewBox = "0 0 200 200"),
    pebblesCount = count,
)

private val elevenPeople =
    listOf(
        person("Maman", 142, listOf(spiral)),
        person("Théo", 96, listOf(peak)),
        person("Léa", 71, listOf(wave)),
        person("Papa", 64, listOf(crossA, crossB)),
        person("Inès", 52, listOf(spiral, wave)),
        person("Sam", 47, listOf(peak, wave)),
        person("Mamie Jo", 41, listOf(crossA)),
        person("Nour", 33, listOf(wave, crossB)),
        person("Adèle", 2, listOf(spiral)),
        person("Baptiste", 2, listOf(spiral)),
        person("Camille", 0, listOf(wave)),
    )

@Composable
private fun People(
    uiState: SoulsListUiState,
    query: String? = null,
) {
    val souls = (uiState as? SoulsListUiState.Content)?.souls.orEmpty()
    val chooser =
        remember {
            SoulChooserState(SoulChooserMode.Browse, souls).also {
                if (query != null) {
                    it.openSearch()
                    it.updateQuery(query)
                }
            }
        }
    PebblesTheme {
        SoulsListContent(
            uiState = uiState,
            chooser = chooser,
            onOpenSoul = {},
            onCreate = {},
            onOpenCircle = {},
            onRetry = {},
            onDeleteSoul = {},
            hapticsOn = true,
        )
    }
}

@PreviewTest
@Preview(name = "fr", showBackground = true, locale = "fr", heightDp = 720)
@Preview(showBackground = true, heightDp = 720)
@PreviewLargeFontTall
@Composable
fun People_Light() = People(SoulsListUiState.Content(elevenPeople))

@PreviewTest
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun People_Dark() = People(SoulsListUiState.Content(elevenPeople))

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun People_Search() = People(SoulsListUiState.Content(elevenPeople), query = "ma")

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun People_Empty() = People(SoulsListUiState.Content(emptyList()))

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun People_Error() = People(SoulsListUiState.Error(R.string.souls_load_error))
