package app.pbbls.android.features.home

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.data.HomeShortcut
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.data.rememberTapHaptics
import app.pbbls.android.core.ui.HomeMonthSlot
import app.pbbls.android.features.home.camera.CameraUnavailableDialog
import app.pbbls.android.features.home.camera.cameraToastTime
import app.pbbls.android.features.home.quick.QuickMomentExit
import app.pbbls.android.features.home.quick.QuickMomentSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val TAG = "home"

/**
 * The Home tab (#1383 D1): the first of five, where a signed-in launch lands. Reads again on every
 * resume ([HomeViewModel.onResumed]); [reselected] fires when the user taps Home while on it, which
 * scrolls the page back to the greeting (iOS `scrollToTop`).
 *
 * [monthSlot] is the Path's month grid (D8), filled by the entry provider; a tap on one of its weeks
 * leaves the Path that focus and calls [onOpenPath] (D10).
 *
 * A quick action is stored as the last used one, then opens its flow (#1383 D7): the camera moment
 * (the system camera through `TakePicture`) and the quick moment sheet are Home's own, held by
 * [moments]; [onPebble] opens Capture chained into Complete. A quick moment saved with "Save & open"
 * calls [onOpenComplete] with its id. A saved camera moment shows a snackbar, "Moment saved · 14:32",
 * whose "Name it" calls [onNameMoment] (Edit moment); `Long`, which TalkBack extends.
 */
@Composable
fun HomeScreen(
    reselected: Flow<Unit>,
    monthSlot: HomeMonthSlot,
    onOpenPath: () -> Unit,
    onPebble: () -> Unit,
    onOpenComplete: (momentId: String) -> Unit,
    onNameMoment: (momentId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    moments: HomeMomentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val quickForm by moments.quick.sheet.collectAsStateWithLifecycle()
    val camera by moments.camera.state.collectAsStateWithLifecycle()
    val haptic = rememberTapHaptics { viewModel.hapticsOn }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), moments.camera::onResult)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    ObserveUiEffects(moments.effects) { effect ->
        when (effect) {
            is HomeMomentEffect.QuickSaved -> {
                haptic(TapHaptic.SUCCESS)
                viewModel.onMomentSaved()
                if (effect.exit == QuickMomentExit.OPEN) onOpenComplete(effect.momentId)
            }
            is HomeMomentEffect.LaunchCamera ->
                try {
                    takePicture.launch(effect.uri)
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "no activity for the camera after all", e)
                    moments.camera.onLaunchFailed()
                }
            is HomeMomentEffect.CameraLanded -> {
                viewModel.onMomentSaved()
                val time = cameraToastTime(effect.shutter, ZoneId.systemDefault(), locale)
                // A snackbar is UI, not a write: the composition's scope is the right one for it.
                scope.launch {
                    // A newer moment replaces the one still showing.
                    snackbar.currentSnackbarData?.dismiss()
                    val result =
                        snackbar.showSnackbar(
                            message = resources.getString(R.string.home_toast_saved, time),
                            actionLabel = resources.getString(R.string.home_toast_name),
                            duration = SnackbarDuration.Long,
                        )
                    if (result == SnackbarResult.ActionPerformed) onNameMoment(effect.momentId)
                }
            }
        }
    }
    LifecycleResumeEffect(viewModel, moments) {
        viewModel.onResumed()
        moments.onResumed()
        onPauseOrDispose {}
    }
    val listState = rememberLazyListState()
    LaunchedEffect(reselected, listState) {
        reselected.collect { listState.animateScrollToItem(0) }
    }
    val now by rememberMinuteClock()
    HomeContent(
        state = state,
        now = now,
        monthSlot = monthSlot,
        onWeek = { weekStart ->
            viewModel.onWeek(weekStart)
            onOpenPath()
        },
        modifier = modifier,
        listState = listState,
        onShortcut = { shortcut ->
            viewModel.onShortcutUsed(shortcut)
            when (shortcut) {
                HomeShortcut.CAMERA -> moments.onCamera()
                HomeShortcut.QUICK -> moments.quick.open()
                HomeShortcut.PEBBLE -> onPebble()
            }
        },
        hapticsOn = { viewModel.hapticsOn },
        camera = camera,
        onDismissCameraError = moments.camera::dismissError,
        snackbar = snackbar,
        onClaim = viewModel::claim,
    )
    if (camera.isUnavailable) CameraUnavailableDialog(onDismiss = moments.camera::dismissUnavailable)
    quickForm?.let { form ->
        QuickMomentSheet(
            form = form,
            onNameChange = moments.quick::setName,
            onWhenChange = moments.quick::setHappenedAt,
            onSave = moments.quick::save,
            onDismiss = moments.quick::dismiss,
        )
    }
}

/**
 * The device's local time, read again at each minute's turn, so the greeting's band flips on the hour
 * while Home is on screen (iOS reads its clock every 60 s).
 */
@Composable
private fun rememberMinuteClock(): State<LocalDateTime> =
    produceState(LocalDateTime.now()) {
        while (true) {
            val now = LocalDateTime.now()
            value = now
            delay(ChronoUnit.MILLIS.between(now, now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)).coerceAtLeast(1))
        }
    }
