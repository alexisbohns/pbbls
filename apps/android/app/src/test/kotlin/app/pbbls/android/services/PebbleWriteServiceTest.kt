package app.pbbls.android.services

import app.pbbls.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exercises the two pure companion helpers of [PebbleWriteService] without any
 * transport (risk 1 + D16): [PebbleWriteService.softSuccessPebbleId] (the 5xx
 * soft-success `pebble_id` extraction) and [PebbleWriteService.pebbleSaveErrorMessage]
 * (the quota vs. generic `@StringRes` mapping). Both return plain values, so no
 * Android `Context` and no live session are needed. The captured 500 body is
 * authoritative from `functions/compose-pebble/index.ts` (`{ error, pebble_id }`).
 */
class PebbleWriteServiceTest {
    @Test
    fun `softSuccessPebbleId extracts the id from a compose-pebble 500 body`() {
        val body = """{ "error": "compose failed: boom", "pebble_id": "abc-123" }"""
        assertEquals("abc-123", PebbleWriteService.softSuccessPebbleId(body))
    }

    @Test
    fun `softSuccessPebbleId returns null for missing-key, empty, or non-JSON bodies`() {
        assertNull(PebbleWriteService.softSuccessPebbleId("""{ "error": "bad request" }"""))
        assertNull(PebbleWriteService.softSuccessPebbleId(""))
        assertNull(PebbleWriteService.softSuccessPebbleId("<html>502</html>"))
    }

    @Test
    fun `pebbleSaveErrorMessage maps quota codes to the quota message`() {
        assertEquals(
            R.string.pebble_save_error_media_quota,
            PebbleWriteService.pebbleSaveErrorMessage("""{ "error": "media_quota_exceeded" }"""),
        )
        assertEquals(
            R.string.pebble_save_error_media_quota,
            PebbleWriteService.pebbleSaveErrorMessage("""{ "message": "P0001" }"""),
        )
    }

    @Test
    fun `pebbleSaveErrorMessage falls back to the generic message`() {
        assertEquals(
            R.string.pebble_save_error_generic,
            PebbleWriteService.pebbleSaveErrorMessage("""{ "error": "something else" }"""),
        )
        assertEquals(
            R.string.pebble_save_error_generic,
            PebbleWriteService.pebbleSaveErrorMessage("not json at all"),
        )
    }
}
