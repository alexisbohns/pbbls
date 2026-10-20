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
import app.pbbls.android.core.model.CardFace
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.features.path.card.CardFaceTarget
import app.pbbls.android.features.path.card.CardFooter
import app.pbbls.android.features.path.card.CardLayout
import app.pbbls.android.features.path.card.CollectionBand
import app.pbbls.android.features.path.card.DomainPostmark
import app.pbbls.android.features.path.card.PebbleCard
import com.android.tools.screenshot.PreviewTest

/*
 * The pebble card's parts (#1135 Part 1), each on the verso's stock in Paper and
 * Night: the domain postmarks, the collection band and the still footer. Then the
 * whole card, each face alone on a 412 dp phone.
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

/** The accented French capitals at the full 72 dp: É and Ê must clear the outer ring. */
@PreviewTest
@Preview(showBackground = true, locale = "fr")
@Composable
fun PostmarkRowAccentsFrenchLight() {
    PebblesTheme { PostmarkRow(listOf(PebbleCardFixtures.health, PebbleCardFixtures.selfCare), PebbleCardFixtures.gratitudePalette) }
}

@PreviewTest
@Preview(showBackground = true, locale = "fr", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PostmarkRowAccentsFrenchNight() {
    PebblesTheme { PostmarkRow(listOf(PebbleCardFixtures.health, PebbleCardFixtures.selfCare), PebbleCardFixtures.gratitudePalette) }
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

/** The recto's footer: shared with connections, turning to the pebble. */
@PreviewTest
@Preview(showBackground = true)
@Composable
fun CardFooterRectoConnectionsLight() {
    PebblesTheme { FooterOnStock(Visibility.PRIVATE, CardFaceTarget.PEBBLE) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CardFooterRectoConnectionsNight() {
    PebblesTheme { FooterOnStock(Visibility.PRIVATE, CardFaceTarget.PEBBLE) }
}

/** The verso's footer: public, turning to the moment. */
@PreviewTest
@Preview(showBackground = true)
@Composable
fun CardFooterVersoPublicLight() {
    PebblesTheme { FooterOnStock(Visibility.PUBLIC, CardFaceTarget.MOMENT) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CardFooterVersoPublicNight() {
    PebblesTheme { FooterOnStock(Visibility.PUBLIC, CardFaceTarget.MOMENT) }
}

/** French, secret, on the recto: "RETOURNER" over "Galet". */
@PreviewTest
@Preview(showBackground = true, locale = "fr")
@Composable
fun CardFooterSecretFrenchLight() {
    PebblesTheme { FooterOnStock(Visibility.SECRET, CardFaceTarget.PEBBLE) }
}

@PreviewTest
@Preview(showBackground = true, locale = "fr", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CardFooterSecretFrenchNight() {
    PebblesTheme { FooterOnStock(Visibility.SECRET, CardFaceTarget.PEBBLE) }
}

/** French at the largest font scale on a 288 dp card: the turn drops under the privacy rather than squeezing it. */
@PreviewTest
@Preview(showBackground = true, locale = "fr", fontScale = 2f)
@Composable
fun CardFooterPublicFrenchLargeFontLight() {
    PebblesTheme { FooterOnStock(Visibility.PUBLIC, CardFaceTarget.PEBBLE, width = 288.dp) }
}

/** The footer across a 328 dp card's stock; the miniature's stone is the joy spot. */
@Composable
private fun FooterOnStock(
    visibility: Visibility,
    turnTo: CardFaceTarget,
    width: Dp = 328.dp,
) {
    val colors = PebblesTheme.spine.colors
    val palette = PebbleCardFixtures.joyPalette
    Box(Modifier.background(colors.ground).padding(16.dp)) {
        CardFooter(
            visibility = visibility,
            turnTo = turnTo,
            stone = if (colors.isNight) palette.secondary else palette.primary,
            onTurn = {},
            modifier = Modifier.width(width).background(colors.stock),
        )
    }
}

/** F1's recto: the photo field, five souls named, the leaf with the long words. */
@PreviewTest
@Preview(showBackground = true, widthDp = 412)
@Composable
fun PebbleCardF1RectoLight() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.f1, CardFace.RECTO, PebbleCardFixtures.ROOFTOP_RATIO) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 412, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleCardF1RectoNight() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.f1, CardFace.RECTO, PebbleCardFixtures.ROOFTOP_RATIO) }
}

/** F1's verso: "Highlight of my month", two postmarks, the Birthdays band. */
@PreviewTest
@Preview(showBackground = true, widthDp = 412)
@Composable
fun PebbleCardF1VersoLight() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.f1, CardFace.VERSO, PebbleCardFixtures.ROOFTOP_RATIO) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 412, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleCardF1VersoNight() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.f1, CardFace.VERSO, PebbleCardFixtures.ROOFTOP_RATIO) }
}

