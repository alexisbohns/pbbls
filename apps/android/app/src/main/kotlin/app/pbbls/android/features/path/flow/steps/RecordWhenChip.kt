package app.pbbls.android.features.path.flow.steps

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.features.path.capture.WhenSource
import app.pbbls.android.features.path.create.WhenDateTime
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The moment page's when chip (record-flow D4), ports iOS `RecordWhenChip`: "Today, 21:10 v" or
 * "From your photo · Sun 3 Nov v", opening the calendar and the time in a sheet. The EXIF seed and its
 * label stay; a user edit always wins ([WhenSource]).
 *
 * The chip reads "When" and then its value to TalkBack, as one control.
 */
@Composable
fun RecordWhenChip(
    happenedAt: OffsetDateTime,
    source: WhenSource,
    onChange: (OffsetDateTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val zone = remember { ZoneId.systemDefault() }
    val locale = LocalConfiguration.current.locales[0]
    var isPresented by remember { mutableStateOf(false) }

    val todayLabel = stringResource(R.string.capture_when_today)
    val yesterdayLabel = stringResource(R.string.capture_when_yesterday)
    val resources = LocalResources.current
    val label =
        whenLabel(
            happenedAt = happenedAt,
            today = LocalDate.now(zone),
            zone = zone,
            locale = locale,
            source = source,
            todayLabel = todayLabel,
            yesterdayLabel = yesterdayLabel,
            fromPhoto = { resources.getString(R.string.capture_when_from_photo, it) },
            weekdayAndDate = { weekdayAndDate(it, locale) },
        )
    val whenA11y = stringResource(R.string.capture_when_a11y)

    AssistChip(
        onClick = { isPresented = true },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            PbblsIconImage(
                icon = if (source == WhenSource.PHOTO) PbblsIcon.SPARKLE else PbblsIcon.CALENDAR_DAY,
                size = PbblsIconSize.INLINE,
                contentDescription = null,
                tint = colors.onSecondaryContainer,
            )
        },
        trailingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.rotate(CHEVRON_DOWN_DEGREES),
            )
        },
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = colors.secondaryContainer,
                labelColor = colors.onSecondaryContainer,
                trailingIconContentColor = colors.onSecondaryContainer,
            ),
        border = null,
        modifier =
            modifier.semantics(mergeDescendants = true) {
                contentDescription = whenA11y
                stateDescription = label
            },
    )

    if (isPresented) {
        RecordWhenSheet(
            happenedAt = happenedAt,
            zone = zone,
            onDismiss = { isPresented = false },
            onDone = {
                onChange(it)
                isPresented = false
            },
        )
    }
}

private const val CHEVRON_DOWN_DEGREES = 90f

/**
 * The chip's label (iOS `RecordWhenChip.label`): today and yesterday keep the time, another day shows
 * the weekday and the date, then the time in the active locale; from a photo it is wrapped as
 * "From your photo · ...". Pure: [today], [zone] and [locale] arrive as parameters so the test pins a
 * clock, and no locale is ever fixed here.
 */
internal fun whenLabel(
    happenedAt: OffsetDateTime,
    today: LocalDate,
    zone: ZoneId,
    locale: Locale,
    source: WhenSource,
    todayLabel: String,
    yesterdayLabel: String,
    fromPhoto: (String) -> String,
    weekdayAndDate: (ZonedDateTime) -> String,
): String {
    val local = happenedAt.atZoneSameInstant(zone)
    val day =
        when (local.toLocalDate()) {
            today -> todayLabel
            today.minusDays(1) -> yesterdayLabel
            else -> weekdayAndDate(local)
        }
    val time = local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    val whenText = "$day, $time"
    return if (source == WhenSource.PHOTO) fromPhoto(whenText) else whenText
}

/** "Wed, Oct 14" in en, "mer. 14 oct." in fr: the platform's own best pattern for the weekday, day and month. */
internal fun weekdayAndDate(
    moment: ZonedDateTime,
    locale: Locale,
): String =
    runCatching {
        moment.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, WEEKDAY_DATE_SKELETON), locale))
    }.getOrElse { moment.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }

private const val WEEKDAY_DATE_SKELETON = "EEEdMMM"

/**
 * The sheet behind the chip: the calendar and the time. Done writes both; back, the scrim and a drag
 * leave the date as it was. A full-height sheet: the calendar needs the room, and with no half detent
 * the stock back has nothing to collapse. Its back is handled inside it (a handler around a sheet never
 * fires: the sheet is its own window), like the souls chooser's.
 *
 * Material's `DatePicker` is **not** safe to render inline in a scrolling step: its year selector is a
 * lazy grid, which throws at measure time inside one (see `RecordWhenStep`'s history). It lives here,
 * in a sheet of its own, and the time sits beside it as a row that opens Material's own dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordWhenSheet(
    happenedAt: OffsetDateTime,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onDone: (OffsetDateTime) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hide: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    val local = remember(happenedAt) { happenedAt.atZoneSameInstant(zone) }
    val dateState =
        rememberDatePickerState(initialSelectedDateMillis = WhenDateTime.toUtcDateMillis(happenedAt, zone))
    var hour by remember { mutableIntStateOf(local.hour) }
    var minute by remember { mutableIntStateOf(local.minute) }
    var isTimeOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val locale = LocalConfiguration.current.locales[0]

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerLow,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(state = backState) { hide() }
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.capture_when_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
            )
            DatePicker(
                state = dateState,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(containerColor = colors.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedButton(
                    onClick = { isTimeOpen = true },
                    modifier = Modifier.heightIn(min = TARGET_MIN),
                ) {
                    PbblsIconImage(
                        icon = PbblsIcon.CALENDAR_DAY,
                        size = PbblsIconSize.INLINE,
                        contentDescription = null,
                        tint = colors.primary,
                    )
                    Text(
                        text =
                            local
                                .withHour(
                                    hour,
                                ).withMinute(minute)
                                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
            PebblesPrimaryButton(
                text = stringResource(R.string.souls_done),
                onClick = {
                    val millis = dateState.selectedDateMillis ?: WhenDateTime.toUtcDateMillis(happenedAt, zone)
                    onDone(WhenDateTime.combine(millis, hour, minute, zone))
                },
                modifier = Modifier.padding(bottom = Spacing.lg),
            )
        }
    }

    if (isTimeOpen) {
        val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = is24Hour)
        AlertDialog(
            onDismissRequest = { isTimeOpen = false },
            containerColor = colors.surfaceContainerHigh,
            confirmButton = {
                TextButton(onClick = {
                    hour = timeState.hour
                    minute = timeState.minute
                    isTimeOpen = false
                }) {
                    Text(
                        text = stringResource(R.string.souls_done),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { isTimeOpen = false }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                    )
                }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

private val TARGET_MIN = 48.dp
