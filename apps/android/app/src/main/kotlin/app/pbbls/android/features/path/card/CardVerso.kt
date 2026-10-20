package app.pbbls.android.features.path.card

import android.icu.text.ListFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.pbbls.android.R
import app.pbbls.android.core.common.UuidHash
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineTypography
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.MomentPebble
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.core.ui.render.stone.BLANK_STONE_PALETTE
import app.pbbls.android.core.ui.render.stone.LitStone
import app.pbbls.android.core.ui.render.stone.StoneGeometry
import app.pbbls.android.core.ui.render.stone.StonePalette
import app.pbbls.android.core.ui.render.stone.StoneTones
import app.pbbls.android.core.ui.render.stone.rememberStonePressState
import app.pbbls.android.core.ui.render.stone.toStonePalette
import app.pbbls.android.features.path.card.logic.LockupInk
import app.pbbls.android.features.path.card.logic.ValenceLockups
import app.pbbls.android.features.path.spine.drawFieldPattern
import app.pbbls.android.features.path.spine.stoneKindRes
import app.pbbls.android.features.path.valence.ValenceWord

/*
 * The frame, in card units (back-token3 `.back .panel`, `.vpanel`): the mat's corner,
 * the stock panel's corner, its hairline rule's inset and corner, then the panel's
 * padding. The postmark row reaches [ROW_OUTSET_UNITS] past the padding, as the mock's
 * `.sbs` negative margin does, so four stamps keep their size on a phone.
 */
private const val MAT_CORNER_UNITS = 1.3f
private const val PANEL_CORNER_UNITS = 1f
private const val PANEL_RULE_INSET_UNITS = 1.1f
private const val PANEL_RULE_CORNER_UNITS = 0.8f
private const val PANEL_PAD_TOP_UNITS = 3.6f
private const val PANEL_PAD_SIDE_UNITS = 4f
private const val PANEL_PAD_FOOT_UNITS = 4.4f
private const val ROW_OUTSET_UNITS = 2.6f

/**
 * The clearing above and below the stone, as a share of its width: the rings and the
 * halo reach (rings + 0.78) ring steps past the silhouette (`StoneGeometry.ringGeometry`),
 * 0.25 to 0.29 of the width at these sizes, and paint past the stone's box. Without the
 * room the halo covers the emotion when the verso is the taller face.
 */
private const val CLEARING_SHARE = 0.3f
private const val EMOTION_GAP_UNITS = 2.2f
private const val HAIRLINE_GAP_UNITS = 2f

/** The mat's and the panel's keylines: the spot at 30 % and 28 % (the mock's inset shadows). */
private const val MAT_RULE_ALPHA = 0.30f
private const val PANEL_RULE_ALPHA = 0.28f

private val RULE = 1.dp
private val HAIRLINE = 24.dp

/** The night ramp's last stop: the secondary 70 % into the primary (the mock's `.night .vword.hl`). */
private const val NIGHT_RAMP_FOOT = 0.7f
private const val RAMP_MID = 0.4f

/**
 * The verso (pebble card spec §Verso): Held's frame, the emotion's mat print round a
 * stock panel, and in it the valence lockup, the emotion, the large lit stone centred
 * in the room left, and the domains as postmarks.
 *
 * The collection band is not drawn here: it runs past the frame to the shell's edges,
 * so `PebbleCard` lays it over this face's foot. [bandHeight] is its measured height
 * (zero for no band); the verso keeps the postmarks [CardLayout.POSTMARK_ROW_TO_BAND]
 * above it and lets the panel run under it.
 *
 * Give it the face's height (`fillMaxHeight` from the caller's constraints) and the
 * stone centres between the emotion and the postmarks.
 */
