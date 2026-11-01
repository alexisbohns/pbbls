package app.pbbls.android.features.home.quick

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesActionHeight
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.Spacing
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Which picker dialog is open, if any. */
private enum class QuickPicker { DATE, TIME }

/**
 * The quick moment as a stock `ModalBottomSheet` (#1383 D7; iOS `QuickMomentSheet` at its medium
 * detent), fully expanded so the two saves sit under the field. Every change and save goes to the
 * owner ([QuickMoment]); the sheet only draws [form] and reports [onDismiss] for a swipe, a scrim
 * tap or back. Saving closes it from the owner's side ([QuickMoment.sheet] turns null).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMomentSheet(
    form: QuickMomentUiState,
    onNameChange: (String) -> Unit,
    onWhenChange: (OffsetDateTime) -> Unit,
    onSave: (QuickMomentExit) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        QuickMomentForm(
            form = form,
            onNameChange = onNameChange,
            onWhenChange = onWhenChange,
            onSave = onSave,
            modifier = Modifier.padding(bottom = Spacing.lg),
        )
    }
}

/**
 * The sheet's content without the sheet (iOS `QuickMomentForm`), so the previews pin it: the
 * title, the name field (focused on open when [autoFocus], clamped by the owner), when as two pills
 * opening Material's own date and time dialogs (as Capture's when step does), the error line, then
 * "Save & open" (filled) over "Save & close" (outlined), both off while the name is blank or a save
 * runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMomentForm(
    form: QuickMomentUiState,
    onNameChange: (String) -> Unit,
    onWhenChange: (OffsetDateTime) -> Unit,
    onSave: (QuickMomentExit) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(focus) { focus.requestFocus() }
    var picker by rememberSaveable { mutableStateOf<QuickPicker?>(null) }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.home_quick_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = form.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            placeholder = {
                Text(
                    text = stringResource(R.string.home_quick_name),
                    style = PebblesTheme.hand.nameInputHand,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            textStyle = PebblesTheme.hand.nameInputHand.copy(textAlign = TextAlign.Center, color = colors.onSurface),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        )
        WhenRow(happenedAt = form.happenedAt, onDate = { picker = QuickPicker.DATE }, onTime = { picker = QuickPicker.TIME })
        form.errorRes?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.error,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PebblesPrimaryButton(
                text = stringResource(R.string.home_quick_save_open),
                onClick = { onSave(QuickMomentExit.OPEN) },
                enabled = form.canSave,
                isLoading = form.isSaving,
            )
            OutlinedButton(
                onClick = { onSave(QuickMomentExit.CLOSE) },
                enabled = form.canSave,
                modifier = Modifier.fillMaxWidth().heightIn(min = PebblesActionHeight),
            ) {
                Text(text = stringResource(R.string.home_quick_save_close), style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    QuickPickerDialog(picker = picker, happenedAt = form.happenedAt, onChange = onWhenChange, onClose = { picker = null })
}

/** "When", then the moment's date and time as two pills (iOS's compact `DatePicker`). */
@Composable
private fun WhenRow(
    happenedAt: OffsetDateTime,
    onDate: () -> Unit,
    onTime: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val local = happenedAt.atZoneSameInstant(ZoneId.systemDefault())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_quick_when),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onDate) {
            Text(local.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)))
        }
        FilledTonalButton(onClick = onTime) {
            Text(local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)))
        }
    }
}

/**
 * Material's date or time dialog over the sheet. The date picker speaks UTC-midnight millis and the
 * moment is a wall-clock time in the device's zone, so the day is projected through UTC both ways
 * and never shifts with the offset (Capture's `WhenDateTime`, which `features/path` keeps).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickPickerDialog(
    picker: QuickPicker?,
    happenedAt: OffsetDateTime,
    onChange: (OffsetDateTime) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val zone = remember { ZoneId.systemDefault() }
    val local = happenedAt.atZoneSameInstant(zone)
    val dateMillis =
        local
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    val done: @Composable (() -> Unit) -> Unit = { onClick ->
        TextButton(onClick = onClick) { Text(stringResource(R.string.action_done), color = colors.primary) }
    }
    val cancel: @Composable () -> Unit = {
        TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel), color = colors.primary) }
    }
    when (picker) {
        QuickPicker.DATE -> {
            val state = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
            DatePickerDialog(
                onDismissRequest = onClose,
                confirmButton = {
                    done {
                        state.selectedDateMillis?.let { millis ->
                            val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onChange(day.atTime(local.toLocalTime()).atZone(zone).toOffsetDateTime())
                        }
                        onClose()
                    }
                },
                dismissButton = cancel,
            ) { DatePicker(state = state) }
        }
        QuickPicker.TIME -> {
            val state =
                rememberTimePickerState(
                    initialHour = local.hour,
                    initialMinute = local.minute,
                    is24Hour = DateFormat.is24HourFormat(LocalContext.current),
                )
            AlertDialog(
                onDismissRequest = onClose,
                containerColor = colors.surfaceContainerHigh,
                confirmButton = {
                    done {
                        onChange(
                            local
                                .toLocalDate()
                                .atTime(state.hour, state.minute)
                                .atZone(zone)
                                .toOffsetDateTime(),
                        )
                        onClose()
                    }
                },
                dismissButton = cancel,
                text = { TimePicker(state = state) },
            )
        }
        null -> Unit
    }
}
