package app.pbbls.android.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The `core/data` half of the production capture (#1048): `delete_moment`'s
 * jsonb answer and the PostgREST error bodies, byte for byte. See
 * `MomentProductionPayloadTest` in `core/model` for the readers.
 *
 * The error cases feed [dataErrorOf] exactly what supabase-kt would hand it:
 * the captured HTTP status, the body's `message` (`RestException.error`) and
 * its `code` (`PostgrestRestException.code`).
 */
class MomentProductionPayloadDataTest {
    private val json = Json { ignoreUnknownKeys = true }

    /** The PostgREST error envelope; supabase-kt reads `message` and `code` from it. */
    @Serializable
    private data class PostgrestErrorBody(
        val code: String,
        val details: String?,
        val hint: String?,
        val message: String,
    )

    private fun classify(
        statusCode: Int,
        body: String,
    ): DataError {
        val error = json.decodeFromString<PostgrestErrorBody>(body)
        assertEquals(setOf("code", "details", "hint", "message"), json.parseToJsonElement(body).jsonObject.keys)
        assertNull(error.details)
        assertNull(error.hint)
        return dataErrorOf(statusCode, error.message, error.code)
    }

    @Test
    fun `the bodies are the captured bytes`() {
        assertEquals(96, DELETE_MOMENT_PHOTO.length)
        assertEquals(68, ERROR_SAVE_MOMENT_EMPTY.length)
        assertEquals(68, ERROR_SAVE_MOMENT_EMPTY_EXPLICIT_NULLS.length)
        assertEquals(76, ERROR_COMPLETE_PEBBLE_NO_EMOTION.length)
        assertEquals(72, ERROR_DELETE_MOMENT_NOT_FOUND.length)
    }

    @Test
    fun `delete_moment returns the snap prefixes to clean up`() {
        val result = json.decodeFromString<DeleteMomentResult>(DELETE_MOMENT_PHOTO)
        assertEquals(
            listOf("40d05913-a631-489c-a04f-08ef063b9507/65f7be7f-d9ff-44c9-afea-d7848f82e089"),
            result.storagePaths,
        )
    }

    @Test
    fun `an empty save_moment is the moment_empty conflict`() {
        assertEquals(DataError.Conflict("moment_empty"), classify(400, ERROR_SAVE_MOMENT_EMPTY))
        assertEquals(DataError.Conflict("moment_empty"), classify(400, ERROR_SAVE_MOMENT_EMPTY_EXPLICIT_NULLS))
    }

    @Test
    fun `completing without an emotion is the pebble_needs_emotion conflict`() {
        assertEquals(DataError.Conflict("pebble_needs_emotion"), classify(400, ERROR_COMPLETE_PEBBLE_NO_EMOTION))
    }

    @Test
    fun `deleting an unknown moment is not found although PostgREST answers 500`() {
        assertEquals(DataError.NotFound, classify(500, ERROR_DELETE_MOMENT_NOT_FOUND))
    }

    private companion object {
        // POST rpc/delete_moment {p_moment_id:<photo-only>} — HTTP 200.
        const val DELETE_MOMENT_PHOTO =
            """{"storage_paths": ["40d05913-a631-489c-a04f-08ef063b9507/65f7be7f-d9ff-44c9-afea-d7848f82e089"]}"""

        // POST rpc/save_moment {payload:{}} — HTTP 400.
        const val ERROR_SAVE_MOMENT_EMPTY =
            """{"code":"P0001","details":null,"hint":null,"message":"moment_empty"}"""

        // POST rpc/save_moment {payload:{name:null, description:null, happened_at, snaps:[]}} — HTTP 400.
        const val ERROR_SAVE_MOMENT_EMPTY_EXPLICIT_NULLS =
            """{"code":"P0001","details":null,"hint":null,"message":"moment_empty"}"""

        // POST rpc/complete_pebble without emotion_id — HTTP 400.
        const val ERROR_COMPLETE_PEBBLE_NO_EMOTION =
            """{"code":"P0001","details":null,"hint":null,"message":"pebble_needs_emotion"}"""

        // POST rpc/delete_moment {p_moment_id:<unknown uuid>} — HTTP 500.
        const val ERROR_DELETE_MOMENT_NOT_FOUND =
            """{"code":"P0002","details":null,"hint":null,"message":"moment_not_found"}"""
    }
}
