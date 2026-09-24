package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesListSection
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTopBarTextButton
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase
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
 * What that buys and what it does not: every label/value row here is laid out
 * the way `SettingsScreen` lays it out — the `weight(1f)` spacer against a
 * `weight(2f)` value, the `Switch` trailing its label, the destructive row — so
 * a control that clips its own text at `fontScale = 2f`, or a French label that
 * squeezes its value column to nothing, shows up. A regression in the screen's
 * own scroll column, its IME padding or its dirty-state top bar does not. That
 * gap closes when Settings grows a stateless content layer the way `PathScreen`
 * has `PathContent`; #848/#849 own that screen's architecture, so it is not
 * pulled forward here.
 */
@Composable
private fun SettingsLabelValueRow(
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(2f),
        )
    }
}

@Composable
private fun SettingsDisclosureRow(
    label: String,
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) colors.error else colors.onSurface,
        )
        Spacer(Modifier.weight(1f))
        if (!destructive) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SettingsWallpaperColorsRow(checked: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.settings_wallpaper_colors_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.settings_wallpaper_colors_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
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
                rows =
                    listOf(
                        { SettingsWallpaperColorsRow(checked = true) },
                        { SettingsWallpaperColorsRow(checked = false) },
                    ),
            )

            PebblesListSection(
                header = stringResource(R.string.settings_informations_header),
                rows =
                    listOf(
                        {
                            SettingsLabelValueRow(
                                label = stringResource(R.string.settings_name_label),
                                value = "Alexis",
                            )
                        },
                        {
                            SettingsLabelValueRow(
                                label = stringResource(R.string.settings_email_label),
                                value = "alexis@example.com",
                            )
                        },
                    ),
            )

            Column(verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm)) {
                PebblesListSection(
                    header = stringResource(R.string.settings_public_profile_header),
                    rows =
                        listOf(
                            {
                                SettingsLabelValueRow(
                                    label = stringResource(R.string.settings_handle_label),
                                    value = "@alexis",
                                )
                            },
                            {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_public_profile_toggle),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.onSurface,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Switch(checked = true, onCheckedChange = {})
                                }
                            },
                            {
                                SettingsDisclosureRow(
                                    label = stringResource(R.string.settings_public_profile_share),
                                )
                            },
                        ),
                )
                Text(
                    text = stringResource(R.string.settings_handle_footer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm)) {
                PebblesListSection(
                    header = stringResource(R.string.settings_password_header),
                    rows =
                        listOf(
                            {
                                Text(
                                    text = stringResource(R.string.settings_password_placeholder),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                        ),
                )
                Text(
                    text = stringResource(R.string.settings_password_footer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            PebblesListSection(
                header = stringResource(R.string.settings_legal_header),
                rows =
                    listOf(
                        { SettingsDisclosureRow(label = stringResource(R.string.auth_consent_terms_link)) },
                        { SettingsDisclosureRow(label = stringResource(R.string.auth_consent_privacy_link)) },
                    ),
            )

            PebblesListSection(
                header = stringResource(R.string.settings_account_header),
                rows =
                    listOf(
                        {
                            SettingsDisclosureRow(
                                label = stringResource(R.string.settings_delete_account),
                                destructive = true,
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
