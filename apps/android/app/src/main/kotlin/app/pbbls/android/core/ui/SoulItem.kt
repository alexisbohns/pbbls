package app.pbbls.android.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.SoulWithGlyph

/**
 * Which visual state a soul cell renders — the iOS `SoulItem.Case` contract
 * (spec table: `docs/superpowers/specs/2026-05-17-issue-459-glyph-souls-consistency-design.md` §3).
 */
enum class SoulItemCase {
    SELECTED, // picker: in the current selection — primary glyph + name
    UNSELECTED, // picker: a selection exists elsewhere — 38% glyph + onSurfaceVariant name
    DEFAULT, // lists: no selection semantics — onSurfaceVariant glyph + name
    CREATE, // trailing "New soul" affordance — plus-in-dashed-frame
}

/**
 * Single soul cell shared by the souls list and the soul picker — ports iOS
 * `Features/Shared/SoulItem.swift`: [GlyphView] on top, hand-face name
 * (selection carried by color, never weight — issue #515), optional
 * fossil-shell + pebble count line. [onLongPress] backs the list's delete
 * affordance (D7 — the caller anchors its own `DropdownMenu`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoulItem(
    case: SoulItemCase,
    soul: SoulWithGlyph?,
    count: Int?,
    modifier: Modifier = Modifier,
    side: Dp = 96.dp,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val glyphCase =
        when (case) {
            SoulItemCase.SELECTED -> GlyphViewCase.SELECTED
            SoulItemCase.UNSELECTED -> GlyphViewCase.UNSELECTED
            SoulItemCase.DEFAULT -> GlyphViewCase.DEFAULT
            SoulItemCase.CREATE -> GlyphViewCase.CREATE
        }
    val nameColor = if (case == SoulItemCase.SELECTED) colors.primary else colors.onSurfaceVariant
    // The count icon is the same pale rose in light and dark (iOS parity: it
    // was AccentSecondary in light, AccentShaded in dark). `primaryFixed` is the
    // one role that holds that tone across both, so no dark-theme branch (#853).
    val fossilColor = colors.primaryFixed
    val displayName =
        if (case == SoulItemCase.CREATE) stringResource(R.string.create_soul_add) else soul?.name.orEmpty()

    Column(
        modifier =
            modifier
                .width(side)
                .clip(MaterialTheme.shapes.medium)
                .let { base ->
                    if (onTap != null || onLongPress != null) {
                        base.combinedClickable(onClick = onTap ?: {}, onLongClick = onLongPress)
                    } else {
                        base
                    }
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm),
    ) {
        GlyphView(case = glyphCase, strokes = soul?.glyph?.strokes, side = side)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs),
        ) {
            Text(
                text = displayName,
                style = PebblesTheme.hand.bodyLeadHand,
                color = nameColor,
                maxLines = 1,
            )
            if (case != SoulItemCase.CREATE && count != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_fossil_shell),
                        contentDescription = null,
                        tint = fossilColor,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
