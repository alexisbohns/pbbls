package app.pbbls.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pbbls.android.rive.RiveLogo
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

private data class TokenSwatch(
    val name: String,
    val color: Color,
)

private val SwatchLabelStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp)

/**
 * Debug composable: color swatches (System + Accent), the full type ramp, and
 * the Rive logo — one screen the maintainer can review as a screenshot
 * without a device. Mirrors `apps/ios/Pebbles/Theme/ColorTokensPreview.swift`,
 * extended with the type ramp + logo. This is `MainActivity`'s temporary home
 * for sub-project B; the real home lands in C.
 */
@Composable
fun DebugTokenPreviewScreen() {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val spacing = PebblesTheme.spacing

    Box(modifier = Modifier.fillMaxSize().background(system.background)) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xxl),
        ) {
            TokenSection(
                title = "System",
                swatches =
                    listOf(
                        TokenSwatch("system.foreground", system.foreground),
                        TokenSwatch("system.secondary", system.secondary),
                        TokenSwatch("system.muted", system.muted),
                        TokenSwatch("system.background", system.background),
                    ),
            )
            TokenSection(
                title = "Accent",
                swatches =
                    listOf(
                        TokenSwatch("accent.dark", accent.dark),
                        TokenSwatch("accent.shaded", accent.shaded),
                        TokenSwatch("accent.primary", accent.primary),
                        TokenSwatch("accent.secondary", accent.secondary),
                        TokenSwatch("accent.light", accent.light),
                        TokenSwatch("accent.surface", accent.surface),
                    ),
            )
            TypeRampSection()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PebblesText(text = "Rive logo", style = PebblesTypography.headline, color = system.foreground)
                RiveLogo(modifier = Modifier.fillMaxWidth().height(160.dp))
            }
        }
    }
}

@Composable
private fun TokenSection(
    title: String,
    swatches: List<TokenSwatch>,
) {
    val system = PebblesTheme.colors.system
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PebblesText(text = title, style = PebblesTypography.headline, color = system.foreground)
        swatches.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { swatch -> Swatch(swatch, modifier = Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Swatch(
    swatch: TokenSwatch,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(swatch.color, RoundedCornerShape(8.dp))
                    .border(1.dp, system.foreground.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
        )
        Text(text = swatch.name, style = SwatchLabelStyle, color = system.foreground)
    }
}

@Composable
private fun TypeRampSection() {
    val system = PebblesTheme.colors.system
    val samples =
        listOf(
            "body" to PebblesTypography.body,
            "bodyEmphasized" to PebblesTypography.bodyEmphasized,
            "subhead" to PebblesTypography.subhead,
            "subheadEmphasized" to PebblesTypography.subheadEmphasized,
            "headline" to PebblesTypography.headline,
            "headlineEmphasized" to PebblesTypography.headlineEmphasized,
            "callout" to PebblesTypography.callout,
            "calloutEmphasized" to PebblesTypography.calloutEmphasized,
            "meta" to PebblesTypography.meta,
            "metaEmphasized" to PebblesTypography.metaEmphasized,
            "cardHeading" to PebblesTypography.cardHeading,
            "cardHeadingEmphasized" to PebblesTypography.cardHeadingEmphasized,
            "counterLg" to PebblesTypography.counterLg,
            "captionEmphasized" to PebblesTypography.captionEmphasized,
            "title" to PebblesTypography.title,
            "buttonLabel" to PebblesTypography.buttonLabel,
            "bodyLeadHand" to PebblesTypography.bodyLeadHand,
            "largeTitleHand" to PebblesTypography.largeTitleHand,
        )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PebblesText(text = "Type ramp", style = PebblesTypography.headline, color = system.foreground)
        samples.forEach { (name, style) ->
            PebblesText(text = "$name — Pebbles 123", style = style, color = system.foreground)
        }
    }
}
