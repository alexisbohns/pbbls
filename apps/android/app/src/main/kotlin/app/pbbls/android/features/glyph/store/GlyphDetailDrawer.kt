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
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import app.pbbls.android.features.glyph.models.BuyGlyphResult
import app.pbbls.android.features.glyph.models.GlyphGridItem
import app.pbbls.android.features.glyph.services.LocalGlyphMarketService
import app.pbbls.android.features.glyph.services.glyphMarketErrorMessage
import app.pbbls.android.features.glyph.views.GlyphBanner
import app.pbbls.android.features.glyph.views.GlyphBannerSubtitle
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.theme.SurfaceTile
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
    onSwapped: (BuyGlyphResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val market = LocalGlyphMarketService.current

    var isOwned by remember(item.id) { mutableStateOf(item.owned) }
    var acquiredAt by remember(item.id) { mutableStateOf(item.acquiredAt) }
    var currentBalance by remember(item.id) { mutableStateOf(balance) }
    var isBuying by remember(item.id) { mutableStateOf(false) }
    var errorRes by remember(item.id) { mutableStateOf<Int?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
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
                try {
                    val result = market.buy(item.glyph.id)
                    currentBalance = result.balance
                    // iOS stamps the client's now, not a server timestamp.
                    acquiredAt = OffsetDateTime.now()
                    isOwned = true
                    onSwapped(result)
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "glyph swap failed", e)
                    errorRes = glyphMarketErrorMessage(e.message)
                    false
                } finally {
                    isBuying = false
                }
            },
        )
    }
}

/** Pure drawer body — split from the sheet so screenshots can drive both states. */
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
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
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
        PebblesText(
            text = stringResource(if (isOwned) R.string.glyph_drawer_owned else R.string.glyph_drawer_swap),
            style = PebblesTypography.headlineEmphasized,
            color = system.foreground,
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
                PebblesText(
                    text = stringResource(R.string.glyph_drawer_me),
                    style = PebblesTypography.captionEmphasized,
                    color = system.secondary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sparkle),
                        contentDescription = null,
                        tint = accent.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    PebblesText(
                        text = currentBalance.toString(),
                        style = PebblesTypography.subheadEmphasized,
                        color = system.foreground,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_alternating_current),
                contentDescription = null,
                tint = system.muted,
                modifier = Modifier.size(16.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PebblesText(
                    text = stringResource(R.string.glyph_drawer_creator),
                    style = PebblesTypography.captionEmphasized,
                    color = system.secondary,
                )
                PebblesText(
                    text = "@community",
                    style = PebblesTypography.bodyLeadHand,
                    color = system.muted,
                )
            }
        }

        if (isOwned) {
            PebblesText(
                text =
                    acquiredAt?.let {
                        stringResource(
                            R.string.glyph_drawer_acquired,
                            it.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale)),
                        )
                    } ?: stringResource(R.string.glyph_drawer_owned),
                style = PebblesTypography.subhead,
                color = system.secondary,
            )
        } else {
            SlideToConfirm(
                cost = item.price,
                enabled = canAfford && !isBuying,
                onConfirm = onConfirm,
            )
            if (!canAfford) {
                PebblesText(
                    text = stringResource(R.string.glyph_error_insufficient_karma),
                    style = PebblesTypography.captionEmphasized,
                    color = PebblesDestructive,
                )
            }
        }

        errorRes?.let { res ->
            PebblesText(
                text = stringResource(res),
                style = PebblesTypography.captionEmphasized,
                color = PebblesDestructive,
            )
        }
    }
}

/**
 * The dotted rule (4dp round dots in accent.secondary) with the centered
 * price/seal badge masking it on a system-background chip — iOS
 * `dividerWithBadge`.
 */
@Composable
private fun DottedRuleWithBadge(
    isOwned: Boolean,
    price: Int,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
            drawLine(
                color = accent.secondary,
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
                    .background(system.background)
                    .padding(horizontal = PebblesTheme.spacing.sm),
        ) {
            if (isOwned) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = accent.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_sparkle),
                    contentDescription = null,
                    tint = accent.primary,
                    modifier = Modifier.size(14.dp),
                )
                PebblesText(
                    text = price.toString(),
                    style = PebblesTypography.headline,
                    color = accent.primary,
                )
            }
        }
    }
}
