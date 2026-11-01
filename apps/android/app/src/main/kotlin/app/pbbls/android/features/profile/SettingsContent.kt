package app.pbbls.android.features.profile

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.data.DeletionState
import app.pbbls.android.core.data.HomeShortcutsLayout
import app.pbbls.android.core.designsystem.ListGroup
import app.pbbls.android.core.designsystem.ListGroupItemScope
import app.pbbls.android.core.designsystem.ListGroupPanel
import app.pbbls.android.core.designsystem.ListGroupSwitchItem
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTopBarTextButton
import app.pbbls.android.core.designsystem.openSettingsLink
import app.pbbls.android.core.model.SettingsLinkSection
import app.pbbls.android.core.model.groupSettingsLinks
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The device-local choices Settings shows beside the form (wallpaper colour, haptics, Home's
 * quick actions). Each saves as it is switched and never waits for Save.
 */
@Immutable
data class SettingsDeviceChoices(
    val useWallpaperColors: Boolean = true,
    val hapticsOn: Boolean = true,
    val shortcutsLayout: HomeShortcutsLayout = HomeShortcutsLayout.ROW,
    val shortcutsStones: Boolean = true,
)

/** What the user can do on Settings; every default is a no-op, for previews. */
@Immutable
class SettingsActions(
    val onBack: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onOpenGlyphPicker: () -> Unit = {},
    val onUseWallpaperColorsChange: (Boolean) -> Unit = {},
    val onShortcutsLayoutChange: (HomeShortcutsLayout) -> Unit = {},
    val onShortcutsStonesChange: (Boolean) -> Unit = {},
    val onHapticsChange: (Boolean) -> Unit = {},
    val onDisplayNameChange: (String) -> Unit = {},
    val onHandleChange: (String) -> Unit = {},
    val onPublicProfileChange: (Boolean) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onWithdrawConsent: () -> Unit = {},
    val onSignOutEverywhere: () -> Unit = {},
    val onDeleteAccount: () -> Unit = {},
    val onLogOut: () -> Unit = {},
)

/**
 * Settings' stateless content layer (#849): the top bar, the spinner, the load error and the
 * form, from [state] and [device]. This is what `SettingsScreenshots` renders; the dialogs and
 * the glyph picker stay on [SettingsScreen].
 *
 * The page is Android's own Settings (Android 16 "App info"), not the iOS grouped form: each
 * section is a [ListGroup] of M3 Expressive segmented list items under a `primary` header,
 * toggles are stock switches on rows that are the one TalkBack control, and the text fields and
 * the Row / Bento choice keep their stock forms on the group's container. Log out closes the
 * page in a group of its own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    device: SettingsDeviceChoices,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    PebblesScreen(
        modifier = modifier.background(colors.surface),
        topBar = {
            PebblesTopBar(
                title = stringResource(R.string.settings_title),
                leading = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.profile_back_a11y),
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                trailing = {
                    if (state.isSaving) {
                        LoadingIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        PebblesTopBarTextButton(
                            text = stringResource(R.string.action_save),
                            onClick = actions.onSave,
                            enabled = state.isDirty,
                            color = if (state.isDirty) colors.onSurfaceVariant else colors.onSurface.copy(alpha = 0.38f),
                        )
                    }
                },
            )
        },
    ) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@PebblesScreen
        }

        val loadErrorRes = state.loadErrorRes
        if (loadErrorRes != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(loadErrorRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.error,
                    textAlign = TextAlign.Center,
                )
            }
            return@PebblesScreen
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xl),
        ) {
            // Header glyph — tap opens the picker (the SettingsSheet 120pt header).
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val hasStrokes = !state.currentStrokes.isNullOrEmpty()
                GlyphView(
                    case = if (hasStrokes) GlyphViewCase.PROFILE else GlyphViewCase.CARVE,
                    strokes = state.currentStrokes,
                    side = 120.dp,
                    modifier = Modifier.clickable(onClick = actions.onOpenGlyphPicker),
                )
            }
            SettingsDeviceSections(device = device, actions = actions)
            SettingsIdentitySections(state = state, actions = actions)
            SettingsSignInSections(state = state, actions = actions)
            SettingsAccountSections(state = state, actions = actions)
        }
    }
}

/*
 * The body comes in four runs of sections, each emitting its groups into the page's column, so
 * the previews can render one run at a time: the whole page is taller than the renderer's frame.
 */

