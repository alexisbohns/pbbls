package app.pbbls.android.features.path.complete

import app.pbbls.android.R
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteFlowModelTest {
    private val haptics = mutableListOf<TapHaptic>()
    private val model = CompleteFlowModel(haptic = { haptics += it })

    @Test
    fun `valence arrives seeded and commits in place`() {
        model.seedValenceIfNeeded()
        assertEquals(Valence.NEUTRAL_MEDIUM, model.answers.valence)
        assertTrue(haptics.isEmpty())
        model.selectValence(Valence.HIGHLIGHT_LARGE)
        assertEquals(CompleteStep.VALENCE, model.step)
        assertEquals(listOf(TapHaptic.SELECTION), haptics)
    }

    @Test
    fun `an unanswered mandatory step blocks with a warning`() {
        model.goTo(CompleteStep.EMOTION)
        model.advance()
        assertEquals(CompleteStep.EMOTION, model.step)
        assertEquals(listOf(TapHaptic.WARNING), haptics)
    }

    @Test
    fun `a tile pick commits and advances with one selection buzz`() {
        model.goTo(CompleteStep.EMOTION)
        model.selectEmotion("e-1")
        assertEquals(CompleteStep.DOMAIN, model.step)
        model.selectDomain("d-1")
        assertEquals(CompleteStep.SOULS, model.step)
        assertEquals(listOf(TapHaptic.SELECTION, TapHaptic.SELECTION), haptics)
    }

    @Test
    fun `souls toggle without advancing and skip is allowed`() {
        model.goTo(CompleteStep.SOULS)
        model.toggleSoul("s-1")
        model.toggleSoul("s-2")
        model.toggleSoul("s-1")
        assertEquals(listOf("s-2"), model.answers.soulIds)
        assertEquals(CompleteStep.SOULS, model.step)
        model.advance()
        assertEquals(CompleteStep.COLLECTION, model.step)
    }

    @Test
    fun `privacy selects without completing`() {
        model.goTo(CompleteStep.PRIVACY)
        model.selectVisibility(Visibility.PUBLIC)
        assertEquals(CompleteStep.PRIVACY, model.step)
        assertEquals(Visibility.PUBLIC, model.answers.visibility)
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
        assertEquals(CompleteStep.DOMAIN, model.step)
        model.resume(PebbleAnswers(valence = Valence.LOWLIGHT_SMALL, emotionId = "e-1", domainId = "d-1"))
        assertEquals(CompleteStep.PRIVACY, model.step)
    }

    @Test
    fun `success and failure buzz and move as decided`() {
        model.goTo(CompleteStep.PRIVACY)
        model.beginComplete()
        model.fail(R.string.pebble_error_soul_gone)
        assertEquals(CompleteStep.PRIVACY, model.step)
        assertFalse(model.isCompleting)
        model.beginComplete()
        model.succeed(CompleteResult(pebbleId = "p-1", karmaDelta = 5, glyph = null))
        assertEquals(CompleteStep.SUCCESS, model.step)
        assertEquals(listOf(TapHaptic.WARNING, TapHaptic.SUCCESS), haptics)
    }
}
