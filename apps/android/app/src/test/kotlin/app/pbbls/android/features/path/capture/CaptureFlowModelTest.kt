package app.pbbls.android.features.path.capture

import app.pbbls.android.R
import app.pbbls.android.core.data.TapHaptic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class CaptureFlowModelTest {
    private val haptics = mutableListOf<TapHaptic>()
    private val model = CaptureFlowModel(haptic = { haptics += it })

    @Test
    fun `the photo step may be skipped and when is always answered`() {
        model.advance()
        assertEquals(CaptureStep.WHEN, model.step)
        model.advance()
        assertEquals(CaptureStep.NAME, model.step)
        assertEquals(listOf(TapHaptic.ADVANCE, TapHaptic.ADVANCE), haptics)
    }

    @Test
    fun `a moment needs a name or a photo before it can be saved`() {
        model.goTo(CaptureStep.NAME)
        assertFalse(model.state.value.canSave)
        model.setName("   ")
        assertFalse(model.state.value.canSave)
        model.hasSnap = true
        assertTrue(model.state.value.canSave)
        model.hasSnap = false
        model.setName("Swim")
        assertTrue(model.state.value.canSave)
    }

    @Test
    fun `the name is clamped to forty characters`() {
        model.setName("x".repeat(41))
        assertEquals(40, model.draft.name.length)
    }

    @Test
    fun `a photo's capture date seeds when, and a missing one keeps now`() {
        val taken = OffsetDateTime.parse("2026-10-11T18:05:27Z")
        model.applyCaptureDate(taken)
        assertEquals(taken, model.draft.happenedAt)
        model.applyCaptureDate(null)
        assertEquals(taken, model.draft.happenedAt)
    }

    @Test
    fun `saving buzzes success, failing buzzes warning and keeps the step`() {
        model.goTo(CaptureStep.NAME)
        model.beginSave()
        model.fail(R.string.moment_error_empty)
        assertEquals(R.string.moment_error_empty, model.state.value.errorRes)
        assertFalse(model.state.value.isSaving)
        assertEquals(CaptureStep.NAME, model.step)
        model.saved()
        assertEquals(listOf(TapHaptic.WARNING, TapHaptic.SUCCESS), haptics)
    }

    @Test
    fun `back walks to the previous step and stops at the photo`() {
        model.goTo(CaptureStep.NAME)
        model.back()
        assertEquals(CaptureStep.WHEN, model.step)
        model.back()
        assertEquals(CaptureStep.PHOTO, model.step)
        model.back()
        assertEquals(CaptureStep.PHOTO, model.step)
        assertEquals(listOf(TapHaptic.ADVANCE, TapHaptic.ADVANCE), haptics)
    }
}
