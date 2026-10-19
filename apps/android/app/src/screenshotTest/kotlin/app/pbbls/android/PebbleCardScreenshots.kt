package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.common.UuidHash
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.features.path.card.CardLayout
import app.pbbls.android.features.path.card.DomainPostmark
import com.android.tools.screenshot.PreviewTest

/*
 * The pebble card's parts (#1135 Part 1), each on the verso's stock in Paper and
 * Night: the domain postmarks, the collection band and the still footer.
 */

/** Two stamps at the full 72 dp. */
@PreviewTest
@Preview(showBackground = true)
@Composable
fun PostmarkRowLight() {
    PebblesTheme { PostmarkRow(listOf(PebbleCardFixtures.friends, PebbleCardFixtures.community), PebbleCardFixtures.joyPalette) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PostmarkRowNight() {
    PebblesTheme { PostmarkRow(listOf(PebbleCardFixtures.friends, PebbleCardFixtures.community), PebbleCardFixtures.joyPalette) }
}

/** Four French names at the shrunk diameter a 360 dp phone gives four stamps: (264 − 3 × 6) / 4. */
@PreviewTest
@Preview(showBackground = true, locale = "fr")
@Composable
fun PostmarkRowFourFrenchLight() {
    PebblesTheme { PostmarkRow(fourDomains, PebbleCardFixtures.gratitudePalette, diameter = shrunkDiameter) }
}

@PreviewTest
@Preview(showBackground = true, locale = "fr", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PostmarkRowFourFrenchNight() {
    PebblesTheme { PostmarkRow(fourDomains, PebbleCardFixtures.gratitudePalette, diameter = shrunkDiameter) }
}

private val fourDomains =
    listOf(PebbleCardFixtures.family, PebbleCardFixtures.travel, PebbleCardFixtures.health, PebbleCardFixtures.selfCare)

private val shrunkDiameter: Dp = (264.dp - CardLayout.POSTMARK_GAP * 3) / 4

/** The stamps as the verso inks them (spec §Verso 4): the emotion's shaded tone on paper, its secondary at Night. */
@Composable
private fun PostmarkRow(
    domains: List<Domain>,
    palette: EmotionPalette,
    diameter: Dp = CardLayout.POSTMARK_DIAMETER,
) {
    val colors = PebblesTheme.spine.colors
    val ink = if (colors.isNight) palette.secondary else palette.shaded
    Row(
        modifier = Modifier.background(colors.stock).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(CardLayout.POSTMARK_GAP),
    ) {
        domains.forEach { domain ->
            DomainPostmark(
                name = ReferenceStrings.referenceName(ReferenceType.DOMAIN, domain.slug, domain.name),
                strokes = domain.strokes,
                viewBox = domain.viewBox,
                ink = ink,
                paper = colors.stock,
                seed = UuidHash.fnv1a(domain.id),
                diameter = diameter,
            )
        }
    }
}
