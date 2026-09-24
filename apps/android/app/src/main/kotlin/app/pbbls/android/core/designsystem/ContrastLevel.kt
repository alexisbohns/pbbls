package app.pbbls.android.core.designsystem

import android.app.UiModeManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/** Which of the export's three contrast variants to draw. */
enum class ContrastLevel {
    STANDARD,
    MEDIUM,
    HIGH,
    ;

    companion object {
        /**
         * `UiModeManager.getContrast()` is a float in [-1, 1]; the system
         * setting's three stops land at 0, 0.5 and 1. Thirds split them.
         */
        fun fromSystemContrast(value: Float): ContrastLevel =
            when {
                value >= 2f / 3f -> HIGH
                value >= 1f / 3f -> MEDIUM
                else -> STANDARD
            }
    }
}

/**
 * The system contrast level, live: raising it in Settings recomposes the theme
 * without an activity restart. The API is 34+; on 33 (our minSdk) there is no
 * user contrast setting, so STANDARD is the truth there, not a fallback.
 */
@Composable
internal fun rememberContrastLevel(): ContrastLevel {
    if (LocalInspectionMode.current) return ContrastLevel.STANDARD
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return ContrastLevel.STANDARD
    val context = LocalContext.current
    val uiModeManager = remember(context) { context.getSystemService(UiModeManager::class.java) }
    var contrast by remember(uiModeManager) { mutableFloatStateOf(uiModeManager.contrast) }
    DisposableEffect(uiModeManager) {
        val listener = UiModeManager.ContrastChangeListener { contrast = it }
        uiModeManager.addContrastChangeListener(context.mainExecutor, listener)
        // The system contrast may have changed between the initial read above and
        // the listener registering; re-read now to close that gap.
        contrast = uiModeManager.contrast
        onDispose { uiModeManager.removeContrastChangeListener(listener) }
    }
    return ContrastLevel.fromSystemContrast(contrast)
}
