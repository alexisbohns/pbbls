package app.pbbls.android.features.report

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.ReportServicing
import app.pbbls.android.core.data.reportErrorMessage
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.model.ReportReason
import app.pbbls.android.core.model.ReportTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "report"

/** `content_reports.detail`'s CHECK, in code points (Postgres `length()`). */
internal const val REPORT_DETAIL_LIMIT = 1_000

/** Where a report is. Mirrors iOS `ReportSheetModel.Phase`. */
sealed interface ReportPhase {
    data object Editing : ReportPhase

    data object Sending : ReportPhase

    /** [didBlock]: the connection was also removed and blocked. */
    data class Sent(
        val didBlock: Boolean,
    ) : ReportPhase

    data class Failed(
        @StringRes val messageRes: Int,
    ) : ReportPhase
}

/**
 * The report screen's state (#839, ports iOS `ReportSheetModel`). A
 * `PebblesKey.Report` entry, so it is scoped to that entry and a second report
 * gets a fresh instance.
 *
 * The form fields are snapshot state, read synchronously by the text field;
 * the phase is a flow. The send runs under `NonCancellable`: with the block on
 * it severs a connection for both sides, so leaving mid-request must not drop
 * the outcome.
 */
@HiltViewModel
class ReportViewModel
    @Inject
    constructor(
        private val service: ReportServicing,
    ) : ViewModel() {
        var target: ReportTarget? = null
            private set

        var reason by mutableStateOf<ReportReason?>(null)

        var detail by mutableStateOf("")
            private set

        /** Only read for a connection; on by default (design decision 3). */
        var removeAndBlock by mutableStateOf(true)

        private val _phase = MutableStateFlow<ReportPhase>(ReportPhase.Editing)
        val phase: StateFlow<ReportPhase> = _phase.asStateFlow()

        val canSend: Boolean
            get() = reason != null && _phase.value.let { it !is ReportPhase.Sending && it !is ReportPhase.Sent }

        fun start(target: ReportTarget) {
            if (this.target == target) return
            this.target = target
        }

        /** Caps at [REPORT_DETAIL_LIMIT] code points, never splitting a surrogate pair. */
        fun onDetailChange(text: String) {
            detail =
                if (text.codePointCount(0, text.length) <= REPORT_DETAIL_LIMIT) {
                    text
                } else {
                    text.substring(0, text.offsetByCodePoints(0, REPORT_DETAIL_LIMIT))
                }
        }

        fun submit() {
            val target = target ?: return
            val reason = reason ?: return
            if (!canSend) return
            _phase.value = ReportPhase.Sending
            val trimmed = detail.trim().ifEmpty { null }
            val block = target.offersBlock && removeAndBlock
            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable { service.send(target, reason, trimmed, block) }
                        .fold(
                            onSuccess = { _phase.value = ReportPhase.Sent(didBlock = block) },
                            onFailure = {
                                Log.e(TAG, "report failed", it)
                                _phase.value = ReportPhase.Failed(reportErrorMessage(it.toDataError()))
                            },
                        )
                }
            }
        }
    }
