package app.pbbls.android.core.designsystem

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.PreviewLargeFont
import com.android.tools.screenshot.PreviewTest

// The specimen in three runs: Home's heads (dateline to the figure's word), the rows, counters
// and wraps, then You's ranks and the hand.
private val HEADS = 0 until 6
private val ROWS = 6 until 15
private val YOU = 15 until 27

/**
 * The page kit of Home and You (spec 2026-10-31 D3, D19): every rank of [PageTypography] (and
 * the hand's `profileName`) as a specimen line, then the parts: two cards, the box with a
 * section head and its figure, and two chip rows. Paper, Night and 2 × font. Sizes and copy are
 * the approved mock's (`type/five/c3.html`).
 *
 * The specimen runs in three pieces and the parts in two (the cards and the box, then the rows)
 * so that none outgrows the renderer's viewport at 2 × font, which clips rather than grows.
 */
@Composable
private fun TypeSpecimen(run: IntRange) {
    val type = PebblesTheme.page
    val colors = MaterialTheme.colorScheme
    val ranks: List<Triple<String, TextStyle, Color>> =
        listOf(
            Triple("Friday 31 October 2026", type.dateline, colors.primary),
            Triple("Good evening, 2026", type.greeting, colors.onSurface),
            Triple("Three moments kept this week", type.lead, colors.onSurfaceVariant),
            Triple("Your first five", type.sectionHead, colors.onSurface),
            Triple("2 of 5 · 12", type.sectionFigure, colors.primary),
            Triple("of · earned", type.figureWord, colors.onSurfaceVariant),
            Triple("Drop your first pebble", type.rowTitle, colors.onSurface),
            Triple("Carve a glyph", type.rowTitleDone, colors.onSurfaceVariant),
            Triple("A moment, a feeling, the people in it", type.rowLine, colors.onSurfaceVariant),
            Triple("Camera moment", type.actionLabel, colors.onSurface),
            Triple("112 340 87", type.counterFigure, colors.onSurface),
            Triple("days", type.counterLabel, colors.onSurfaceVariant),
            Triple("23", type.wrapFigure, colors.onSurface),
            Triple("October", type.wrapMonth, colors.onSurface),
            Triple("2026", type.wrapYear, colors.onSurfaceVariant),
            Triple("First pebble", type.badgeLabel, colors.onSurfaceVariant),
            Triple("Member since March 2026", type.since, colors.onSurfaceVariant),
            Triple("Ripples Level 3", type.rippleLabel, colors.onSurface),
            Triple("Lab notes · 31 October", type.postKicker, colors.onSurfaceVariant),
            Triple("Home, and a new You", type.postTitle, colors.onSurface),
            Triple("A first tab for today, and your page rebuilt.", type.postLine, colors.onSurfaceVariant),
            Triple("31 Oct", type.logDate, colors.onSurfaceVariant),
            Triple("Quick actions on Home", type.logTitle, colors.onSurface),
            Triple("Home", type.logLine, colors.onSurfaceVariant),
            Triple("Join the community", type.calloutTitle, colors.onSurface),
            Triple("Tell us what to build next.", type.calloutLine, colors.onSurfaceVariant),
            Triple("Alexandrine", PebblesTheme.hand.profileName, colors.onSurface),
        )
    Column(
        modifier =
            Modifier
                .width(390.dp)
                .background(colors.surface)
                .padding(PebblesTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm),
    ) {
        ranks.slice(run).forEach { (sample, style, color) -> Text(sample, style = style, color = color) }
    }
}

