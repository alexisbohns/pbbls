package app.pbbls.android.features.path.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureStepTest {
    @Test
    fun `photo, when, name in that order`() {
        assertEquals(listOf(CaptureStep.PHOTO, CaptureStep.WHEN, CaptureStep.NAME), CaptureStep.entries)
    }

    @Test
    fun `only the photo is optional`() {
        assertEquals(listOf(CaptureStep.PHOTO), CaptureStep.entries.filter { it.isOptional })
    }

    @Test
    fun `name steps back to when, and the photo has nothing before it`() {
        assertEquals(CaptureStep.WHEN, CaptureStep.NAME.previous)
        assertNull(CaptureStep.PHOTO.previous)
        assertNull(CaptureStep.NAME.next)
    }
}
