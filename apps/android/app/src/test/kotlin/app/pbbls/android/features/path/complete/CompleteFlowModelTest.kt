package app.pbbls.android.features.path.complete

import app.pbbls.android.R
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteFlowModelTest {
    private val haptics = mutableListOf<TapHaptic>()
    private val model = CompleteFlowModel(haptic = { haptics += it })

    @Test
    fun `valence arrives empty and commits in place`() {
        assertNull(model.answers.valence)
        assertTrue(haptics.isEmpty())
        model.selectValence(Valence.HIGHLIGHT_LARGE)
        assertEquals(Valence.HIGHLIGHT_LARGE, model.answers.valence)
        assertEquals(CompleteStep.VALENCE, model.step)
        assertEquals(listOf(TapHaptic.SELECTION), haptics)
    }

    @Test
    fun `nothing seeds a valence and Continue stays blocked until a pick`() {
        assertEquals(CompleteStep.VALENCE, model.step)
        assertNull(model.answers.valence)
        assertFalse(model.state.value.isAnswered)
        model.advance()
        assertEquals(CompleteStep.VALENCE, model.step)
        assertEquals(listOf(TapHaptic.WARNING), haptics)
        model.selectValence(Valence.HIGHLIGHT_MEDIUM)
        assertTrue(model.state.value.isAnswered)
        model.advance()
        assertEquals(CompleteStep.EMOTION, model.step)
    }

    @Test
    fun `a restore without a valence lands on an empty valence step`() {
        model.resume(PebbleAnswers(soulIds = listOf("s-1")))
        assertEquals(CompleteStep.VALENCE, model.step)
        assertNull(model.answers.valence)
        assertEquals(listOf("s-1"), model.answers.soulIds)
    }

    @Test
    fun `an unanswered mandatory step blocks with a warning`() {
        model.goTo(CompleteStep.EMOTION)
        model.advance()
        assertEquals(CompleteStep.EMOTION, model.step)
        assertEquals(listOf(TapHaptic.WARNING), haptics)
    }

    @Test
    fun `an emotion pick commits and advances with one selection buzz`() {
        model.goTo(CompleteStep.EMOTION)
        model.selectEmotion("e-1")
        assertEquals(CompleteStep.LIFE, model.step)
        assertEquals(listOf(TapHaptic.SELECTION), haptics)
    }

    @Test
    fun `domains toggle in tap order and a tap on a chosen one removes it`() {
        model.goTo(CompleteStep.LIFE)
        model.toggleDomain("d-2")
        model.toggleDomain("d-1")
        model.toggleDomain("d-3")
        assertEquals(listOf("d-2", "d-1", "d-3"), model.answers.domainIds)
        model.toggleDomain("d-1")
        assertEquals(listOf("d-2", "d-3"), model.answers.domainIds)
        assertEquals(List(4) { TapHaptic.SELECTION }, haptics)
        assertEquals(CompleteStep.LIFE, model.step)
    }

    @Test
    fun `a fourth domain is refused with a warning and changes nothing`() {
        model.goTo(CompleteStep.LIFE)
        listOf("d-1", "d-2", "d-3").forEach(model::toggleDomain)
        haptics.clear()
        model.toggleDomain("d-4")
        assertEquals(listOf("d-1", "d-2", "d-3"), model.answers.domainIds)
        assertEquals(listOf(TapHaptic.WARNING), haptics)
    }

    @Test
    fun `life is answered by one domain and advance never reaches success`() {
        model.goTo(CompleteStep.LIFE)
        model.advance()
        assertEquals(listOf(TapHaptic.WARNING), haptics)
        model.toggleDomain("d-1")
        haptics.clear()
        assertTrue(model.state.value.isAnswered)
        model.advance()
        assertEquals(CompleteStep.LIFE, model.step)
        assertTrue(haptics.isEmpty())
    }

    @Test
    fun `souls toggle without advancing`() {
        model.goTo(CompleteStep.LIFE)
        model.toggleSoul("s-1")
        model.toggleSoul("s-2")
        model.toggleSoul("s-1")
        assertEquals(listOf("s-2"), model.answers.soulIds)
        assertEquals(CompleteStep.LIFE, model.step)
        assertEquals(List(3) { TapHaptic.SELECTION }, haptics)
    }

    @Test
    fun `the chooser's result replaces the souls silently and an equal one changes nothing`() {
        model.goTo(CompleteStep.LIFE)
        model.setSouls(listOf("s-2", "s-1"))
        assertEquals(listOf("s-2", "s-1"), model.answers.soulIds)
        model.setSouls(listOf("s-2", "s-1"))
        model.setSouls(emptyList())
        assertEquals(emptyList<String>(), model.answers.soulIds)
        assertTrue(haptics.isEmpty())
    }

    @Test
    fun `collection and visibility keep their defaults through the flow`() {
        assertNull(model.answers.collectionId)
        assertEquals(Visibility.SECRET, model.answers.visibility)
    }

    @Test
    fun `back preserves answers`() {
        model.goTo(CompleteStep.EMOTION)
        model.selectEmotion("e-1")
        model.back()
        assertEquals(CompleteStep.EMOTION, model.step)
        assertEquals("e-1", model.answers.emotionId)
    }

    @Test
    fun `a restored flow lands on its first gap`() {
        model.resume(PebbleAnswers(valence = Valence.LOWLIGHT_SMALL, emotionId = "e-1"))
        assertEquals(CompleteStep.LIFE, model.step)
        model.resume(PebbleAnswers(valence = Valence.LOWLIGHT_SMALL, emotionId = "e-1", domainIds = listOf("d-1")))
        assertEquals(CompleteStep.LIFE, model.step)
        model.resume(PebbleAnswers(valence = Valence.LOWLIGHT_SMALL, domainIds = listOf("d-1")))
        assertEquals(CompleteStep.EMOTION, model.step)
    }

    @Test
    fun `success and failure buzz and move as decided`() {
        model.goTo(CompleteStep.LIFE)
        model.beginComplete()
        model.fail(R.string.pebble_error_soul_gone)
        assertEquals(CompleteStep.LIFE, model.step)
        assertFalse(model.isCompleting)
        model.beginComplete()
        model.succeed(CompleteResult(pebbleId = "p-1", karmaDelta = 5, glyph = null))
        assertEquals(CompleteStep.SUCCESS, model.step)
        assertEquals(listOf(TapHaptic.WARNING, TapHaptic.SUCCESS), haptics)
    }

    @Test
    fun `a quiet success moves to success without the success buzz`() {
        model.goTo(CompleteStep.LIFE)
        model.beginComplete()
        model.fail(R.string.pebble_error_soul_gone)
        model.beginComplete()
        model.succeed(CompleteResult(pebbleId = "p-1", karmaDelta = 5, glyph = null), quiet = true)
        assertEquals(CompleteStep.SUCCESS, model.step)
        assertEquals(listOf(TapHaptic.WARNING), haptics)
    }
}
