package app.pbbls.android.features.profile.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.ChipRow
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.SectionHead
import app.pbbls.android.core.model.GlyphShelf

/** Under the head, to the first row (the mock's 8 px). */
private val HEAD_GAP = 8.dp

/**
 * You's Glyphs (#1383, spec D14.5 and D17): the head, then three chip rows, no box. My glyphs
 * ("The 12 marks you carved.", `draw-dotted`), Owned glyphs ("The 5 you collected from others.",
 * `pouch`) and Community ("Glyphs other pebblers share.", `petroglyph`), on stones cut from
 * `chip-g0…2`. [onOpen] gets the store's tab for the row tapped.
 *
 * The counts come from `get_profile_engagement` (#1386). While one is not served, or is zero, its
 * line reads without a number ("The marks you carved."), which is still true.
 */
@Composable
internal fun YouGlyphsSection(
    glyphsCarved: Int?,
    glyphsOwned: Int?,
    textInset: Dp,
    onOpen: (GlyphShelf) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionHead(
            title = stringResource(R.string.glyphs_title),
            modifier = Modifier.padding(horizontal = textInset),
        )
        Spacer(Modifier.height(HEAD_GAP))
        ChipRow(
            icon = PbblsIcon.DRAW_DOTTED,
            title = stringResource(R.string.you_glyphs_mine),
            line =
                glyphsCarved?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.you_glyphs_mine_line, it, it) }
                    ?: stringResource(R.string.you_glyphs_mine_line_none),
            seed = "chip-g0",
            onClick = { onOpen(GlyphShelf.MINE) },
            modifier = Modifier.padding(horizontal = textInset),
        )
        ChipRow(
            icon = PbblsIcon.POUCH,
            title = stringResource(R.string.you_glyphs_owned),
            line =
                glyphsOwned?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.you_glyphs_owned_line, it, it) }
                    ?: stringResource(R.string.you_glyphs_owned_line_none),
            seed = "chip-g1",
            onClick = { onOpen(GlyphShelf.OWNED) },
            modifier = Modifier.padding(horizontal = textInset),
        )
        ChipRow(
            icon = PbblsIcon.PETROGLYPH,
            title = stringResource(R.string.you_glyphs_community),
            line = stringResource(R.string.you_glyphs_community_line),
            seed = "chip-g2",
            onClick = { onOpen(GlyphShelf.COMMU) },
            modifier = Modifier.padding(horizontal = textInset),
        )
    }
}
