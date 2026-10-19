package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
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
import app.pbbls.android.features.path.card.CollectionBand
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

/** One collection across a 328 dp card (a 360 dp phone). */
@PreviewTest
@Preview(showBackground = true)
@Composable
fun CollectionBandOneLight() {
    PebblesTheme { BandOnStock(listOf("Birthdays"), PebbleCardFixtures.joyPalette) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CollectionBandOneNight() {
    PebblesTheme { BandOnStock(listOf("Birthdays"), PebbleCardFixtures.joyPalette) }
}

/** Two long French names: they wrap inside the band, centred. */
@PreviewTest
@Preview(showBackground = true, locale = "fr")
@Composable
fun CollectionBandTwoFrenchLight() {
    PebblesTheme { BandOnStock(twoFrenchCollections, PebbleCardFixtures.gratitudePalette) }
}

@PreviewTest
@Preview(showBackground = true, locale = "fr", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CollectionBandTwoFrenchNight() {
    PebblesTheme { BandOnStock(twoFrenchCollections, PebbleCardFixtures.gratitudePalette) }
}

private val twoFrenchCollections = listOf("Les étés chez Mamie à Pornichet", "Ce qu’Élio a dit pendant le voyage")

/** The band as the verso tints it (the mock's `--band`): the emotion's spot mixed into the stock, its rule in the spot. */
@Composable
private fun BandOnStock(
    names: List<String>,
    palette: EmotionPalette,
) {
    val colors = PebblesTheme.spine.colors
    val spot = if (colors.isNight) palette.secondary else palette.primary
    Box(Modifier.background(colors.ground).padding(vertical = 16.dp)) {
        CollectionBand(
            names = names,
            paper = lerp(colors.stock, spot, if (colors.isNight) 0.24f else 0.19f),
            ink = colors.ink,
            edge = spot,
            modifier = Modifier.width(328.dp),
        )
    }
}
