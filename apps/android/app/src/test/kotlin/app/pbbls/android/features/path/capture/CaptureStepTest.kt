package app.pbbls.android.features.path.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureStepTest {
    @Test
    fun `two steps, the photo then the moment page`() {
        assertEquals(listOf(CaptureStep.PHOTO, CaptureStep.MOMENT), CaptureStep.entries)
    }

    @Test
    fun `the page steps back to the photo, and nothing lies beyond it`() {
        assertEquals(CaptureStep.PHOTO, CaptureStep.MOMENT.previous)
        assertEquals(CaptureStep.MOMENT, CaptureStep.PHOTO.next)
        assertNull(CaptureStep.PHOTO.previous)
        assertNull(CaptureStep.MOMENT.next)
    }
}
