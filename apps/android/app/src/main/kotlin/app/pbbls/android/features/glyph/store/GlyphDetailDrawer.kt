package app.pbbls.android.features.glyph.store

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.data.GlyphMarketServicing
import app.pbbls.android.core.data.glyphMarketErrorMessage
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SurfaceTile
import app.pbbls.android.core.model.BuyGlyphResult
import app.pbbls.android.core.model.GlyphGridItem
import app.pbbls.android.core.ui.GlyphBanner
import app.pbbls.android.core.ui.GlyphBannerSubtitle
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TAG = "glyph-detail"

/**
 * The swap/owned drawer — ports iOS `GlyphDetailDrawer` as this screen's
 * single `ModalBottomSheet` level (D5): banner, stat tiles, dotted rule with
 * the price/seal badge, me-vs-creator row, then [SlideToConfirm] or the
 * acquired label. A successful swap does NOT dismiss — the drawer morphs in
 * place to its Owned state and [onSwapped] lets the host update caches +
 * karma. Buy errors map through `glyphMarketErrorMessage` (M43 D4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlyphDetailDrawer(
    item: GlyphGridItem,
    balance: Int,
    market: GlyphMarketServicing,
    onRecorded: (BuyGlyphResult) -> Unit,
    onSwapped: (BuyGlyphResult) -> Unit = {},
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        GlyphSwapPanel(item = item, balance = balance, market = market, onRecorded = onRecorded, onSwapped = onSwapped)
    }
}

/**
 * The stateful swap body without the sheet wrapper — the tabbed picker embeds
 * this as a content swap (D5: never stack ModalBottomSheets). Owns the buy
 * flow: on success the panel morphs to Owned in place and [onSwapped] fires.
 */
@Composable
internal fun GlyphSwapPanel(
    item: GlyphGridItem,
    balance: Int,
    market: GlyphMarketServicing,
    /**
     * Runs inside the uncancellable section — see [GlyphPurchase]. Record only:
     * the new balance, caches that now disagree with the server. Never
     * navigate, dismiss or select from here.
     */
    onRecorded: (BuyGlyphResult) -> Unit,
    /**
     * Runs after, in the gesture's own scope, and is therefore skipped when the
     * user has walked away — which is exactly right for "select this glyph and
     * hand control back to the caller".
     */
    onSwapped: (BuyGlyphResult) -> Unit = {},
) {
    var isOwned by remember(item.id) { mutableStateOf(item.owned) }
    var acquiredAt by remember(item.id) { mutableStateOf(item.acquiredAt) }
    var currentBalance by remember(item.id) { mutableStateOf(balance) }
    var isBuying by remember(item.id) { mutableStateOf(false) }
    var errorRes by remember(item.id) { mutableStateOf<Int?>(null) }

    GlyphDetailDrawerContent(
        item = item,
        isOwned = isOwned,
        acquiredAt = acquiredAt,
        currentBalance = currentBalance,
        isBuying = isBuying,
        errorRes = errorRes,
        onConfirm = {
            isBuying = true
            errorRes = null
            val result =
                GlyphPurchase.buyAndRecord(
                    market = market,
                    glyphId = item.glyph.id,
                    onRecorded = { landed ->
                        currentBalance = landed.balance
                        // iOS stamps the client's now, not a server timestamp.
                        acquiredAt = OffsetDateTime.now()
                        isOwned = true
                        onRecorded(landed)
                    },
                    onError = { e ->
                        Log.e(TAG, "glyph swap failed", e)
                        errorRes = glyphMarketErrorMessage(e.toDataError())
                    },
                )
            isBuying = false
            // Outside the uncancellable section on purpose: a host that reacts
            // by selecting the glyph into a form must not do so once the user
            // has dismissed the sheet.
            result?.let(onSwapped)
            result != null
        },
    )
}

/** Pure drawer body — split from the sheet so screenshots can drive both states. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GlyphDetailDrawerContent(
    item: GlyphGridItem,
    isOwned: Boolean,
    acquiredAt: OffsetDateTime?,
    currentBalance: Int,
    isBuying: Boolean,
    errorRes: Int?,
    onConfirm: suspend () -> Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val canAfford = currentBalance >= item.price
    val locale = Locale.getDefault()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(PebblesTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(if (isOwned) R.string.glyph_drawer_owned else R.string.glyph_drawer_swap),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = colors.onSurface,
        )

        GlyphBanner(
            title = item.glyph.name ?: stringResource(R.string.create_glyph_untitled),
            strokes = item.glyph.strokes,
            viewBox = item.glyph.viewBox,
            subtitle = GlyphBannerSubtitle.Byline("@community"),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm)) {
            SurfaceTile(
                iconPainter = painterResource(R.drawable.ic_calendar),
                label =
                    item.createdAt?.format(DateTimeFormatter.ofPattern("MMM yyyy", locale))
                        ?: "—",
                modifier = Modifier.weight(1f),
            )
            SurfaceTile(
                iconPainter = painterResource(R.drawable.ic_alternating_current),
                label = stringResource(R.string.glyph_drawer_soon),
                muted = true,
                modifier = Modifier.weight(1f),
            )
            SurfaceTile(
                iconPainter = painterResource(R.drawable.ic_person_pair),
                label = stringResource(R.string.glyph_drawer_soon),
                muted = true,
                modifier = Modifier.weight(1f),
            )
        }

        DottedRuleWithBadge(isOwned = isOwned, price = item.price)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.glyph_drawer_me),
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = colors.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sparkle),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = currentBalance.toString(),
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                        color = colors.onSurface,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_alternating_current),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.glyph_drawer_creator),
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = "@community",
                    style = PebblesTheme.hand.bodyLeadHand,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        if (isOwned) {
            Text(
                text =
                    acquiredAt?.let {
                        stringResource(
                            R.string.glyph_drawer_acquired,
                            it.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale)),
                        )
                    } ?: stringResource(R.string.glyph_drawer_owned),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        } else {
            SlideToConfirm(
                cost = item.price,
                enabled = canAfford && !isBuying,
                onConfirm = onConfirm,
            )
            if (!canAfford) {
                Text(
                    text = stringResource(R.string.glyph_error_insufficient_karma),
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = colors.error,
                )
            }
        }

        errorRes?.let { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = colors.error,
            )
        }
    }
}

/**
 * The dotted rule (4dp round dots in `primaryContainer`) with the centered
 * price/seal badge masking it on a `surfaceContainerLow` chip — the
 * `ModalBottomSheet` default container, so the chip reads as a gap in the
 * rule — iOS `dividerWithBadge`.
 */
@Composable
private fun DottedRuleWithBadge(
    isOwned: Boolean,
    price: Int,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
            drawLine(
                color = colors.primaryContainer,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = size.height,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 8.dp.toPx())),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .background(colors.surfaceContainerLow)
                    .padding(horizontal = PebblesTheme.spacing.sm),
        ) {
            if (isOwned) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_sparkle),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = price.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary,
                )
            }
        }
    }
}