@Composable
internal fun CardVerso(
    pebble: MomentPebble,
    palette: EmotionPalette?,
    u: Dp,
    domains: List<Domain>,
    bandHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val night = colors.isNight
    val valence = pebble.valence
    val polarity = valence.polarity
    val stone = remember(palette) { palette?.toStonePalette() ?: BLANK_STONE_PALETTE }
    val spot = StoneTones.spot(stone, night)
    val tint = remember(spot, colors) { StoneTones.mixOklab(spot, colors.tintK, colors.stock) }
    val pattern = spot.copy(alpha = colors.patK)

    val matInset = u * CardLayout.VERSO_MAT_UNITS
    val panelInset = CardLayout.bandFoot(u)
    // With a band, the panel's foot sits under it; the postmarks sit above it.
    val panelFoot = if (bandHeight > 0.dp) panelInset + bandHeight - CardLayout.BAND_OVER_PANEL else panelInset
    val contentFoot =
        if (bandHeight > 0.dp) panelInset + bandHeight + CardLayout.POSTMARK_ROW_TO_BAND else panelInset + u * PANEL_PAD_FOOT_UNITS
    val side = panelInset + u * (PANEL_PAD_SIDE_UNITS - ROW_OUTSET_UNITS)

    Box(
        modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true }
            .drawBehind {
                drawFrame(
                    polarity = polarity,
                    matInset = matInset.toPx(),
                    panelInset = panelInset.toPx(),
                    panelFoot = panelFoot.toPx(),
                    u = u.toPx(),
                    tint = tint,
                    pattern = pattern,
                    matRule = spot.copy(alpha = MAT_RULE_ALPHA),
                    panelRule = spot.copy(alpha = PANEL_RULE_ALPHA),
                    stock = colors.stock,
                    cardRule = colors.cardRule,
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = side, end = side, top = panelInset + u * PANEL_PAD_TOP_UNITS, bottom = contentFoot),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Lockup(pebble, stone, night)
            Box(
                Modifier
                    .padding(top = u * EMOTION_GAP_UNITS, bottom = u * HAIRLINE_GAP_UNITS)
                    .size(HAIRLINE, RULE)
                    .background(colors.cardRule),
            )
            Text(
                text = ReferenceStrings.referenceName(ReferenceType.EMOTION, pebble.emotion.slug, pebble.emotion.name),
                style = SpineTypography.cardEmotion,
                color = colors.ink,
            )
            Spacer(Modifier.weight(1f))
            val kind = stringResource(stoneKindRes(valence.sizeGroup, polarity))
            val stoneWidth = versoStoneWidth(valence.sizeGroup)
            LitStone(
                valence = valence,
                palette = palette,
                glyph = pebble.glyph,
                seed = remember(pebble.id) { UuidHash.fnv1a(pebble.id) },
                press = rememberStonePressState(),
                width = stoneWidth,
                modifier =
                    Modifier
                        .padding(vertical = stoneWidth * CLEARING_SHARE)
                        .semantics { contentDescription = kind },
            )
            Spacer(Modifier.weight(1f))
            Postmarks(pebble, domains, stone, night, rowWidth = u * CardLayout.U_DIVISOR - side * 2)
        }
    }
}