@Composable
private fun PartsFrame(content: @Composable () -> Unit) {
    val spacing = PebblesTheme.spacing
    Column(
        modifier =
            Modifier
                .width(390.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = spacing.lg, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        content()
    }
}

@Composable
private fun CardsAndBox() {
    val type = PebblesTheme.page
    val colors = MaterialTheme.colorScheme
    val spacing = PebblesTheme.spacing
    PartsFrame {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            PebblesCard(Modifier.weight(1f)) {
                Column(Modifier.padding(start = 18.dp, top = 18.dp, end = 16.dp, bottom = 18.dp)) {
                    Text("23", style = type.wrapFigure, color = colors.onSurface)
                    Text("October", style = type.wrapMonth, color = colors.onSurface)
                    Text("2026", style = type.wrapYear, color = colors.onSurfaceVariant)
                }
            }
            PebblesCard(Modifier.weight(1f), onClick = {}, onClickLabel = "Open") {
                Column(Modifier.padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 18.dp)) {
                    Text("Lab notes · 31 October", style = type.postKicker, color = colors.onSurfaceVariant)
                    Text("Home, and a new You", style = type.postTitle, color = colors.onSurface)
                    Text("A first tab for today.", style = type.postLine, color = colors.onSurfaceVariant)
                }
            }
        }
        Column(
            modifier =
                Modifier
                    .background(colors.surfaceContainer, PageShapes.box)
                    .padding(start = 18.dp, top = spacing.xl, end = spacing.lg, bottom = 10.dp),
        ) {
            SectionHead("Your first five", figure = figureOf(2, 5, "of"))
            Column(Modifier.padding(top = spacing.lg)) {
                Text("Drop your first pebble", style = type.rowTitleDone, color = colors.onSurfaceVariant)
                Text("Done", style = type.rowLine, color = colors.onSurfaceVariant)
                Text("Carve a glyph", style = type.rowTitle, color = colors.onSurface, modifier = Modifier.padding(top = 13.dp))
                Text("Draw the mark of a moment", style = type.rowLine, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Rows() {
    PartsFrame {
        Column {
            SectionHead("About Pebbles")
            ChipRow(
                icon = PbblsIcon.PEBBLE_FLAT,
                title = "What is a pebble",
                line = "A moment you kept, and how it felt",
                seed = "about-pebble",
                onClick = {},
            )
            ChipRow(
                icon = PbblsIcon.GLYPH_UNDERLINED,
                title = "Glyphs",
                line = "The marks you carve for your moments",
                seed = "about-glyphs",
                onClick = {},
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun PageKitTypeHeadsPaper() {
    PebblesTheme { TypeSpecimen(HEADS) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PageKitTypeHeadsNight() {
    PebblesTheme { TypeSpecimen(HEADS) }
}

@PreviewTest
@PreviewLargeFont
@Composable
fun PageKitTypeHeadsLargeFont() {
    PebblesTheme { TypeSpecimen(HEADS) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun PageKitTypeRowsPaper() {
    PebblesTheme { TypeSpecimen(ROWS) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PageKitTypeRowsNight() {
    PebblesTheme { TypeSpecimen(ROWS) }
}

@PreviewTest
@PreviewLargeFont
@Composable
fun PageKitTypeRowsLargeFont() {
    PebblesTheme { TypeSpecimen(ROWS) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun PageKitTypeYouPaper() {
    PebblesTheme { TypeSpecimen(YOU) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PageKitTypeYouNight() {
    PebblesTheme { TypeSpecimen(YOU) }
}

@PreviewTest
@PreviewLargeFont
@Composable
fun PageKitTypeYouLargeFont() {
    PebblesTheme { TypeSpecimen(YOU) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun PageKitCardsPaper() {
    PebblesTheme { CardsAndBox() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PageKitCardsNight() {
    PebblesTheme { CardsAndBox() }
}

@PreviewTest
@PreviewLargeFont
@Composable
fun PageKitCardsLargeFont() {
    PebblesTheme { CardsAndBox() }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun PageKitRowsPaper() {
    PebblesTheme { Rows() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PageKitRowsNight() {
    PebblesTheme { Rows() }
}

@PreviewTest
@PreviewLargeFont
@Composable
fun PageKitRowsLargeFont() {
    PebblesTheme { Rows() }
}
