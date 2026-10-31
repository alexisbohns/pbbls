package app.pbbls.android.features.home.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

/** The snackbar's time (#1383 D7): the shutter in the device's zone, in the locale's short form. */
class CameraToastTest {
    private val shutter = OffsetDateTime.parse("2026-10-31T13:32:07Z")

    @Test
    fun `french reads the twenty-four hour clock in the device's zone`() {
        assertEquals("14:32", cameraToastTime(shutter, ZoneId.of("Europe/Paris"), Locale.FRANCE))
    }

    @Test
    fun `english reads its own short form`() {
        val time = cameraToastTime(shutter, ZoneId.of("Europe/Paris"), Locale.US)
        assertTrue(time, time.startsWith("2:32") && time.endsWith("PM"))
    }
}
