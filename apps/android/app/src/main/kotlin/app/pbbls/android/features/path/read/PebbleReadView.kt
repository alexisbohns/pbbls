package app.pbbls.android.features.path.read

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.Color
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
 *
 * The whole page tints to the pebble's emotion palette (#605) — background plus
 * every text/tile/soul color resolves through [pebblePageColors]. On a palette
 * cache miss ([palette] null) the page falls back to the system/accent chrome.
 */
@Composable
fun PebbleReadView(
    detail: PebbleDetail,
    palette: EmotionPalette?,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val pageColors = palette?.let { pebblePageColors(it, isSystemInDarkTheme()) }
    Column(
        modifier
            .fillMaxSize()
            .background(pageColors?.background ?: system.background)
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
            snapStoragePath = detail.sortedSnaps.firstOrNull()?.storagePath,
        )
        PebbleReadTitle(
            name = detail.name,
            happenedAt = detail.happenedAt,
            nameColor = pageColors?.title,
            dateColor = pageColors?.date,
        )
        PebbleReadMeta(detail = detail, pageColors = pageColors)
        val desc = detail.description
        if (!desc.isNullOrEmpty()) {
            PebblesText(
                desc,
                style = PebblesTypography.body,
                color = pageColors?.description ?: system.foreground,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (detail.souls.isNotEmpty()) {
            PebbleReadSouls(
                souls = detail.souls,
                glyphColor = pageColors?.soulGlyph,
                nameColor = pageColors?.soulName,
            )
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
private fun PebbleReadMeta(
    detail: PebbleDetail,
    pageColors: PebblePageColors?,
) {
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
            backgroundColor = pageColors?.tileBackground,
            iconTint = pageColors?.tileIcon,
            labelColor = pageColors?.tileLabel,
        )
        if (domainNames.isEmpty()) {
            SurfaceTile(
                iconPainter = painterResource(R.drawable.ic_pebble_domain),
                label = stringResource(R.string.pebble_detail_no_domain),
                modifier = Modifier.weight(1f),
                muted = true,
                backgroundColor = pageColors?.tileBackground,
            )
        } else {
            SurfaceTile(
                iconPainter = painterResource(R.drawable.ic_pebble_domain),
                label = domainNames.joinToString(", "),
                modifier = Modifier.weight(1f),
                backgroundColor = pageColors?.tileBackground,
                iconTint = pageColors?.tileIcon,
                labelColor = pageColors?.tileLabel,
            )
        }
        if (detail.collections.isNotEmpty()) {
            SurfaceTile(
                iconPainter = painterResource(R.drawable.ic_pebble_collection),
                label = detail.collections.joinToString(", ") { it.name },
                modifier = Modifier.weight(1f),
                backgroundColor = pageColors?.tileBackground,
                iconTint = pageColors?.tileIcon,
                labelColor = pageColors?.tileLabel,
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
private fun PebbleReadSouls(
    souls: List<SoulWithGlyph>,
    glyphColor: Color?,
    nameColor: Color?,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        souls.chunked(3).forEach { rowSouls ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowSouls.forEach { soul ->
                    DetailSoulCell(
                        soul = soul,
                        modifier = Modifier.weight(1f),
                        glyphColor = glyphColor,
                        nameColor = nameColor,
                    )
                }
                repeat(3 - rowSouls.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One soul cell — ports iOS `SoulItem(case: .default)`: the soul's glyph above
 * its name in the hand font, no pebble count. [glyphColor] / [nameColor] tint to
 * the emotion palette on the read page (#605); null keeps `system.secondary`.
 */
@Composable
private fun DetailSoulCell(
    soul: SoulWithGlyph,
    modifier: Modifier = Modifier,
    glyphColor: Color? = null,
    nameColor: Color? = null,
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
            strokeColor = glyphColor ?: system.secondary,
            modifier = Modifier.size(72.dp),
        )
        PebblesText(
            soul.name,
            style = PebblesTypography.bodyLeadHand,
            color = nameColor ?: system.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