/** The valence lockup, one heading: "Your pebble. Highlight of my month". */
@Composable
private fun Lockup(
    pebble: MomentPebble,
    stone: StonePalette,
    night: Boolean,
) {
    val colors = PebblesTheme.spine.colors
    val locale = LocalConfiguration.current.locales[0]
    val lockup = ValenceLockups.of(pebble.valence)
    val word = stringResource(lockup.wordRes).let { if (lockup.lowercase) it.lowercase(locale) else it }
    val span = stringResource(lockup.spanRes)
    val description = stringResource(R.string.card_verso_heading) + ". " + word + " " + span.lowercase(locale)
    val primary = MaterialTheme.colorScheme.primary
    val brush: Brush =
        remember(lockup.ink, stone, night, colors.ink, primary) {
            when (lockup.ink) {
                LockupInk.STONE_RAMP -> highlightRamp(stone, night)
                LockupInk.INK -> SolidColor(colors.ink)
                LockupInk.PRIMARY -> SolidColor(primary)
            }
        }
    Column(
        Modifier.clearAndSetSemantics {
            heading()
            contentDescription = description
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ValenceWord(valence = pebble.valence, scale = lockup.scale, brush = brush, lowercase = lockup.lowercase)
        Text(text = span, style = SpineTypography.cardLockupSpan, color = colors.ink2)
    }
}

/**
 * "Highlight" in its stone's light (spec §Verso 1): the body ramp top to bottom on paper;
 * at Night the mock's ramp, light into secondary into the secondary dimmed toward primary.
 */
private fun highlightRamp(
    stone: StonePalette,
    night: Boolean,
): Brush {
    val stops =
        if (night) {
            listOf(
                0f to stone.light,
                RAMP_MID to stone.secondary,
                1f to StoneTones.mixOklab(stone.secondary, NIGHT_RAMP_FOOT, stone.primary),
            )
        } else {
            StoneTones.bodyRamp(ValencePolarity.HIGHLIGHT, stone).map { it.offset to it.color }
        }
    return Brush.verticalGradient(*stops.toTypedArray())
}

/** The domains as one centred row of postmarks; TalkBack reads "Life domains: Friends and Community". */
@Composable
private fun Postmarks(
    pebble: MomentPebble,
    domains: List<Domain>,
    stone: StonePalette,
    night: Boolean,
    rowWidth: Dp,
) {
    if (pebble.domains.isEmpty()) return
    val colors = PebblesTheme.spine.colors
    val locale = LocalConfiguration.current.locales[0]
    val names = pebble.domains.map { ReferenceStrings.referenceName(ReferenceType.DOMAIN, it.slug, it.name) }
    val listed = remember(names, locale) { ListFormatter.getInstance(locale).format(names) }
    val description = stringResource(R.string.card_domains_a11y, listed)
    val count = pebble.domains.size
    val diameter = min(CardLayout.POSTMARK_DIAMETER, (rowWidth - CardLayout.POSTMARK_GAP * (count - 1)) / count)
    // The emotion's shaded tone on paper, its secondary at Night (back-token3 `--ink-c`).
    val ink = if (night) stone.secondary else stone.shaded

    Row(
        Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(CardLayout.POSTMARK_GAP, Alignment.CenterHorizontally),
    ) {
        pebble.domains.forEachIndexed { i, ref ->
            val domain = domains.firstOrNull { it.id == ref.id }
            DomainPostmark(
                name = names[i],
                strokes = domain?.strokes,
                viewBox = domain?.viewBox,
                ink = ink,
                paper = colors.stock,
                seed = remember(ref.id) { UuidHash.fnv1a(ref.id) },
                diameter = diameter,
            )
        }
    }
}

/** The verso's stone: [CardLayout.VERSO_STONE_WIDTH] for a large one, the others in the Spine's proportions. */
private fun versoStoneWidth(size: ValenceSizeGroup): Dp =
    CardLayout.VERSO_STONE_WIDTH *
        (StoneGeometry.STONE_WIDTH_DP.getValue(size) / StoneGeometry.STONE_WIDTH_DP.getValue(ValenceSizeGroup.LARGE))

/** The mat with its print, then the stock panel with its keyline and inner rule. */
private fun DrawScope.drawFrame(
    polarity: ValencePolarity,
    matInset: Float,
    panelInset: Float,
    panelFoot: Float,
    u: Float,
    tint: Color,
    pattern: Color,
    matRule: Color,
    panelRule: Color,
    stock: Color,
    cardRule: Color,
) {
    val rule = RULE.toPx()
    val matCorner = CornerRadius(u * MAT_CORNER_UNITS)
    inset(matInset) {
        val mat = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, matCorner)) }
        clipPath(mat) {
            drawRect(tint)
            drawFieldPattern(polarity, pattern)
        }
        drawRoundRect(
            matRule,
            topLeft = Offset(rule / 2, rule / 2),
            size = Size(size.width - rule, size.height - rule),
            cornerRadius = matCorner,
            style = Stroke(rule),
        )
    }
    val panel = Size(size.width - panelInset * 2, size.height - panelInset - panelFoot)
    val panelCorner = CornerRadius(u * PANEL_CORNER_UNITS)
    drawRoundRect(stock, topLeft = Offset(panelInset, panelInset), size = panel, cornerRadius = panelCorner)
    drawRoundRect(
        panelRule,
        topLeft = Offset(panelInset + rule / 2, panelInset + rule / 2),
        size = Size(panel.width - rule, panel.height - rule),
        cornerRadius = panelCorner,
        style = Stroke(rule),
    )
    val ruleInset = u * PANEL_RULE_INSET_UNITS
    drawRoundRect(
        cardRule,
        topLeft = Offset(panelInset + ruleInset, panelInset + ruleInset),
        size = Size(panel.width - ruleInset * 2, panel.height - ruleInset * 2),
        cornerRadius = CornerRadius(u * PANEL_RULE_CORNER_UNITS),
        style = Stroke(rule),
    )
}
