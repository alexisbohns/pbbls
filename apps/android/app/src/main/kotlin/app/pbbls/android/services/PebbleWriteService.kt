package app.pbbls.android.services

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.AppEnvironment
import app.pbbls.android.R
import app.pbbls.android.features.path.models.ComposePebbleResponse
import app.pbbls.android.features.path.models.PebbleCreatePayload
import app.pbbls.android.features.path.models.PebbleDraft
import app.pbbls.android.features.path.models.PebbleSnapPayload
import app.pbbls.android.features.path.models.PebbleUpdatePayload
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Outcome of a compose-pebble / compose-pebble-update call. Screens branch on
 * semantics, not exception classes (D2). Mirrors the iOS save() outcomes.
 */
sealed interface ComposeResult {
    data class Success(
        val response: ComposePebbleResponse,
    ) : ComposeResult

    data class SoftSuccess(
        val pebbleId: String,
    ) : ComposeResult

    data class Failure(
        @StringRes val messageRes: Int,
    ) : ComposeResult
}

/**
 * The one write path for pebbles (D2) — UI never touches the transport. Posts to
 * the edge functions with a dedicated Ktor OkHttp client so we can (1) read the
 * 5xx soft-success body (risk 1) and (2) serialize the payload with OUR Json
 * (explicitNulls + ISO-8601 happened_at, D3). Ports SupabaseService.swift-style
 * plumbing.
 */
class PebbleWriteService(
    private val supabase: SupabaseService,
) {
    private val http = HttpClient(OkHttp)

    // No defaults on the payload classes + explicitNulls = true => description
    // = null and glyph_id = null encode as literal JSON nulls (edit-clear, D3).
    private val json = Json { explicitNulls = true }
    private val decodeJson = Json { ignoreUnknownKeys = true }

    suspend fun create(
        draft: PebbleDraft,
        snaps: List<PebbleSnapPayload>? = null,
    ): ComposeResult {
        val body = json.encodeToString(CreateRequest(PebbleCreatePayload.from(draft, snaps)))
        val (status, text) = post(FUNCTION_CREATE, body) ?: return failGeneric()
        if (status in 200..299) return ComposeResult.Success(decodeJson.decodeFromString(text))
        // Create soft-success = the (500) body carries pebble_id (iOS parity).
        val pebbleId = softSuccessPebbleId(text)
        return if (pebbleId != null) {
            Log.w(TAG, "$FUNCTION_CREATE returned $status with pebble_id — advancing (soft-success)")
            ComposeResult.SoftSuccess(pebbleId)
        } else {
            ComposeResult.Failure(pebbleSaveErrorMessage(text))
        }
    }

    suspend fun update(
        pebbleId: String,
        draft: PebbleDraft,
        snaps: List<PebbleSnapPayload> = emptyList(),
    ): ComposeResult {
        val body = json.encodeToString(UpdateRequest(pebbleId, PebbleUpdatePayload.from(draft, snaps)))
        val (status, text) = post(FUNCTION_UPDATE, body) ?: return failGeneric()
        return when {
            status in 200..299 -> ComposeResult.Success(decodeJson.decodeFromString(text))
            // Edit soft-success = any status >= 500 advances (iOS parity); the
            // caller already knows the pebble id.
            status >= 500 -> {
                Log.w(TAG, "$FUNCTION_UPDATE returned $status — advancing (soft-success)")
                ComposeResult.SoftSuccess(pebbleId)
            }
            else -> ComposeResult.Failure(pebbleSaveErrorMessage(text))
        }
    }

    /** Direct RPC (void return). Server sums karma_events and writes the clawback. No flash. */
    suspend fun delete(pebbleId: String) {
        try {
            supabase.client.postgrest.rpc(
                "delete_pebble",
                buildJsonObject { put("p_pebble_id", pebbleId) },
            )
        } catch (e: Exception) {
            Log.e(TAG, "delete_pebble failed", e)
            throw e
        }
    }

    private fun failGeneric(): ComposeResult = ComposeResult.Failure(R.string.pebble_save_error_generic)

    /** POST helper: (status, body) or null when the request could not even be sent. */
    private suspend fun post(
        functionName: String,
        body: String,
    ): Pair<Int, String>? {
        val token = supabase.session?.accessToken
        if (token == null) {
            Log.e(TAG, "$functionName: no access token; not signed in")
            return null
        }
        return try {
            val response: HttpResponse =
                http.post("${AppEnvironment.supabaseUrl}/functions/v1/$functionName") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("apikey", AppEnvironment.supabaseAnonKey)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            response.status.value to response.bodyAsText()
        } catch (e: Exception) {
            Log.e(TAG, "$functionName request failed", e)
            null
        }
    }

    @Serializable
    private data class CreateRequest(
        val payload: PebbleCreatePayload,
    )

    @Serializable
    private data class UpdateRequest(
        @SerialName("pebble_id")
        val pebbleId: String,
        val payload: PebbleUpdatePayload,
    )

    companion object {
        private const val TAG = "pebble-write"
        private const val FUNCTION_CREATE = "compose-pebble"
        private const val FUNCTION_UPDATE = "compose-pebble-update"

        /**
         * Extracts pebble_id from an edge-function error body (create soft-success).
         * The compose-pebble 500 branch returns
         * `{ "error": "...", "pebble_id": "<uuid>" }` (functions/compose-pebble/index.ts).
         * Null for empty / non-JSON / missing-key bodies. Pure + unit-tested.
         */
        fun softSuccessPebbleId(body: String): String? =
            runCatching {
                val element = Json.parseToJsonElement(body).jsonObject["pebble_id"]
                element?.jsonPrimitive?.contentOrNull
            }.getOrNull()

        /**
         * Maps an error body to a user-facing message resource (D16, minus the iOS
         * photo/pipeline cases). Pure + unit-tested.
         */
        @StringRes
        fun pebbleSaveErrorMessage(body: String): Int {
            val message =
                runCatching {
                    val obj = Json.parseToJsonElement(body).jsonObject
                    (obj["error"] ?: obj["message"])?.jsonPrimitive?.contentOrNull
                }.getOrNull().orEmpty()
            return if (message.contains("media_quota_exceeded") || message.contains("P0001")) {
                R.string.pebble_save_error_media_quota
            } else {
                R.string.pebble_save_error_generic
            }
        }
    }
}

val LocalPebbleWriteService =
    staticCompositionLocalOf<PebbleWriteService> {
        error("LocalPebbleWriteService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
