package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.path.render.PebbleStaticRender
import app.pbbls.android.features.path.render.PebbleSvg
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import com.android.tools.screenshot.PreviewTest

/**
 * SVG-fidelity spike (issue #531, umbrella design D10 / risk 2): renders all
 * nine authentic engine-composed pebble SVGs through [PebbleSvg] so the CI
 * `ui-screenshots` artifact can be compared side-by-side against the iOS
 * renders. The stroke hexes are the light/dark roles of a real category
 * palette (primary `#7B5E99` / secondary `#AE91CC`, the values the iOS test
 * suite uses), exercising the same `currentColor` substitution as production.
 *
 * Exit criterion (maintainer): shapes, clip paths (mediumNeutral), fill rules
 * (largeLowlight), fossil opacity (smallNeutral) and glyph transforms all
 * match iOS. Fallback ladder if not: (a) server-side SVG tweak, (b) scoped
 * custom renderer — see the plan doc.
 */
@Composable
private fun FidelityGrid(strokeHex: String) {
    val system = PebblesTheme.colors.system
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PebbleSvgFixtures.all.chunked(3).forEach { rowFixtures ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowFixtures.forEach { (name, svg) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(96.dp)) {
                            PebbleSvg(
                                svg = svg,
                                strokeHex = strokeHex,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Text(
                            text = name,
                            style = PebblesTypography.captionEmphasized,
                            color = system.secondary,
                        )
                    }
                }
            }
        }
        // Full-size renders of the two risk-feature carriers: clipPath and
        // fill-rule/clip-rule survive AndroidSVG or the ladder kicks in.
        listOf(
            "mediumNeutral (clipPath)" to PebbleSvgFixtures.mediumNeutral,
            "largeLowlight (fill-rule)" to PebbleSvgFixtures.largeLowlight,
        ).forEach { (label, svg) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(260.dp)) {
                    PebbleSvg(
                        svg = svg,
                        strokeHex = strokeHex,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    text = label,
                    style = PebblesTypography.captionEmphasized,
                    color = system.secondary,
                )
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 1200)
@Composable
fun PebbleSvgFidelityLight() {
    // Light mode strokes with the palette *primary* role, as production does.
    PebblesTheme { FidelityGrid(strokeHex = "#7B5E99") }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 1200, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleSvgFidelityDark() {
    // Dark mode strokes with the palette *secondary* role, as production does.
    PebblesTheme { FidelityGrid(strokeHex = "#AE91CC") }
}

/**
 * The same nine fixtures rendered through [PebbleStaticRender] — the
 * layer-tracing renderer the Path and detail pages now use. Compared against
 * [FidelityGrid] above, the glyph strokes read at the *outline's* weight instead
 * of their authored (custom-heavy / domain-light) width, so glyph == outline
 * and custom vs domain glyphs match. The outline itself is unchanged.
 */
@Composable
private fun TracedGrid(strokeHex: String) {
    val system = PebblesTheme.colors.system
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PebbleSvgFixtures.all.chunked(3).forEach { rowFixtures ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowFixtures.forEach { (name, svg) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(96.dp)) {
                            PebbleStaticRender(
                                svg = svg,
                                strokeHex = strokeHex,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Text(
                            text = name,
                            style = PebblesTypography.captionEmphasized,
                            color = system.secondary,
                        )
                    }
                }
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 900)
@Composable
fun PebbleTracedFidelityLight() {
    PebblesTheme { TracedGrid(strokeHex = "#7B5E99") }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 900, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleTracedFidelityDark() {
    PebblesTheme { TracedGrid(strokeHex = "#AE91CC") }
}