/** F1's recto in French: the locale's full dateline, "jeudi 8 octobre 2026 · 21:00". */
@PreviewTest
@Preview(showBackground = true, widthDp = 412, locale = "fr")
@Composable
fun PebbleCardF1RectoFrenchLight() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.f1, CardFace.RECTO, PebbleCardFixtures.ROOFTOP_RATIO) }
}

/** F1's verso in French: "Temps fort de mon mois", "Retourner". */
@PreviewTest
@Preview(showBackground = true, widthDp = 412, locale = "fr")
@Composable
fun PebbleCardF1VersoFrenchLight() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.f1, CardFace.VERSO, PebbleCardFixtures.ROOFTOP_RATIO) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 412, locale = "fr", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleCardF1VersoFrenchNight() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.f1, CardFace.VERSO, PebbleCardFixtures.ROOFTOP_RATIO) }
}

/** F2's verso: a medium stone, one postmark, no band; the face is as tall as the portrait recto. */
@PreviewTest
@Preview(showBackground = true, widthDp = 412)
@Composable
fun PebbleCardF2VersoLight() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.f2, CardFace.VERSO, PebbleCardFixtures.SEAWALL_RATIO) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 412, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleCardF2VersoNight() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.f2, CardFace.VERSO, PebbleCardFixtures.SEAWALL_RATIO) }
}

/** F3's recto: no photo, so the slip carries the name; nobody with it. */
@PreviewTest
@Preview(showBackground = true, widthDp = 412)
@Composable
fun PebbleCardF3RectoLight() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.f3, CardFace.RECTO, ratio = null) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 412, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleCardF3RectoNight() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.f3, CardFace.RECTO, ratio = null) }
}

/** F3's verso: "lowlight of my day" in ink, a small stone. */
@PreviewTest
@Preview(showBackground = true, widthDp = 412)
@Composable
fun PebbleCardF3VersoLight() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.f3, CardFace.VERSO, ratio = null) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 412, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleCardF3VersoNight() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.f3, CardFace.VERSO, ratio = null) }
}

/** The stress verso: four shrunk postmarks and a band that wraps two long names. */
@PreviewTest
@Preview(showBackground = true, widthDp = 412)
@Composable
fun PebbleCardStressVersoLight() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.stress, CardFace.VERSO, PebbleCardFixtures.ROOFTOP_RATIO) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 412, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleCardStressVersoNight() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.stress, CardFace.VERSO, PebbleCardFixtures.ROOFTOP_RATIO) }
}

/**
 * F1 a third of the way through a turn from its recto: the face leans, its far edge
 * recedes, and it is shrunk just enough that its near corners stay inside the shell.
 */
@PreviewTest
@Preview(showBackground = true, widthDp = 412)
@Composable
fun PebbleCardMidTurn() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.f1, CardFace.RECTO, PebbleCardFixtures.ROOFTOP_RATIO, turnProgress = 0.3f) }
}

/** Past the half: the verso is in front, coming round, and the footer already offers the moment. */
@PreviewTest
@Preview(showBackground = true, widthDp = 412)
@Composable
fun PebbleCardMidTurnPastHalf() {
    PebblesTheme { CardOnGround(PebbleCardFixtures.f1, CardFace.RECTO, PebbleCardFixtures.ROOFTOP_RATIO, turnProgress = 0.7f) }
}

/** The card alone on the page's ground; the photo is unsigned, so the field shows its tint. */
@Composable
private fun CardOnGround(
    detail: MomentDetail,
    face: CardFace,
    ratio: Float?,
    turnProgress: Float? = null,
) {
    val colors = PebblesTheme.spine.colors
    Box(Modifier.background(colors.ground).padding(vertical = 16.dp)) {
        PebbleCard(
            detail = detail,
            palette =
                detail.pebble
                    ?.emotion
                    ?.id
                    ?.let(PebbleCardFixtures.palettes::get),
            ratio = ratio,
            photoUrl = null,
            thumbUrl = null,
            soulsById = PebbleCardFixtures.soulsById,
            domains = PebbleCardFixtures.domains,
            requestedFace = face,
            zone = PebbleCardFixtures.zone,
            turnProgressOverride = turnProgress,
        )
    }
}
