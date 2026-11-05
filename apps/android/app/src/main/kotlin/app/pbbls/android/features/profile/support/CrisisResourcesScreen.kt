package app.pbbls.android.features.profile.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.ListGroup
import app.pbbls.android.core.designsystem.ListGroupItemScope
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.model.CrisisLine
import app.pbbls.android.core.model.CrisisResources
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Crisis resources (#1461; iOS `CrisisResourcesSheet`, web `/settings/support`): a country picker
 * and that country's lines, each one tap from the dialer, then the worldwide directory. Reached
 * from Settings and from a high-distress pebble's success step. Plain text colours: nothing here
 * is an error and nothing is red.
 */
@Composable
fun CrisisResourcesScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CrisisResourcesViewModel = hiltViewModel(),
) {
    val selectedCode by viewModel.selectedCode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    CrisisResourcesContent(
        resources = viewModel.resources,
        selectedCode = selectedCode,
        onSelectCountry = viewModel::select,
        onDial = { dial(context, it) },
        onOpenWorldwide = { openWorldwide(context, viewModel.resources.worldwide.url) },
        onBack = onDismiss,
        modifier = modifier,
    )
}

/** Stateless layer: what `CrisisResourcesScreenshots` renders. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CrisisResourcesContent(
    resources: CrisisResources,
    selectedCode: String?,
    onSelectCountry: (String) -> Unit,
    onDial: (CrisisLine) -> Unit,
    onOpenWorldwide: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val country = resources.countries.firstOrNull { it.code == selectedCode }

    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = stringResource(R.string.settings_support_get_header),
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.profile_back_a11y),
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PebblesTheme.spacing.lg)
                    .padding(bottom = PebblesTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.crisis_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
            )

            if (country != null) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = country.name.text(locale),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.crisis_country)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier =
                            Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        resources.countries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name.text(locale)) },
                                onClick = {
                                    onSelectCountry(option.code)
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                ListGroup(
                    items =
                        country.lines.map { line ->
                            { CrisisLineItem(line = line, locale = locale, onClick = { onDial(line) }) }
                        },
                )
            }

            // The worldwide directory is a web link: shown only when it is https.
            if (resources.worldwide.url.startsWith("https://")) {
                ListGroup(
                    header = stringResource(R.string.crisis_worldwide),
                    items =
                        listOf({
                            SegmentedListItem(onClick = onOpenWorldwide, shapes = shapes, colors = colors) {
                                Text(resources.worldwide.label.text(locale))
                            }
                        }),
                )
            }

            Text(
                text = stringResource(R.string.crisis_verified_on, verifiedDate(resources.verifiedOn, locale)),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** A line: its label, then the number and the optional note under it. One tap opens the dialer. */
@Composable
private fun ListGroupItemScope.CrisisLineItem(
    line: CrisisLine,
    locale: Locale,
    onClick: () -> Unit,
) {
    val supporting = listOfNotNull(line.number, line.note?.text(locale)).joinToString(" · ")
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = colors,
        supportingContent = { Text(supporting) },
    ) {
        Text(line.label.text(locale))
    }
}

/** `2026-11-05` as the locale's medium date; the raw text when it is not an ISO date. */
private fun verifiedDate(
    iso: String,
    locale: Locale,
): String =
    try {
        LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    } catch (e: DateTimeParseException) {
        Log.w("crisis-resources", "verified_on is not an ISO date: $iso", e)
        iso
    }

/** The dialer with the number filled in, never a call placed: the person presses call. */
private fun dial(
    context: Context,
    line: CrisisLine,
) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${line.tel}".toUri()))
    } catch (e: ActivityNotFoundException) {
        Log.w("crisis-resources", "nothing on this device dials", e)
    }
}

private fun openWorldwide(
    context: Context,
    url: String,
) {
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
    } catch (e: ActivityNotFoundException) {
        Log.w("crisis-resources", "nothing on this device opens the directory", e)
    }
}
