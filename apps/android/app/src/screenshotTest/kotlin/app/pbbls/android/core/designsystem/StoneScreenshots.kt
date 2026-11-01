package app.pbbls.android.core.designsystem

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.stone.SplitStone
import app.pbbls.android.core.designsystem.stone.SquirclePentagonShape
import app.pbbls.android.core.designsystem.stone.StoneGeometry
import app.pbbls.android.core.designsystem.stone.StoneOptions
import app.pbbls.android.core.designsystem.stone.StoneShape
import com.android.tools.screenshot.PreviewTest

/**
 * The stones of Home and You (spec D5), Paper and Night: the quick actions
 * row on the iOS seed, the first-five stepper, the counters rock and its
 * stacked large-font form (D18), a 48 × 46 chip and the profile pentagon.
 * Sizes and seeds are the approved mock's (`type/five/c3.html`), so these
 * renders are the review surface for the geometry port.
 */
@Composable
private fun StoneGallery() {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val spacing = PebblesTheme.spacing
    Column(
        modifier =
            Modifier
                .background(colors.surface)
                .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        val actions =
            listOf(
                PbblsIcon.CAMERA to "Camera moment",
                PbblsIcon.PEBBLE_FLAT to "Quick moment",
                PbblsIcon.PLUS_CIRCLE to "New pebble",
            )
        SplitStone(
            n = 3,
            seed = StoneGeometry.QUICK_ACTIONS_SEED,
            fills = List(3) { colors.primaryContainer },
            modifier = Modifier.width(359.dp),
            minHeight = 100.dp,
        ) { k ->
            Column(
                modifier =
                    Modifier
                        .pieceClip(k)
                        .clickable(onClick = {})
                        .padding(spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterVertically),
            ) {
                PbblsIconImage(actions[k].first, PbblsIconSize.TILE, contentDescription = null, tint = colors.onPrimaryContainer)
                Text(actions[k].second, style = type.labelLarge, color = colors.onPrimaryContainer, textAlign = TextAlign.Center)
            }
        }
        SplitStone(
            n = 5,
            seed = "first-five",
            fills = List(5) { if (it < 2) colors.primary else colors.surfaceContainerHighest },
            modifier = Modifier.width(324.dp),
            options = StoneOptions(corner = 3f, gutter = 5f, fillet = 2f, lean = 0.9f),
            minHeight = 6.dp,
        ) {}
        val counters = listOf("112" to "days", "340" to "pebbles", "87" to "karma")
        for (vertical in listOf(false, true)) {
            SplitStone(
                n = 3,
                seed = "days|pebbles|karma",
                fills = List(3) { colors.surfaceContainer },
                modifier = Modifier.width(358.dp),
                minHeight = if (vertical) 56.dp else 88.dp,
                vertical = vertical,
            ) { k ->
                Column(
                    modifier = Modifier.padding(spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(counters[k].first, style = type.headlineSmall, color = colors.onSurface)
                    Text(counters[k].second, style = type.labelMedium, color = colors.onSurfaceVariant)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp, 46.dp)
                        .background(colors.surfaceContainer, StoneShape("chip-0", 16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                PbblsIconImage(PbblsIcon.PEBBLE_FLAT, PbblsIconSize.ROW, contentDescription = null, tint = colors.onSurface)
            }
            Box(
                modifier =
                    Modifier
                        .size(128.dp, 122.dp)
                        .background(colors.primaryContainer, SquirclePentagonShape),
                contentAlignment = Alignment.Center,
            ) {
                PbblsIconImage(
                    PbblsIcon.PEBBLE_DOTTED_GLYPH,
                    PbblsIconSize.HERO,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                )
            }
            Box(Modifier.size(48.dp).background(colors.surfaceContainerHigh, StoneShape("settings", 16.dp)))
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun StoneGalleryPaper() {
    PebblesTheme { StoneGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun StoneGalleryNight() {
    PebblesTheme { StoneGallery() }
}
