package app.pbbls.android.core.data

import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.model.OffsetDateTimeSerializer
import app.pbbls.android.core.model.ReportReason
import app.pbbls.android.core.model.ReportTarget
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** The report seam (#839) — what [app.pbbls.android.features.report.ReportViewModel] is tested against. */
interface ReportServicing {
    suspend fun send(
        target: ReportTarget,
        reason: ReportReason,
        detail: String?,
        removeAndBlock: Boolean,
    ): ReportReceipt
}

/**
 * Files a report (#839, ports iOS `ReportService`). A glyph goes through
 * `report_content`; a person through `report_person`, which resolves them from
 * the connection or invite on the server. Both return scalar `jsonb`, so the
 * receipt uses `decodeAs()`. Filing twice is a success: both RPCs hand back the
 * standing open report.
 */
@Singleton
class ReportService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) : ReportServicing {
        override suspend fun send(
            target: ReportTarget,
            reason: ReportReason,
            detail: String?,
            removeAndBlock: Boolean,
        ): ReportReceipt {
            val call = reportCall(target, reason, detail, removeAndBlock)
            return supabase.client.postgrest
                .rpc(call.function, call.params)
                .decodeAs()
        }
    }

/** Which RPC a report goes through, with its wire params. Pure; JVM-tested. */
internal data class ReportCall(
    val function: String,
    val params: JsonObject,
)

/** A null [detail] is omitted so the RPC's default applies. An invite never blocks. */
internal fun reportCall(
    target: ReportTarget,
    reason: ReportReason,
    detail: String?,
    removeAndBlock: Boolean,
): ReportCall =
    when (target) {
        is ReportTarget.Glyph ->
            ReportCall(
                "report_content",
                buildJsonObject {
                    put("p_target_kind", "glyph")
                    put("p_target_id", target.glyphId)
                    put("p_reason", reason.slug)
                    detail?.let { put("p_detail", it) }
                },
            )

        is ReportTarget.Connection ->
            ReportCall(
                "report_person",
                buildJsonObject {
                    put("p_reason", reason.slug)
                    detail?.let { put("p_detail", it) }
                    put("p_connection_id", target.connectionId)
                    put("p_remove_and_block", removeAndBlock)
                },
            )

        is ReportTarget.Invite ->
            ReportCall(
                "report_person",
                buildJsonObject {
                    put("p_reason", reason.slug)
                    detail?.let { put("p_detail", it) }
                    put("p_invite_token", target.token)
                    put("p_remove_and_block", false)
                },
            )
    }

/** `{id, status, created_at}` — the projection both report RPCs return. */
@Serializable
data class ReportReceipt(
    val id: String,
    val status: String,
    @Serializable(with = OffsetDateTimeSerializer::class)
    @SerialName("created_at") val createdAt: OffsetDateTime,
)

/**
 * A report failure as its inline message (pure; JVM-tested). The same three
 * messages as iOS `ReportError`: every condition is raised as P0001 with the
 * slug as its message, so they arrive as [DataError.Conflict].
 */
@StringRes
fun reportErrorMessage(error: DataError): Int =
    when (error) {
        is DataError.Conflict ->
            when (error.code) {
                "not_found" -> R.string.report_error_unavailable
                "cannot_report_own" -> R.string.report_error_own
                else -> R.string.report_error_generic
            }

        DataError.NotFound, DataError.Network, DataError.Unauthorized, DataError.Quota, is DataError.Unknown ->
            R.string.report_error_generic
    }
