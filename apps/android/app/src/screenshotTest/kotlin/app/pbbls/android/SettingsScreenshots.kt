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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.glyph.views.GlyphView
import app.pbbls.android.features.glyph.views.GlyphViewCase
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesListSection
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
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
        PebblesText(
            text = label,
            style = PebblesTypography.body,
            color = PebblesTheme.colors.system.secondary,
        )
        Spacer(Modifier.weight(1f))
        PebblesText(
            text = value,
            style = PebblesTypography.body.copy(textAlign = TextAlign.End),
            color = PebblesTheme.colors.system.foreground,
            modifier = Modifier.weight(2f),
        )
    }
}

@Composable
private fun SettingsDisclosureRow(
    label: String,
    destructive: Boolean = false,
) {
    val system = PebblesTheme.colors.system
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        PebblesText(
            text = label,
            style = PebblesTypography.body,
            color = if (destructive) PebblesDestructive else system.foreground,
        )
        Spacer(Modifier.weight(1f))
        if (!destructive) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = system.secondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SettingsSections() {
    val system = PebblesTheme.colors.system
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
                                    PebblesText(
                                        text = stringResource(R.string.settings_public_profile_toggle),
                                        style = PebblesTypography.body,
                                        color = system.foreground,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Switch(
                                        checked = true,
                                        onCheckedChange = {},
                                        colors =
                                            SwitchDefaults.colors(
                                                checkedTrackColor = PebblesTheme.colors.accent.primary,
                                            ),
                                    )
                                }
                            },
                            {
                                SettingsDisclosureRow(
                                    label = stringResource(R.string.settings_public_profile_share),
                                )
                            },
                        ),
                )
                PebblesText(
                    text = stringResource(R.string.settings_handle_footer),
                    style = PebblesTypography.subhead,
                    color = system.secondary,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm)) {
                PebblesListSection(
                    header = stringResource(R.string.settings_password_header),
                    rows =
                        listOf(
                            {
                                PebblesText(
                                    text = stringResource(R.string.settings_password_placeholder),
                                    style = PebblesTypography.body,
                                    color = system.muted,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                        ),
                )
                PebblesText(
                    text = stringResource(R.string.settings_password_footer),
                    style = PebblesTypography.subhead,
                    color = system.secondary,
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
