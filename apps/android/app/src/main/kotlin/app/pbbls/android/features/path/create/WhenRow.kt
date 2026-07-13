package app.pbbls.android.features.path.create

import android.text.format.DateFormat
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import java.time.OffsetDateTime
import java.time.ZoneId

/** Which stage of the two-step [WhenRow] date/time flow is showing (D15). */
enum class WhenStage { DATE, TIME }

/**
 * The pebble's "When" form row (D15) — ports the iOS combined date/time picker
 * as Material's date-then-time two-step. The row shows the locale-formatted
 * date · time; tapping opens a `DatePickerDialog`, then (on Next) a `TimePicker`
 * in an `AlertDialog` (no `TimePickerDialog` is assumed from the BOM). Both
 * commit through the pure [WhenDateTime] conversions so no timezone offset ever
 * shifts the displayed day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhenRow(
    happenedAt: OffsetDateTime,
    onChange: (OffsetDateTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    var stage by remember { mutableStateOf<WhenStage?>(null) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    FormRow(
        leading = {
            Icon(
                painter = painterResource(R.drawable.ic_pebble_when),
                contentDescription = null,
                tint = PebblesTheme.colors.accent.primary,
                modifier = Modifier.size(28.dp),
            )
        },
        label = stringResource(R.string.create_when_label),
        value = WhenDateTime.formatRow(happenedAt, zone, locale),
        onClick = { stage = WhenStage.DATE },
        modifier = modifier,
    )

    when (stage) {
        WhenStage.DATE -> {
            val dateState =
                rememberDatePickerState(
                    initialSelectedDateMillis = WhenDateTime.toUtcDateMillis(happenedAt, zone),
                )
            DatePickerDialog(
                onDismissRequest = { stage = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDateMillis = dateState.selectedDateMillis
                            stage = WhenStage.TIME
                        },
                    ) {
                        PebblesText(
                            text = stringResource(R.string.action_next),
                            style = PebblesTypography.buttonLabel,
                            color = PebblesTheme.colors.accent.primary,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { stage = null }) {
                        PebblesText(
                            text = stringResource(R.string.action_cancel),
                            style = PebblesTypography.buttonLabel,
                            color = PebblesTheme.colors.accent.primary,
                        )
                    }
                },
            ) {
                DatePicker(state = dateState)
            }
        }
        WhenStage.TIME -> {
            val local = happenedAt.atZoneSameInstant(zone)
            val timeState =
                rememberTimePickerState(
                    initialHour = local.hour,
                    initialMinute = local.minute,
                    is24Hour = DateFormat.is24HourFormat(context),
                )
            AlertDialog(
                onDismissRequest = { stage = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val dateMillis = pendingDateMillis ?: WhenDateTime.toUtcDateMillis(happenedAt, zone)
                            onChange(WhenDateTime.combine(dateMillis, timeState.hour, timeState.minute, zone))
                            stage = null
                        },
                    ) {
                        PebblesText(
                            text = stringResource(R.string.action_done),
                            style = PebblesTypography.buttonLabel,
                            color = PebblesTheme.colors.accent.primary,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { stage = null }) {
                        PebblesText(
                            text = stringResource(R.string.action_cancel),
                            style = PebblesTypography.buttonLabel,
                            color = PebblesTheme.colors.accent.primary,
                        )
                    }
                },
                text = { TimePicker(state = timeState) },
            )
        }
        null -> Unit
    }
}
