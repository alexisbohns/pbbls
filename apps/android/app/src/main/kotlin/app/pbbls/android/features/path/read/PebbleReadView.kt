package app.pbbls.android.features.path.read

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.PebbleDetail
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.render.GlyphImage
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.theme.ReferenceStrings
import app.pbbls.android.theme.ReferenceType
import app.pbbls.android.theme.SurfaceTile

/**
 * Pure read body of the pebble detail sheet — ports iOS `PebbleReadView.swift`.
 * Takes a [PebbleDetail] plus its [palette] so it previews with no services:
 * banner, title, meta tiles, optional description, and a souls grid, stacked in
 * a scrolling column. The souls grid is chunked [Row]s, never a lazy grid — a
 * lazy grid inside a `verticalScroll` [Column] throws "infinite height".
 */
@Composable
fun PebbleReadView(
    detail: PebbleDetail,
    palette: EmotionPalette?,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PebbleReadBanner(
            renderSvg = detail.renderSvg,
            valence = Valence.fromOrDefault(detail.positiveness, detail.intensity),
            palette = palette,
            modifier = Modifier.fillMaxWidth(),
        )
        PebbleReadTitle(name = detail.name, happenedAt = detail.happenedAt)
        PebbleReadMeta(detail = detail)
        val desc = detail.description
        if (!desc.isNullOrEmpty()) {
            PebblesText(
                desc,
                style = PebblesTypography.body,
                color = system.foreground,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (detail.souls.isNotEmpty()) {
            PebbleReadSouls(souls = detail.souls)
        }
    }
}

/**
 * Metadata tile row — the iOS `metadataRow`. Emotion always shows; domain always
 * shows (a muted "No domain" placeholder when empty, iOS parity); collection
 * shows only when present. Reference names resolve slug -> localized string
 * (never the DB name directly, the D9 rule). Names are pre-resolved in a plain
 * loop so the `@Composable` [ReferenceStrings.referenceName] is never called
 * inside a non-inline `joinToString` transform lambda.
 */
@Composable
private fun PebbleReadMeta(detail: PebbleDetail) {
    val spacing = PebblesTheme.spacing
    val emotionLabel = ReferenceStrings.referenceName(ReferenceType.EMOTION, detail.emotion.slug, detail.emotion.name)
    val domainNames = mutableListOf<String>()
    for (domain in detail.domains) {
        domainNames.add(ReferenceStrings.referenceName(ReferenceType.DOMAIN, domain.slug, domain.name))
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        SurfaceTile(
            iconPainter = painterResource(R.drawable.ic_pebble_emotion),
            label = emotionLabel,
            modifier = Modifier.weight(1f),
        )
        if (domainNames.isEmpty()) {
            SurfaceTile(
                iconPainter = painterResource(R.drawable.ic_pebble_domain),
                label = stringResource(R.string.pebble_detail_no_domain),
                modifier = Modifier.weight(1f),
                muted = true,
            )
        } else {
            SurfaceTile(
                iconPainter = painterResource(R.drawable.ic_pebble_domain),
                label = domainNames.joinToString(", "),
                modifier = Modifier.weight(1f),
            )
        }
        if (detail.collections.isNotEmpty()) {
            SurfaceTile(
                iconPainter = painterResource(R.drawable.ic_pebble_collection),
                label = detail.collections.joinToString(", ") { it.name },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 3-column souls grid via chunked [Row]s (never `LazyVerticalGrid` inside a
 * `verticalScroll`). The trailing row is padded with weighted [Spacer]s so cells
 * keep a stable third-of-width regardless of count.
 */
@Composable
private fun PebbleReadSouls(souls: List<SoulWithGlyph>) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        souls.chunked(3).forEach { rowSouls ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowSouls.forEach { soul -> DetailSoulCell(soul = soul, modifier = Modifier.weight(1f)) }
                repeat(3 - rowSouls.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One soul cell — ports iOS `SoulItem(case: .default)`: the soul's glyph in
 * `system.secondary` above its name in the hand font, no pebble count.
 */
@Composable
private fun DetailSoulCell(
    soul: SoulWithGlyph,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlyphImage(
            strokes = soul.glyph.strokes,
            viewBox = soul.glyph.viewBox,
            strokeColor = system.secondary,
            modifier = Modifier.size(72.dp),
        )
        PebblesText(
            soul.name,
            style = PebblesTypography.bodyLeadHand,
            color = system.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
