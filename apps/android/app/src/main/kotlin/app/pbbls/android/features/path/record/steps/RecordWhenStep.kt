package app.pbbls.android.features.path.record.steps

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.path.create.WhenDateTime
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.theme.Spacing
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Which picker dialog is open, if any. */
private enum class WhenPicker { DATE, TIME }

/**
 * Step 1 — the moment — ports iOS `RecordWhenStep`. Seeded from the photo's
 * EXIF when it had one (M58 D7), so the common case (recording from a picture
 * taken earlier today) needs no input at all beyond confirming.
 *
 * iOS renders one `.graphical` `DatePicker` covering both components. Material's
 * `DatePicker` is **not** safe to render inline here: its year selector is a
 * `LazyVerticalGrid`, and a lazy list inside the flow's scrolling step content
 * throws at measure time. So the step shows the answer it already has as two
 * large pills and opens Material's own dialogs — which host those lazy lists
 * correctly — reusing the same two-step date-then-time flow (and the same pure
 * [WhenDateTime] conversions) that `WhenRow` uses in the form, so no timezone
 * offset ever shifts the displayed calendar day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordWhenStep(
    happenedAt: OffsetDateTime,
    onChange: (OffsetDateTime) -> Unit,
    seededFromPhoto: Boolean,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val zone = remember { ZoneId.systemDefault() }
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    var picker by remember { mutableStateOf<WhenPicker?>(null) }

    val local = happenedAt.atZoneSameInstant(zone)
    val dateLabel = local.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    val timeLabel = local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

    Column(
        modifier = modifier.fillMaxWidth().padding(top = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WhenPill(
            iconRes = R.drawable.ic_calendar,
            label = dateLabel,
            onClick = { picker = WhenPicker.DATE },
        )
        WhenPill(
            iconRes = R.drawable.ic_pebble_when,
            label = timeLabel,
            onClick = { picker = WhenPicker.TIME },
        )

        if (seededFromPhoto) {
            Row(
                modifier = Modifier.padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sparkle),
                    contentDescription = null,
                    tint = system.secondary,
                    modifier = Modifier.size(14.dp),
                )
                PebblesText(
                    text = stringResource(R.string.record_when_from_photo),
                    style = PebblesTypography.subhead,
                    color = system.secondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    when (picker) {
        WhenPicker.DATE -> {
            val dateState =
                rememberDatePickerState(
                    initialSelectedDateMillis = WhenDateTime.toUtcDateMillis(happenedAt, zone),
                )
            DatePickerDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        val millis = dateState.selectedDateMillis
                        if (millis != null) {
                            onChange(WhenDateTime.combine(millis, local.hour, local.minute, zone))
                        }
                        picker = null
                    }) {
                        PebblesText(
                            text = stringResource(R.string.action_done),
                            style = PebblesTypography.buttonLabel,
                            color = accent.primary,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { picker = null }) {
                        PebblesText(
                            text = stringResource(R.string.action_cancel),
                            style = PebblesTypography.buttonLabel,
                            color = accent.primary,
                        )
                    }
                },
            ) {
                DatePicker(state = dateState)
            }
        }

        WhenPicker.TIME -> {
            val timeState =
                rememberTimePickerState(
                    initialHour = local.hour,
                    initialMinute = local.minute,
                    is24Hour = DateFormat.is24HourFormat(context),
                )
            AlertDialog(
                onDismissRequest = { picker = null },
                containerColor = system.background,
                confirmButton = {
                    TextButton(onClick = {
                        onChange(
                            WhenDateTime.combine(
                                WhenDateTime.toUtcDateMillis(happenedAt, zone),
                                timeState.hour,
                                timeState.minute,
                                zone,
                            ),
                        )
                        picker = null
                    }) {
                        PebblesText(
                            text = stringResource(R.string.action_done),
                            style = PebblesTypography.buttonLabel,
                            color = accent.primary,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { picker = null }) {
                        PebblesText(
                            text = stringResource(R.string.action_cancel),
                            style = PebblesTypography.buttonLabel,
                            color = accent.primary,
                        )
                    }
                },
                text = { TimePicker(state = timeState) },
            )
        }

        null -> Unit
    }
}

/** One large, centred tap target carrying half the answer. */
@Composable
private fun WhenPill(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(system.muted)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = accent.primary,
            modifier = Modifier.size(20.dp),
        )
        PebblesText(
            text = label,
            style = PebblesTypography.body,
            color = system.foreground,
            textAlign = TextAlign.Center,
        )
    }
}
