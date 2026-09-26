package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesListDefaults
import app.pbbls.android.core.designsystem.PebblesListSection
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesSectionHeader
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTopBarTextButton
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase
import app.pbbls.android.features.profile.SettingsNavRow
import app.pbbls.android.features.profile.settingsRowColors
import com.android.tools.screenshot.PreviewTest

/**
 * Settings previews (#847). `SettingsScreen` itself reads `LocalProfileService`
 * and `LocalSupabaseService`, and neither can be provided here: `ProfileService`
 * needs a `SupabaseService`, whose constructor goes through `AppEnvironment` and
 * throws whenever `BuildConfig.SUPABASE_URL` is blank — which is every fork PR
 * and every screenshot job that runs without the repo secrets. So this renders
 * the screen's *sections* against the real `PebblesListSection`, `PebblesScreen`
 * and `PebblesTopBar`, with the real string resources, rather than the screen.
 *
 * What that buys and what it does not: every field and row here is the one
 * `SettingsScreen` renders — the outlined fields with their labels and
 * supporting text, and the `ListItem` rows (`SettingsNavRow`,
 * `settingsRowColors`, shared with the screen) — so a control that clips its
 * own text at `fontScale = 2f`, or a French label that squeezes its row, shows
 * up. A regression in the screen's
 * own scroll column, its IME padding or its dirty-state top bar does not. That
 * gap closes when Settings grows a stateless content layer the way `PathScreen`
 * has `PathContent`; #848/#849 own that screen's architecture, so it is not
 * pulled forward here.
 */
@Composable
private fun SettingsWallpaperColorsRow(checked: Boolean) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_wallpaper_colors_title)) },
        supportingContent = { Text(stringResource(R.string.settings_wallpaper_colors_body)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = settingsRowColors(),
    )
}

@Composable
private fun SettingsSections() {
    val colors = MaterialTheme.colorScheme
    PebblesScreen(
        topBar = {
            PebblesTopBar(
                title = stringResource(R.string.settings_title),
                leading = {
                    PebblesTopBarTextButton(text = stringResource(R.string.action_cancel), onClick = {})
                },
                trailing = {
                    PebblesTopBarTextButton(text = stringResource(R.string.action_save), onClick = {})
                },
            )
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xl),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                GlyphView(case = GlyphViewCase.CARVE, strokes = null, side = 120.dp)
            }

            PebblesListSection(
                header = stringResource(R.string.settings_appearance_section),
                rowPadding = PebblesListDefaults.ListItemRowPadding,
                rows =
                    listOf(
                        { SettingsWallpaperColorsRow(checked = true) },
                        { SettingsWallpaperColorsRow(checked = false) },
                    ),
            )

            Column(verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm)) {
                PebblesSectionHeader(text = stringResource(R.string.settings_informations_header))
                OutlinedTextField(
                    value = "Alexis",
                    onValueChange = {},
                    label = { Text(stringResource(R.string.settings_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = "alexis@example.com",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_email_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm)) {
                PebblesSectionHeader(text = stringResource(R.string.settings_public_profile_header))
                OutlinedTextField(
                    value = "alexis",
                    onValueChange = {},
                    label = { Text(stringResource(R.string.settings_handle_label)) },
                    prefix = { Text("@") },
                    supportingText = { Text(stringResource(R.string.settings_handle_footer)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PebblesListSection(
                    rowPadding = PebblesListDefaults.ListItemRowPadding,
                    rows =
                        listOf(
                            {
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.settings_public_profile_toggle)) },
                                    trailingContent = { Switch(checked = true, onCheckedChange = null) },
                                    colors = settingsRowColors(),
                                )
                            },
                            { SettingsNavRow(text = stringResource(R.string.settings_public_profile_share), onClick = {}) },
                        ),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm)) {
                PebblesSectionHeader(text = stringResource(R.string.settings_password_header))
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = { Text(stringResource(R.string.settings_password_placeholder)) },
                    supportingText = { Text(stringResource(R.string.settings_password_footer)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PebblesListSection(
                header = stringResource(R.string.settings_legal_header),
                rowPadding = PebblesListDefaults.ListItemRowPadding,
                rows =
                    listOf(
                        { SettingsNavRow(text = stringResource(R.string.auth_consent_terms_link), onClick = {}) },
                        { SettingsNavRow(text = stringResource(R.string.auth_consent_privacy_link), onClick = {}) },
                    ),
            )

            PebblesListSection(
                header = stringResource(R.string.settings_account_header),
                rowPadding = PebblesListDefaults.ListItemRowPadding,
                rows =
                    listOf(
                        {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_delete_account), color = colors.error) },
                                colors = settingsRowColors(),
                            )
                        },
                    ),
            )
        }
    }
}

// heightDp pins the window so the whole form is in frame at 1.0x. The variants
// keep the same height on purpose: at 2x the column runs past the bottom, and
// that overflow is the finding, not an artifact of the preview.
@PreviewTest
@Preview(showBackground = true, heightDp = 1400)
@Preview(name = "fs2", showBackground = true, heightDp = 1400, fontScale = 2f)
@Preview(name = "fr", showBackground = true, heightDp = 1400, locale = "fr")
@Preview(name = "w840", showBackground = true, widthDp = 840, heightDp = 1400)
@Preview(name = "w1024", showBackground = true, widthDp = 1024, heightDp = 1400)
@Composable
fun SettingsSectionsLight() {
    PebblesTheme { SettingsSections() }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 1400, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SettingsSectionsDark() {
    PebblesTheme { SettingsSections() }
}
