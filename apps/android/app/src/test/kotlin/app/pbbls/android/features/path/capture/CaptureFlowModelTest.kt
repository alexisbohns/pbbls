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
    fun `the photo advances to the page, which is the last step`() {
        model.advance()
        assertEquals(CaptureStep.MOMENT, model.step)
        model.advance()
        assertEquals(CaptureStep.MOMENT, model.step)
        assertEquals(listOf(TapHaptic.ADVANCE), haptics)
    }

    @Test
    fun `a moment needs a name or a photo before it can be saved`() {
        model.goTo(CaptureStep.MOMENT)
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
    fun `a words-only draft cannot save, but it does ask before closing`() {
        model.setDescription("We sat by the canal.")
        assertFalse(model.state.value.canSave)
        assertTrue(model.state.value.hasInput)
    }

    @Test
    fun `an empty draft has nothing to lose`() {
        assertFalse(model.state.value.hasInput)
        model.setDescription("   ")
        assertFalse(model.state.value.canSave)
    }

    @Test
    fun `the name clamps at forty and the words at two hundred and eighty`() {
        model.setName("x".repeat(41))
        assertEquals(40, model.draft.name.length)
        model.setDescription("y".repeat(281))
        assertEquals(280, model.draft.description.length)
        model.setDescription("y".repeat(280))
        assertEquals(280, model.draft.description.length)
    }

    @Test
    fun `a photo's capture date seeds when, says so, and a missing one keeps what was there`() {
        assertEquals(WhenSource.NOW, model.state.value.whenSource)
        val taken = OffsetDateTime.parse("2026-10-11T18:05:27Z")
        model.applyCaptureDate(taken)
        assertEquals(taken, model.draft.happenedAt)
        assertEquals(WhenSource.PHOTO, model.state.value.whenSource)
        model.applyCaptureDate(null)
        assertEquals(taken, model.draft.happenedAt)
    }

    @Test
    fun `a user edit of the date beats a later photo date`() {
        val chosen = OffsetDateTime.parse("2026-10-14T10:30:00Z")
        model.setHappenedAt(chosen)
        assertEquals(WhenSource.EDITED, model.state.value.whenSource)
        model.applyCaptureDate(OffsetDateTime.parse("2026-10-11T18:05:27Z"))
        assertEquals(chosen, model.draft.happenedAt)
        assertEquals(WhenSource.EDITED, model.state.value.whenSource)
    }

    @Test
    fun `the date source moves NOW to PHOTO to EDITED`() {
        val sources = mutableListOf(model.state.value.whenSource)
        model.applyCaptureDate(OffsetDateTime.parse("2026-10-11T18:05:27Z"))
        sources += model.state.value.whenSource
        model.setHappenedAt(OffsetDateTime.parse("2026-10-14T10:30:00Z"))
        sources += model.state.value.whenSource
        assertEquals(listOf(WhenSource.NOW, WhenSource.PHOTO, WhenSource.EDITED), sources)
    }

    @Test
    fun `saving buzzes success, failing buzzes warning and lands on the page`() {
        model.goTo(CaptureStep.MOMENT)
        model.beginSave()
        model.fail(R.string.moment_error_empty)
        assertEquals(R.string.moment_error_empty, model.state.value.errorRes)
        assertFalse(model.state.value.isSaving)
        assertEquals(CaptureStep.MOMENT, model.step)
        model.saved()
        assertEquals(listOf(TapHaptic.WARNING, TapHaptic.SUCCESS), haptics)
    }

    @Test
    fun `a failed save shows on the page from the photo step`() {
        model.goTo(CaptureStep.PHOTO)
        model.beginSave()
        model.fail(R.string.moment_error_empty)
        assertEquals(CaptureStep.MOMENT, model.step)
        assertEquals(R.string.moment_error_empty, model.state.value.errorRes)
    }

    @Test
    fun `the steps hold still while saving and once saved`() {
        model.goTo(CaptureStep.MOMENT)
        model.setName("Swim")
        model.beginSave()
        model.back()
        assertEquals(CaptureStep.MOMENT, model.step)
        assertFalse(model.state.value.canSave)
        model.saved()
        model.back()
        assertEquals(CaptureStep.MOMENT, model.step)
        assertFalse(model.state.value.canSave)
        assertEquals(listOf(TapHaptic.SUCCESS), haptics)
    }

    @Test
    fun `the save in flight says which button is spinning`() {
        model.beginSave(thenComplete = true)
        assertTrue(model.state.value.savingThenComplete)
        model.fail(R.string.moment_error_empty)
        assertFalse(model.state.value.savingThenComplete)
    }

    @Test
    fun `back walks to the photo and stops there`() {
        model.goTo(CaptureStep.MOMENT)
        model.back()
        assertEquals(CaptureStep.PHOTO, model.step)
        model.back()
        assertEquals(CaptureStep.PHOTO, model.step)
        assertEquals(listOf(TapHaptic.ADVANCE), haptics)
    }
}
