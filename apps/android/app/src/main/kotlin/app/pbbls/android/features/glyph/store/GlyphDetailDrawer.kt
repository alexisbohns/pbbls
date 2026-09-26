package app.pbbls.android.features.glyph.store

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * The glyph detail entry (#940): [GlyphSwapPanel] for the item in the key — the
 * swap/owned drawer that ports iOS `GlyphDetailDrawer`. On a phone the
 * bottom-sheet scene hosts it, on a large screen the list-detail scene puts it
 * beside the store, so it draws no sheet of its own. A successful swap does
 * NOT dismiss: the panel morphs in place to its Owned state (M43 D4).
 *
 * The background is `surfaceContainerLow`, the sheet's default container
 * colour, so the page is identical in both hosts and the price badge's chip (which masks the dotted
 * rule in that colour) has no seam in the pane. Nothing pads a pane, so the
 * page clears the status and gesture bars itself; in the sheet the top is
 * already consumed by the sheet's own insets and only the gesture bar is left.
 */
@Composable
fun GlyphDetailScreen(
    item: GlyphGridItem,
    modifier: Modifier = Modifier,
    viewModel: GlyphDetailViewModel = hiltViewModel(),
) {
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    GlyphDetailSurface(modifier) {
        GlyphSwapPanel(
            // Owned if this entry recorded a buy: a rebuilt panel must not offer it again.
            item = viewModel.shown(item),
            balance = balance,
            market = viewModel.market,
            onRecorded = { result -> viewModel.onRecorded(item, result) },
        )
    }
}

/** [GlyphDetailScreen]'s page, shared with the list-detail screenshots. */
@Composable
internal fun GlyphDetailSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical)),
    ) {
        content()
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
