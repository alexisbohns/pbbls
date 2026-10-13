package app.pbbls.android.core.ui

import app.pbbls.android.R
import app.pbbls.android.core.data.DataError
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentErrorMessageTest {
    @Test
    fun `every slug in the shared table maps to its copy`() {
        mapOf(
            "moment_empty" to R.string.moment_error_empty,
            "moment_not_found" to R.string.moment_error_not_found,
            "pebble_needs_emotion" to R.string.pebble_error_needs_emotion,
            "pebble_needs_valence" to R.string.pebble_error_needs_valence,
            "pebble_needs_domain" to R.string.pebble_error_needs_domain,
            "soul_not_owned" to R.string.pebble_error_soul_gone,
            "collection_not_owned" to R.string.pebble_error_collection_gone,
        ).forEach { (slug, res) -> assertEquals(slug, res, momentWriteErrorRes(DataError.Conflict(slug))) }
    }

    @Test
    fun `classified failures map to their copy`() {
        assertEquals(R.string.moment_error_media_quota, momentWriteErrorRes(DataError.Quota))
        assertEquals(R.string.moment_error_not_found, momentWriteErrorRes(DataError.NotFound))
        assertEquals(R.string.record_signed_out_error, momentWriteErrorRes(DataError.Unauthorized))
    }

    @Test
    fun `everything else is the generic line`() {
        listOf(
            DataError.Conflict("invalid_payload"),
            DataError.Conflict("invalid_snap"),
            DataError.Conflict("something_new"),
            DataError.Network,
            DataError.Unknown(null),
        ).forEach { assertEquals(it.toString(), R.string.moment_save_error_generic, momentWriteErrorRes(it)) }
    }
}
