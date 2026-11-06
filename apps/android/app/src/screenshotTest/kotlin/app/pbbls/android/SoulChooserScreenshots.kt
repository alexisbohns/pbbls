package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.souls.SoulChooserBody
import app.pbbls.android.core.ui.souls.SoulChooserMode
import app.pbbls.android.core.ui.souls.SoulChooserSearchButton
import app.pbbls.android.core.ui.souls.SoulChooserSearchField
import app.pbbls.android.core.ui.souls.SoulChooserSheetContent
import app.pbbls.android.core.ui.souls.SoulChooserState
import com.android.tools.screenshot.PreviewTest

/*
 * The souls chooser (#1519) against the approved Android mocks: screen 10 (picker), 11 (search)
 * and 13 (browse). Pick frames render the sheet's own content; browse frames the body the way
 * the People tab does.
 */

private val spiral = GlyphStroke(d = "M30,30 C60,10 140,10 170,30 S190,140 170,170 S60,190 30,170 S10,60 30,30", width = 6.0)
private val wave = GlyphStroke(d = "M 20 120 Q 100 40 180 120", width = 6.0)
private val peak = GlyphStroke(d = "M 40 150 L 100 50 L 160 150", width = 6.0)
private val crossA = GlyphStroke(d = "M 50 50 L 150 150", width = 6.0)
private val crossB = GlyphStroke(d = "M 150 50 L 50 150", width = 6.0)

private fun soul(
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

private val elevenSouls =
    listOf(
        soul("Maman", 142, listOf(spiral)),
        soul("Théo", 96, listOf(peak)),
        soul("Léa", 71, listOf(wave)),
        soul("Papa", 64, listOf(crossA, crossB)),
        soul("Inès", 52, listOf(spiral, wave)),
        soul("Sam", 47, listOf(peak, wave)),
        soul("Mamie Jo", 41, listOf(crossA)),
        soul("Nour", 33, listOf(wave, crossB)),
        soul("Adèle", 2, listOf(spiral)),
        soul("Baptiste", 2, listOf(spiral)),
        soul("Camille", 0, listOf(wave)),
    )

private val searchSouls =
    listOf(
        soul("Maman", 142, listOf(spiral)),
        soul("Mamie Jo", 41, listOf(crossA)),
        soul("Marc", 15, listOf(wave)),
        soul("Margaux", 1, listOf(spiral)),
        soul("Marie", 14, listOf(peak)),
        soul("Marie (yoga)", 9, listOf(crossB)),
        soul("Théo", 96, listOf(peak)),
    )

@Composable
private fun ChooserFrame(
    state: SoulChooserState,
    isLoaded: Boolean = true,
    loadFailed: Boolean = false,
    title: String? = stringResource(R.string.souls_who_was_there),
) {
    PebblesTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            if (state.mode is SoulChooserMode.Pick) {
                // The real sheet content, so what is rendered is what the sheet draws.
                SoulChooserSheetContent(
                    state = state,
                    title = title.orEmpty(),
                    isLoaded = isLoaded,
                    loadFailed = loadFailed,
                    hapticsOn = true,
                    onBack = {},
                    onDone = {},
                    onCreate = {},
                    onRetry = {},
                    onHaptic = {},
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                BrowseFrame(state, isLoaded, loadFailed, title)
            }
        }
    }
}

@Composable
private fun BrowseFrame(
    state: SoulChooserState,
    isLoaded: Boolean,
    loadFailed: Boolean,
    title: String?,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        when {
            state.isSearching ->
                SoulChooserSearchField(
                    state = state,
                    onClose = state::closeSearch,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
            title != null ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = null)
                    Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    SoulChooserSearchButton(state = state)
                }
        }
        Box(modifier = Modifier.weight(1f)) {
            SoulChooserBody(
                state = state,
                isLoaded = isLoaded,
                loadFailed = loadFailed,
                onRetry = {},
                onOpenSoul = {},
                onCreate = {},
                onHaptic = {},
                hapticsOn = true,
            )
        }
    }
}

private fun picker(
    souls: List<SoulWithGlyph>,
    selection: List<String> = emptyList(),
) = SoulChooserState(SoulChooserMode.Pick(allowsSkip = true), souls, selection)

private fun chosenTwo() = picker(elevenSouls, listOf(elevenSouls[0].id, elevenSouls[1].id))

@PreviewTest
@PreviewFrench
@PreviewLargeFontTall
@Preview(showBackground = true, heightDp = 720)
@Composable
fun SoulChooser_Picker() = ChooserFrame(chosenTwo())

@PreviewTest
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SoulChooser_PickerDark() = ChooserFrame(chosenTwo())

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun SoulChooser_PickerEmpty() = ChooserFrame(picker(elevenSouls))

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun SoulChooser_Search() {
    val searching = picker(searchSouls, listOf(searchSouls[0].id, searchSouls.last().id))
    searching.openSearch()
    searching.updateQuery("ma")
    ChooserFrame(searching, title = null)
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun SoulChooser_SearchEmpty() {
    val state = chosenTwo()
    state.openSearch()
    ChooserFrame(state, title = null)
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun SoulChooser_NoResult() {
    val state = picker(searchSouls)
    state.openSearch()
    state.updateQuery("Mathilde")
    ChooserFrame(state, title = null)
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun SoulChooser_Browse() = ChooserFrame(SoulChooserState(SoulChooserMode.Browse, elevenSouls), title = "People")

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun SoulChooserSheet_Full() = ChooserFrame(chosenTwo())

/** Edit pebble's half sheet: the stock handle over the same content, at about half a phone's height. */
@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(showBackground = true, heightDp = 400)
@Composable
fun SoulChooserSheet_Partial() {
    PebblesTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.CenterHorizontally)) { BottomSheetDefaults.DragHandle() }
                SoulChooserSheetContent(
                    state = chosenTwo(),
                    title = "Dinner at Maman's",
                    isLoaded = true,
                    loadFailed = false,
                    hapticsOn = true,
                    onBack = {},
                    onDone = {},
                    onCreate = {},
                    onRetry = {},
                    onHaptic = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
