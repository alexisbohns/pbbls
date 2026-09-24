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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.rive.RiveLogo

private data class TokenSwatch(
    val name: String,
    val color: Color,
)

private data class SwatchGroup(
    val title: String,
    val swatches: List<TokenSwatch>,
)

/** Every role of [scheme], grouped the way the Material Theme Builder lays them out. */
private fun schemeGroups(scheme: ColorScheme): List<SwatchGroup> =
    listOf(
        SwatchGroup(
            "Primary",
            listOf(
                TokenSwatch("primary", scheme.primary),
                TokenSwatch("onPrimary", scheme.onPrimary),
                TokenSwatch("primaryContainer", scheme.primaryContainer),
                TokenSwatch("onPrimaryContainer", scheme.onPrimaryContainer),
                TokenSwatch("inversePrimary", scheme.inversePrimary),
            ),
        ),
        SwatchGroup(
            "Secondary",
            listOf(
                TokenSwatch("secondary", scheme.secondary),
                TokenSwatch("onSecondary", scheme.onSecondary),
                TokenSwatch("secondaryContainer", scheme.secondaryContainer),
                TokenSwatch("onSecondaryContainer", scheme.onSecondaryContainer),
            ),
        ),
        SwatchGroup(
            "Tertiary",
            listOf(
                TokenSwatch("tertiary", scheme.tertiary),
                TokenSwatch("onTertiary", scheme.onTertiary),
                TokenSwatch("tertiaryContainer", scheme.tertiaryContainer),
                TokenSwatch("onTertiaryContainer", scheme.onTertiaryContainer),
            ),
        ),
        SwatchGroup(
            "Error",
            listOf(
                TokenSwatch("error", scheme.error),
                TokenSwatch("onError", scheme.onError),
                TokenSwatch("errorContainer", scheme.errorContainer),
                TokenSwatch("onErrorContainer", scheme.onErrorContainer),
            ),
        ),
        SwatchGroup(
            "Surface",
            listOf(
                TokenSwatch("surfaceDim", scheme.surfaceDim),
                TokenSwatch("surface", scheme.surface),
                TokenSwatch("surfaceBright", scheme.surfaceBright),
                TokenSwatch("surfaceContainerLowest", scheme.surfaceContainerLowest),
                TokenSwatch("surfaceContainerLow", scheme.surfaceContainerLow),
                TokenSwatch("surfaceContainer", scheme.surfaceContainer),
                TokenSwatch("surfaceContainerHigh", scheme.surfaceContainerHigh),
                TokenSwatch("surfaceContainerHighest", scheme.surfaceContainerHighest),
                TokenSwatch("onSurface", scheme.onSurface),
                TokenSwatch("onSurfaceVariant", scheme.onSurfaceVariant),
                TokenSwatch("inverseSurface", scheme.inverseSurface),
                TokenSwatch("inverseOnSurface", scheme.inverseOnSurface),
            ),
        ),
        SwatchGroup(
            "Outline",
            listOf(
                TokenSwatch("outline", scheme.outline),
                TokenSwatch("outlineVariant", scheme.outlineVariant),
                TokenSwatch("scrim", scheme.scrim),
            ),
        ),
        SwatchGroup(
            "Fixed",
            listOf(
                TokenSwatch("primaryFixed", scheme.primaryFixed),
                TokenSwatch("primaryFixedDim", scheme.primaryFixedDim),
                TokenSwatch("onPrimaryFixed", scheme.onPrimaryFixed),
                TokenSwatch("onPrimaryFixedVariant", scheme.onPrimaryFixedVariant),
                TokenSwatch("secondaryFixed", scheme.secondaryFixed),
                TokenSwatch("secondaryFixedDim", scheme.secondaryFixedDim),
                TokenSwatch("onSecondaryFixed", scheme.onSecondaryFixed),
                TokenSwatch("onSecondaryFixedVariant", scheme.onSecondaryFixedVariant),
                TokenSwatch("tertiaryFixed", scheme.tertiaryFixed),
                TokenSwatch("tertiaryFixedDim", scheme.tertiaryFixedDim),
                TokenSwatch("onTertiaryFixed", scheme.onTertiaryFixed),
                TokenSwatch("onTertiaryFixedVariant", scheme.onTertiaryFixedVariant),
            ),
        ),
    )

/**
 * Debug composable: every `MaterialTheme.colorScheme` role, the
 * `MaterialTheme.typography` scale (with the emphasized styles the app reads)
 * plus the handwritten faces, and the Rive logo — one screen the maintainer can
 * review as a screenshot without a device (#853 replaced the old
 * `system.*`/`accent.*` swatches with the scheme's roles).
 */
@Composable
fun DebugTokenPreviewScreen() {
    val colors = MaterialTheme.colorScheme
    val spacing = PebblesTheme.spacing

    Box(modifier = Modifier.fillMaxSize().background(colors.surface)) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xxl),
        ) {
            schemeGroups(colors).forEach { group -> TokenSection(group) }
            TypeRampSection()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Rive logo", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                RiveLogo(modifier = Modifier.fillMaxWidth().height(160.dp))
            }
        }
    }
}

@Composable
private fun TokenSection(group: SwatchGroup) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = group.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        group.swatches.chunked(3).forEach { row ->
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
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
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
                    .background(swatch.color, shape)
                    .border(1.dp, colors.outlineVariant, shape),
        )
        Text(
            text = swatch.name,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = colors.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TypeRampSection() {
    val type = MaterialTheme.typography
    val hand = PebblesTheme.hand
    val samples =
        listOf(
            "displayLarge" to type.displayLarge,
            "displayMedium" to type.displayMedium,
            "displaySmall" to type.displaySmall,
            "headlineLarge" to type.headlineLarge,
            "headlineMedium" to type.headlineMedium,
            "headlineSmall" to type.headlineSmall,
            "titleLarge" to type.titleLarge,
            "titleMedium" to type.titleMedium,
            "titleMediumEmphasized" to type.titleMediumEmphasized,
            "titleSmall" to type.titleSmall,
            "bodyLarge" to type.bodyLarge,
            "bodyLargeEmphasized" to type.bodyLargeEmphasized,
            "bodyMedium" to type.bodyMedium,
            "bodyMediumEmphasized" to type.bodyMediumEmphasized,
            "bodySmall" to type.bodySmall,
            "labelLarge" to type.labelLarge,
            "labelMedium" to type.labelMedium,
            "labelMediumEmphasized" to type.labelMediumEmphasized,
            "labelSmall" to type.labelSmall,
            "hand.bodyLeadHand" to hand.bodyLeadHand,
            "hand.largeTitleHand" to hand.largeTitleHand,
            "hand.nameInputHand" to hand.nameInputHand,
            "hand.valenceWord" to hand.valenceWord,
        )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Type ramp", style = type.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        samples.forEach { (name, style) ->
            Text(text = "$name — Pebbles 123", style = style, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