/** Appearance, Home and Haptics: the device-local choices. */
@Composable
internal fun SettingsDeviceSections(
    device: SettingsDeviceChoices,
    actions: SettingsActions,
) {
    ListGroup(
        header = stringResource(R.string.settings_appearance_section),
        items =
            listOf {
                ListGroupSwitchItem(
                    headline = stringResource(R.string.settings_wallpaper_colors_title),
                    supporting = stringResource(R.string.settings_wallpaper_colors_body),
                    checked = device.useWallpaperColors,
                    onCheckedChange = actions.onUseWallpaperColorsChange,
                )
            },
    )

    SettingsHomeSection(
        layout = device.shortcutsLayout,
        stones = device.shortcutsStones,
        onLayoutChange = actions.onShortcutsLayoutChange,
        onStonesChange = actions.onShortcutsStonesChange,
    )

    // Pebbles' own haptics switch (#1242 D23; iOS `hapticsSection`): device-local like the
    // wallpaper row above, so it saves as it is switched and never waits for Save.
    ListGroup(
        header = stringResource(R.string.settings_haptics_section),
        items =
            listOf {
                ListGroupSwitchItem(
                    headline = stringResource(R.string.settings_haptics_vibrations),
                    supporting = stringResource(R.string.settings_haptics_footer),
                    checked = device.hapticsOn,
                    onCheckedChange = actions.onHapticsChange,
                )
            },
    )
}

