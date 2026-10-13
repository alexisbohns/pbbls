package app.pbbls.android.features.path.read

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SurfaceTile
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.MomentPebble
import app.pbbls.android.core.model.SoulRef
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.core.ui.render.GlyphImage

/**
 * Pure read body of the moment read page — ports iOS `PebbleReadView.swift`.
 * Takes a [MomentDetail] plus its [palette] so it previews with no services:
 * banner, title, meta tiles, optional description, and a souls grid, stacked in
 * a scrolling column. The meta tiles and souls are the pebble's, so a moment
 * without your pebble shows only the banner, title and description. The souls grid is chunked [Row]s, never a lazy grid — a
 * lazy grid inside a `verticalScroll` [Column] throws "infinite height".
 *
 * The read page uses theme roles; only the pebble visual ([PebbleReadBanner])
 * carries the emotion [palette] (#940, maintainer decision, superseding #605's
 * page tint). It draws no background of its own, so it sits on whatever hosts
 * it: the sheet's container on a phone, `surface` in the pane.
 */
@Composable
fun PebbleReadView(
    detail: MomentDetail,
    palette: EmotionPalette?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val pebble = detail.pebble
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Inside the scroll, so the page scrolls under the gesture bar and
            // its last section still ends clear of it: in the sheet, which pads
            // only the status bar, and in the pane, where nothing pads (#940).
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PebbleReadBanner(
            glyph = pebble?.glyph,
            valence = pebble?.valence ?: Valence.NEUTRAL_MEDIUM,
            palette = palette,
            modifier = Modifier.fillMaxWidth(),
            snapStoragePath = detail.sortedSnaps.firstOrNull()?.storagePath,
        )
        PebbleReadTitle(
            name = detail.name,
            happenedAt = detail.happenedAt,
        )
        if (pebble != null) PebbleReadMeta(pebble = pebble)
        val desc = detail.description
        if (!desc.isNullOrEmpty()) {
            Text(
                desc,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (pebble != null && pebble.souls.isNotEmpty()) {
            PebbleReadSouls(souls = pebble.souls)
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
private fun PebbleReadMeta(pebble: MomentPebble) {
    val spacing = PebblesTheme.spacing
    val emotionLabel = ReferenceStrings.referenceName(ReferenceType.EMOTION, pebble.emotion.slug, pebble.emotion.name)
    val domainNames = mutableListOf<String>()
    for (domain in pebble.domains) {
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
        if (pebble.collections.isNotEmpty()) {
            SurfaceTile(
                iconPainter = painterResource(R.drawable.ic_pebble_collection),
                label = pebble.collections.joinToString(", ") { it.name },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 3-column souls grid via chunked [Row]s (never `LazyVerticalGrid` inside a
 * `verticalScroll`). The trailing row is padded with weighted [Spacer]s so cells
 * keep a stable third-of-width regardless of count.
 *
 * The view projects souls as `{id, name}`; each glyph comes from the ambient
 * souls cache, and a soul the cache does not know yet shows an empty glyph
 * (A-15).
 */
@Composable
private fun PebbleReadSouls(souls: List<SoulRef>) {
    val cached = LocalReferenceDataService.current.souls.associateBy { it.id }
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
                        name = soul.name,
                        glyph = cached[soul.id]?.glyph,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - rowSouls.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One soul cell — ports iOS `SoulItem(case: .default)`: the soul's glyph above
 * its name in the hand font, no pebble count, both in `onSurfaceVariant`.
 */
@Composable
private fun DetailSoulCell(
    name: String,
    glyph: Glyph?,
    modifier: Modifier = Modifier,
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlyphImage(
            strokes = glyph?.strokes.orEmpty(),
            viewBox = glyph?.viewBox ?: "0 0 200 200",
            strokeColor = onSurfaceVariant,
            modifier = Modifier.size(72.dp),
        )
        Text(
            name,
            style = PebblesTheme.hand.bodyLeadHand,
            color = onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
