package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.data.HomeShortcutsLayout
import app.pbbls.android.core.designsystem.ListGroup
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.HealthDataConsent
import app.pbbls.android.features.profile.HealthConsentRow
import app.pbbls.android.features.profile.HealthConsentStatus
import app.pbbls.android.features.profile.SettingsAccountSections
import app.pbbls.android.features.profile.SettingsActions
import app.pbbls.android.features.profile.SettingsContent
import app.pbbls.android.features.profile.SettingsDeviceChoices
import app.pbbls.android.features.profile.SettingsDeviceSections
import app.pbbls.android.features.profile.SettingsForm
import app.pbbls.android.features.profile.SettingsHomeSection
import app.pbbls.android.features.profile.SettingsIdentitySections
import app.pbbls.android.features.profile.SettingsInitial
import app.pbbls.android.features.profile.SettingsSignInSections
import app.pbbls.android.features.profile.SettingsUiState
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime

/*
 * Settings previews (#847), in Android's own Settings form: M3 Expressive segmented list
 * groups under primary headers, stock switches (#1383). They render the screen's stateless
 * layer, `SettingsContent`, and its three runs of sections with the real string resources, so
 * a label clipping at `fontScale = 2f` or a French string squeezing its row shows up.
 *
 * The whole page is taller than the renderer's frame, so the page preview shows its head (the
 * top bar, the glyph, the first groups) and each run of sections has a preview of its own, short
 * enough that its large-font variant stays inside the renderer's 2400 px frame.
 */

/** Noon UTC keeps the localized date the same in any zone the job runs in. */
private val GivenConsent =
    HealthConsentStatus.Given(HealthDataConsent("1.4.0", OffsetDateTime.parse("2026-10-01T12:00:00Z")))

/** An email account with a live public profile: the share row shows, Save is enabled. */
private val EmailAccount =
    SettingsUiState(
        initial =
            SettingsInitial(
                displayName = "Alexis",
                handle = "alexis",
                publicProfile = true,
                email = "alexis@example.com",
            ),
        form = SettingsForm(displayName = "Alexis B.", handle = "alexis", isPublicProfile = true),
        healthConsent = GivenConsent,
        isLoading = false,
    )

/** A Google account with no handle yet: the public switch is disabled, providers replace the password. */
private val SsoAccount =
    SettingsUiState(
        initial = SettingsInitial(displayName = "Alexis", email = "alexis@example.com", providers = listOf("Google")),
        form = SettingsForm(displayName = "Alexis"),
        healthConsent = HealthConsentStatus.NotRecorded,
        isLoading = false,
    )

private val Device = SettingsDeviceChoices()

/** A run of sections as the page lays it out: the page's gutter and its gap between groups. */
@Composable
private fun SectionsFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xl),
        content = content,
    )
}

// The page at phone height: top bar with the back arrow and Save, the glyph, the first groups.
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@PreviewLargeFontTall
@PreviewWideTall
@Composable
fun SettingsPageLight() {
    PebblesTheme { SettingsContent(state = EmailAccount, device = Device, actions = SettingsActions()) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SettingsPageDark() {
    PebblesTheme { SettingsContent(state = EmailAccount, device = Device, actions = SettingsActions()) }
}

// Appearance, Home and Haptics, switches both on and off so both thumbs are on file.
@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@PreviewLargeFont
@Composable
fun SettingsDeviceSectionsLight() {
    PebblesTheme {
        SectionsFrame {
            SettingsDeviceSections(
                device =
                    SettingsDeviceChoices(
                        useWallpaperColors = false,
                        hapticsOn = true,
                        shortcutsLayout = HomeShortcutsLayout.BENTO,
                        shortcutsStones = false,
                    ),
                actions = SettingsActions(),
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SettingsDeviceSectionsDark() {
    PebblesTheme { SectionsFrame { SettingsDeviceSections(device = Device, actions = SettingsActions()) } }
}

// Informations and Public profile for an email account with a live public profile.
@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@PreviewLargeFont
@Composable
fun SettingsIdentitySectionsLight() {
    PebblesTheme { SectionsFrame { SettingsIdentitySections(state = EmailAccount, actions = SettingsActions()) } }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SettingsIdentitySectionsDark() {
    PebblesTheme { SectionsFrame { SettingsIdentitySections(state = EmailAccount, actions = SettingsActions()) } }
}

// The same run for an SSO account with no handle yet: the public switch is disabled.
@PreviewTest
@Preview(showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SettingsIdentitySectionsSso() {
    PebblesTheme { SectionsFrame { SettingsIdentitySections(state = SsoAccount, actions = SettingsActions()) } }
}

// The password field, under it the save error a failed save leaves.
@PreviewTest
@Preview(showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@PreviewFrench
@PreviewLargeFont
@Composable
fun SettingsSignInSectionsPassword() {
    PebblesTheme {
        SectionsFrame { SettingsSignInSections(state = EmailAccount.copy(didSaveFail = true), actions = SettingsActions()) }
    }
}

// An SSO account's providers, in place of the password.
@PreviewTest
@Preview(showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SettingsSignInSectionsProviders() {
    PebblesTheme { SectionsFrame { SettingsSignInSections(state = SsoAccount, actions = SettingsActions()) } }
}

// Legal, Consent, Account, and Log out in its own group at the end.
@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@PreviewLargeFont
@Composable
fun SettingsAccountSectionsLight() {
    PebblesTheme { SectionsFrame { SettingsAccountSections(state = EmailAccount, actions = SettingsActions()) } }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SettingsAccountSectionsDark() {
    PebblesTheme { SectionsFrame { SettingsAccountSections(state = EmailAccount, actions = SettingsActions()) } }
}

/** The Consent group (#972) with every state of its one row, one item each. */
@Composable
private fun SettingsConsentSection() {
    SectionsFrame {
        ListGroup(
            header = stringResource(R.string.settings_consent_header),
            items =
                listOf(
                    { HealthConsentRow(status = GivenConsent, canWithdraw = true, onWithdraw = {}) },
                    { HealthConsentRow(status = HealthConsentStatus.NotRecorded, canWithdraw = true, onWithdraw = {}) },
                    { HealthConsentRow(status = HealthConsentStatus.Unavailable, canWithdraw = true, onWithdraw = {}) },
                ),
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@PreviewFrench
@Composable
fun SettingsConsentSectionLight() {
    PebblesTheme { SettingsConsentSection() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SettingsConsentSectionDark() {
    PebblesTheme { SettingsConsentSection() }
}

/**
 * The Home group (#1383 D6, #1376): the quick actions' Row / Bento choice and the pebble shapes
 * switch, twice: the defaults (row, shapes on) and the other choice of each.
 */
@Composable
private fun SettingsHomeSections() {
    SectionsFrame {
        SettingsHomeSection(layout = HomeShortcutsLayout.ROW, stones = true, onLayoutChange = {}, onStonesChange = {})
        SettingsHomeSection(layout = HomeShortcutsLayout.BENTO, stones = false, onLayoutChange = {}, onStonesChange = {})
    }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@PreviewFrench
@Composable
fun SettingsHomeSectionLight() {
    PebblesTheme { SettingsHomeSections() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SettingsHomeSectionDark() {
    PebblesTheme { SettingsHomeSections() }
}
