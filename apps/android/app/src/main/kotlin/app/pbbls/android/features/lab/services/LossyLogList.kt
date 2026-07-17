package app.pbbls.android.features.lab.services

import app.pbbls.android.features.lab.models.Log
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/**
 * Lossy per-element feed decode — ports iOS `LossyLogArray` (M44 design D2).
 * A bad row (unknown enum value — including the D1 platform policy —
 * malformed UUID, missing required key) is DROPPED whole, never defaulted:
 * do not add `coerceInputValues` or fallback enum cases here, either would
 * silently diverge from iOS and break the ported suite.
 */
object LossyLogList {
    /** Wire decoder for `v_logs_with_counts` rows; tolerant of extra columns. */
    val wire: Json = Json { ignoreUnknownKeys = true }

    /**
     * Decodes a raw top-level JSON array into its valid [Log]s, invoking
     * [onSkip] once per dropped element. A non-array top level still throws —
     * whole-response failures must reach the caller's error state.
     */
    fun decode(
        raw: String,
        onSkip: (index: Int, cause: Exception) -> Unit,
    ): List<Log> {
        val root = wire.parseToJsonElement(raw)
        val rows = root as? JsonArray ?: throw SerializationException("expected a top-level JSON array of log rows")
        return rows.mapIndexedNotNull { index, element ->
            try {
                wire.decodeFromJsonElement(Log.serializer(), element)
            } catch (e: Exception) {
                onSkip(index, e)
                null
            }
        }
    }
}
