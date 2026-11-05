package app.pbbls.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.decodeCrisisResources
import app.pbbls.android.features.profile.support.CrisisResourcesContent
import com.android.tools.screenshot.PreviewTest

/** Crisis resources (#1461): France selected, so a two-line country shows both rows and the worldwide link. */
@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@PreviewFrench
@Composable
fun CrisisResourcesFrance() {
    PebblesTheme {
        CrisisResourcesContent(
            resources = decodeCrisisResources(CRISIS_RESOURCES_JSON),
            selectedCode = "FR",
            onSelectCountry = {},
            onDial = {},
            onOpenWorldwide = {},
            onBack = {},
        )
    }
}

// A trimmed copy of the canonical file: the screenshot source set reads no assets.
private const val CRISIS_RESOURCES_JSON =
    """{"version":1,"verified_on":"2026-11-05","worldwide":{"label":{"en":"Find a helpline anywhere","fr":"Trouver une ligne d'écoute partout"},"url":"https://findahelpline.com"},"countries":[{"code":"FR","name":{"en":"France","fr":"France"},"lines":[{"label":{"en":"3114, national suicide prevention line","fr":"3114, numéro national de prévention du suicide"},"number":"3114","tel":"3114","note":{"en":"Free, 24/7","fr":"Gratuit, 24h/24"}},{"label":{"en":"Emergency services","fr":"Urgences"},"number":"112","tel":"112"}]}]}"""