/** Informations and Public profile. */
@Composable
internal fun SettingsIdentitySections(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    val context = LocalContext.current

    // Editable fields keep their stock outlined form (#854), each on a panel of the group.
    ListGroup(
        header = stringResource(R.string.settings_informations_header),
        items =
            listOf(
                {
                    ListGroupPanel {
                        OutlinedTextField(
                            value = state.form.displayName,
                            onValueChange = actions.onDisplayNameChange,
                            label = { Text(stringResource(R.string.settings_name_label)) },
                            placeholder = { Text(stringResource(R.string.settings_name_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                {
                    ListGroupPanel {
                        OutlinedTextField(
                            value = state.initial.email ?: "—",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.settings_email_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
            ),
    )

    // Public profile (M50): claim a handle, opt in, then share the link.
    val canGoPublic = state.initial.handle != null
    ListGroup(
        header = stringResource(R.string.settings_public_profile_header),
        items =
            buildList {
                add {
                    ListGroupPanel {
                        OutlinedTextField(
                            value = state.form.handle,
                            onValueChange = actions.onHandleChange,
                            label = { Text(stringResource(R.string.settings_handle_label)) },
                            placeholder = { Text(stringResource(R.string.settings_handle_placeholder)) },
                            prefix = { Text("@") },
                            isError = state.handleErrorRes != null,
                            supportingText = {
                                Text(
                                    state.handleErrorRes?.let { stringResource(it) }
                                        ?: if (state.initial.handle == null) {
                                            stringResource(R.string.settings_public_profile_needs_handle)
                                        } else {
                                            stringResource(R.string.settings_handle_footer)
                                        },
                                )
                            },
                            singleLine = true,
                            keyboardOptions =
                                KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                add {
                    // No handle on the server yet: the whole row is disabled, not merely quiet.
                    ListGroupSwitchItem(
                        headline = stringResource(R.string.settings_public_profile_toggle),
                        checked = state.form.isPublicProfile,
                        onCheckedChange = actions.onPublicProfileChange,
                        enabled = canGoPublic,
                    )
                }
                state.shareUrl?.let { shareUrl ->
                    add {
                        SettingsLinkItem(
                            text = stringResource(R.string.settings_public_profile_share),
                            onClick = { sharePublicProfile(context, shareUrl) },
                        )
                    }
                }
            },
    )
}

/** The password (or, for an SSO account, its providers), then the save error if there is one. */
@Composable
internal fun SettingsSignInSections(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    if (state.initial.providers.isNotEmpty()) {
        ListGroup(
            header = stringResource(R.string.settings_providers_header),
            items =
                state.initial.providers.map { provider ->
                    {
                        // Brand names render verbatim — never localized.
                        SegmentedListItem(shapes = shapes, colors = colors) { Text(provider) }
                    }
                },
        )
    } else {
        ListGroup(
            header = stringResource(R.string.settings_password_header),
            items =
                listOf {
                    ListGroupPanel {
                        OutlinedTextField(
                            value = state.form.newPassword,
                            onValueChange = actions.onPasswordChange,
                            label = { Text(stringResource(R.string.settings_password_placeholder)) },
                            supportingText = { Text(stringResource(R.string.settings_password_footer)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
        )
    }

    if (state.didSaveFail) {
        Text(
            text = stringResource(R.string.settings_save_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = PebblesTheme.spacing.lg),
        )
    }
}

/** The links (Legal, About, Support), Consent, Account, and Log out on its own at the end. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsAccountSections(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    val context = LocalContext.current

    // The admin-edited links (#1489): Legal, About and Support, each shown when it has a row. The
    // titles follow the app's locale, French falling back to English (like the guides).
    val locale = LocalConfiguration.current.locales[0]
    groupSettingsLinks(state.links).forEach { (section, links) ->
        ListGroup(
            header = stringResource(section.headerRes),
            items =
                links.map { link ->
                    {
                        SettingsLinkItem(
                            text = link.title(locale),
                            onClick = { openSettingsLink(context, link) },
                        )
                    }
                },
        )
    }

    // Art. 9 consent and the Art. 7(3) right to take it back (#972).
    // Health data only: terms, privacy and age assurance cannot be
    // withdrawn, so they never appear here as controls.
    ListGroup(
        header = stringResource(R.string.settings_consent_header),
        items =
            listOf {
                HealthConsentRow(
                    status = state.healthConsent,
                    canWithdraw = state.deletion == DeletionState.IDLE,
                    onWithdraw = actions.onWithdrawConsent,
                )
            },
    )

    // Store-mandated account deletion entry (Play hard blocker;
    // parity with iOS Settings → Account).
    ListGroup(
        header = stringResource(R.string.settings_account_header),
        items =
            listOf(
                {
                    val isWorking = state.signOutEverywhere == SignOutEverywhereState.WORKING
                    SegmentedListItem(
                        onClick = actions.onSignOutEverywhere,
                        shapes = shapes,
                        colors = colors,
                        enabled = !isWorking,
                        trailingContent =
                            if (isWorking) {
                                { LoadingIndicator(modifier = Modifier.size(24.dp)) }
                            } else {
                                null
                            },
                    ) {
                        Text(stringResource(R.string.settings_sign_out_everywhere))
                    }
                },
                {
                    // The one destructive row keeps the error role (orange, never red).
                    val isDeleting = state.deletion == DeletionState.DELETING
                    val error = MaterialTheme.colorScheme.error
                    SegmentedListItem(
                        onClick = actions.onDeleteAccount,
                        shapes = shapes,
                        colors = colors,
                        enabled = !isDeleting,
                        trailingContent =
                            if (isDeleting) {
                                { LoadingIndicator(color = error, modifier = Modifier.size(24.dp)) }
                            } else {
                                null
                            },
                    ) {
                        Text(stringResource(R.string.settings_delete_account), color = error)
                    }
                },
            ),
    )

    // Log out (#1383 D14): this device only, no confirmation, as it was on You. A plain row in a
    // group of its own, at the end, in the ordinary text colour.
    ListGroup(
        items =
            listOf {
                SegmentedListItem(onClick = actions.onLogOut, shapes = shapes, colors = colors) {
                    Text(stringResource(R.string.settings_log_out))
                }
            },
    )
}

/** Each links section's header (#1489; iOS `SettingsSheet`). */
private val SettingsLinkSection.headerRes: Int
    @StringRes get() =
        when (this) {
            SettingsLinkSection.LEGAL -> R.string.settings_legal_header
            SettingsLinkSection.ABOUT -> R.string.settings_about_header
            SettingsLinkSection.SUPPORT -> R.string.settings_support_header
        }

/** A row that opens something (a document, the share sheet): a plain headline, one target. */
@Composable
private fun ListGroupItemScope.SettingsLinkItem(
    text: String,
    onClick: () -> Unit,
) {
    SegmentedListItem(onClick = onClick, shapes = shapes, colors = colors) {
        Text(text)
    }
}

/**
 * Settings → Home (#1383 D6, #1376; iOS `homeSection`): the quick actions as a row or a bento, and
 * as pebble shapes or plain tiles. Device-local like the haptics switch, so each choice saves as it
 * is made and never waits for Save.
 */
@Composable
internal fun SettingsHomeSection(
    layout: HomeShortcutsLayout,
    stones: Boolean,
    onLayoutChange: (HomeShortcutsLayout) -> Unit,
    onStonesChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layouts = HomeShortcutsLayout.entries
    ListGroup(
        header = stringResource(R.string.settings_home_section),
        modifier = modifier,
        items =
            listOf(
                {
                    SegmentedListItem(
                        shapes = shapes,
                        colors = colors,
                        supportingContent = {
                            Column {
                                Text(stringResource(R.string.settings_home_footer))
                                SingleChoiceSegmentedButtonRow(
                                    modifier = Modifier.fillMaxWidth().padding(top = PebblesTheme.spacing.md),
                                ) {
                                    layouts.forEachIndexed { index, option ->
                                        SegmentedButton(
                                            selected = option == layout,
                                            onClick = { onLayoutChange(option) },
                                            shape = SegmentedButtonDefaults.itemShape(index, layouts.size),
                                        ) {
                                            Text(stringResource(option.labelRes))
                                        }
                                    }
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.settings_home_quick_actions))
                    }
                },
                {
                    ListGroupSwitchItem(
                        headline = stringResource(R.string.settings_home_stones),
                        checked = stones,
                        onCheckedChange = onStonesChange,
                    )
                },
            ),
    )
}

private val HomeShortcutsLayout.labelRes: Int
    get() =
        when (this) {
            HomeShortcutsLayout.ROW -> R.string.settings_home_layout_row
            HomeShortcutsLayout.BENTO -> R.string.settings_home_layout_bento
        }

/**
 * The health-data consent row (#972, mirrors web's `ConsentSection`): what was
 * given, when, and against which policy version, with Withdraw as the one
 * action. Withdraw opens the deletion dialog, never a toggle: the consent is
 * the lawful basis for the journal, so taking it back closes the account.
 */
@Composable
internal fun ListGroupItemScope.HealthConsentRow(
    status: HealthConsentStatus,
    canWithdraw: Boolean,
    onWithdraw: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val supporting =
        when (status) {
            HealthConsentStatus.Loading -> null
            is HealthConsentStatus.Given -> {
                val date =
                    remember(status.consent.grantedAt, locale) {
                        status.consent.grantedAt
                            .atZoneSameInstant(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
                    }
                stringResource(R.string.settings_consent_given_on, date, status.consent.documentVersion)
            }
            HealthConsentStatus.NotRecorded -> stringResource(R.string.settings_consent_not_recorded)
            HealthConsentStatus.Unavailable -> stringResource(R.string.settings_consent_unavailable)
        }
    SegmentedListItem(
        shapes = shapes,
        colors = colors,
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent =
            if (status is HealthConsentStatus.Given) {
                {
                    TextButton(onClick = onWithdraw, enabled = canWithdraw) {
                        Text(stringResource(R.string.settings_consent_withdraw))
                    }
                }
            } else {
                null
            },
    ) {
        Text(stringResource(R.string.settings_consent_health_data))
    }
}

/**
 * Android's share affordance for the public profile — the app's first
 * `ACTION_SEND` chooser (iOS uses `ShareLink`).
 */
private fun sharePublicProfile(
    context: Context,
    url: String,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
    context.startActivity(Intent.createChooser(intent, null))
}
