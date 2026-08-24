package app.pbbls.android.features.path.record

import app.pbbls.android.features.path.models.ComposePebbleResponse
import app.pbbls.android.features.path.models.KnownDraftIds
import app.pbbls.android.features.path.models.PebbleDraftPayload
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.models.Visibility
import app.pbbls.android.services.TapHaptic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Mirrors iOS `RecordFlowModelTests`. Everything interesting about the flow —
 * gating, back-preserves-answers, skip on optional steps only, first-gap resume,
 * the name clamp, and the haptic flavor per interaction — is testable here with
 * no UI at all, which is the point of routing every interaction through the
 * model (M58 D4).
 */
class RecordFlowModelTest {
    private val recorded = mutableListOf<TapHaptic>()

    private fun model() = RecordFlowModel(haptic = { recorded += it })

    private fun answeredThrough(step: RecordStep): RecordFlowModel {
        val model = model()
        model.draft =
            model.draft.copy(
                name = "A walk",
                valence = Valence.NEUTRAL_MEDIUM,
                emotionId = EMOTION_ID,
                domainId = DOMAIN_ID,
            )
        model.goTo(step)
        return model
    }

    // MARK: - Gating

    @Test
    fun mandatoryStepsGateForward() {
        val model = model()
        model.goTo(RecordStep.NAME)
        model.advance()
        assertEquals("blank name must not advance", RecordStep.NAME, model.step)
        assertEquals(listOf(TapHaptic.WARNING), recorded)
    }

    @Test
    fun anAnsweredMandatoryStepAdvances() {
        val model = model()
        model.goTo(RecordStep.NAME)
        model.setName("A walk")
        model.advance()
        assertEquals(RecordStep.VALENCE, model.step)
        assertEquals(listOf(TapHaptic.ADVANCE), recorded)
    }

    @Test
    fun aWhitespaceOnlyNameIsNotAnAnswer() {
        val model = model()
        model.setName("   ")
        assertFalse(model.hasAnswer(RecordStep.NAME))
    }

    /** Passing an optional step is the user saying "not this one", not an error. */
    @Test
    fun optionalStepsAdvanceWhileEmpty() {
        val model = model()
        assertEquals(RecordStep.PHOTO, model.step)
        model.advance()
        assertEquals(RecordStep.WHEN, model.step)
        assertEquals(listOf(TapHaptic.ADVANCE), recorded)
    }

    @Test
    fun whenAndPrivacyArriveAlreadyAnswered() {
        val model = model()
        assertTrue(model.hasAnswer(RecordStep.WHEN))
        assertTrue(model.hasAnswer(RecordStep.PRIVACY))
    }

    // MARK: - Skip / Done label

    @Test
    fun optionalButtonReadsSkipWhileEmptyAndDoneOnceFilled() {
        val model = model()
        model.goTo(RecordStep.SOULS)
        assertTrue(model.optionalButtonIsSkip)
        model.toggleSoul(SOUL_ID)
        assertFalse(model.optionalButtonIsSkip)
    }

    // MARK: - Back

    @Test
    fun backPreservesAnswers() {
        val model = model()
        model.goTo(RecordStep.VALENCE)
        model.selectValence(Valence.HIGHLIGHT_LARGE)
        assertEquals(RecordStep.EMOTION, model.step)
        model.back()
        assertEquals(RecordStep.VALENCE, model.step)
        assertEquals(Valence.HIGHLIGHT_LARGE, model.draft.valence)
    }

    @Test
    fun backFromTheFirstStepDoesNothing() {
        val model = model()
        model.back()
        assertEquals(RecordStep.PHOTO, model.step)
        assertTrue("no haptic for a no-op", recorded.isEmpty())
    }

    @Test
    fun successIsTerminal() {
        val model = model()
        model.succeed(ComposePebbleResponse(pebbleId = PEBBLE_ID))
        model.back()
        assertEquals(RecordStep.SUCCESS, model.step)
        model.advance()
        assertEquals(RecordStep.SUCCESS, model.step)
    }

    // MARK: - Selection

    @Test
    fun tileSelectionCommitsAndAdvancesWithOneHaptic() {
        val model = model()
        model.goTo(RecordStep.DOMAIN)
        model.selectDomain(DOMAIN_ID)
        assertEquals(DOMAIN_ID, model.draft.domainId)
        assertEquals(RecordStep.SOULS, model.step)
        // One tap, one buzz: two for one gesture reads as a stutter.
        assertEquals(listOf(TapHaptic.SELECTION), recorded)
    }

    @Test
    fun soulsToggleDoesNotAdvance() {
        val model = model()
        model.goTo(RecordStep.SOULS)
        model.toggleSoul(SOUL_ID)
        assertEquals(listOf(SOUL_ID), model.draft.soulIds)
        assertEquals(RecordStep.SOULS, model.step)
        model.toggleSoul(SOUL_ID)
        assertEquals(emptyList<String>(), model.draft.soulIds)
    }

    /** Silently publishing on a grade tap would be a trap (D3). */
    @Test
    fun privacySelectionDoesNotAdvance() {
        val model = model()
        model.goTo(RecordStep.PRIVACY)
        model.selectVisibility(Visibility.PUBLIC)
        assertEquals(Visibility.PUBLIC, model.draft.visibility)
        assertEquals(RecordStep.PRIVACY, model.step)
    }

    // MARK: - Name clamp

    @Test
    fun nameIsClampedToTheLimit() {
        val model = model()
        model.setName("x".repeat(RecordFlowModel.NAME_LIMIT + 25))
        assertEquals(RecordFlowModel.NAME_LIMIT, model.draft.name.length)
    }

    @Test
    fun nameAtExactlyTheLimitIsUntouched() {
        val model = model()
        val exact = "y".repeat(RecordFlowModel.NAME_LIMIT)
        model.setName(exact)
        assertEquals(exact, model.draft.name)
    }

    // MARK: - EXIF seeding

    @Test
    fun captureDateSeedsHappenedAt() {
        val model = model()
        val taken = OffsetDateTime.of(2026, 8, 1, 14, 30, 0, 0, ZoneOffset.UTC)
        model.applyCaptureDate(taken)
        assertEquals(taken, model.draft.happenedAt)
    }

    /** A photo without metadata leaves `happenedAt` at its default of now. */
    @Test
    fun aNullCaptureDateIsANoOp() {
        val model = model()
        val before = model.draft.happenedAt
        model.applyCaptureDate(null)
        assertEquals(before, model.draft.happenedAt)
    }

    // MARK: - Resume

    @Test
    fun firstGapIsTheFirstUnansweredMandatoryStep() {
        val model = model()
        assertEquals(RecordStep.NAME, model.firstGap())
        model.setName("A walk")
        assertEquals(RecordStep.VALENCE, model.firstGap())
    }

    /** Skipping an optional step is a legitimate answer — re-asking would undo it. */
    @Test
    fun optionalStepsAreNeverGaps() {
        val model = answeredThrough(RecordStep.PHOTO)
        assertEquals(RecordStep.PRIVACY, model.firstGap())
    }

    @Test
    fun aFullyAnsweredDraftResumesAgainstPublish() {
        val model = model()
        model.resume(
            PebbleDraftPayload(
                name = "A walk",
                emotionId = EMOTION_ID,
                domainIds = listOf(DOMAIN_ID),
                positiveness = 0,
                intensity = 2,
            ),
            KnownDraftIds(soulIds = emptySet(), collectionIds = emptySet()),
        )
        assertEquals(RecordStep.PRIVACY, model.step)
        assertEquals("A walk", model.draft.name)
    }

    @Test
    fun aPartialDraftResumesAtItsFirstGap() {
        val model = model()
        model.resume(
            PebbleDraftPayload(name = "A walk", positiveness = 0, intensity = 2),
            KnownDraftIds(soulIds = emptySet(), collectionIds = emptySet()),
        )
        assertEquals(RecordStep.EMOTION, model.step)
    }

    /** Resume is a jump, not an interaction: nothing the user did, so no buzz. */
    @Test
    fun resumeIsSilent() {
        val model = model()
        model.resume(
            PebbleDraftPayload(name = "A walk"),
            KnownDraftIds(soulIds = emptySet(), collectionIds = emptySet()),
        )
        assertTrue(recorded.isEmpty())
    }

    // MARK: - Publish

    @Test
    fun publishSuccessLandsOnTheSuccessStep() {
        val model = model()
        model.goTo(RecordStep.PRIVACY)
        model.beginPublish()
        assertTrue(model.isPublishing)
        model.succeed(ComposePebbleResponse(pebbleId = PEBBLE_ID, karmaDelta = 12))
        assertEquals(RecordStep.SUCCESS, model.step)
        assertFalse(model.isPublishing)
        assertEquals(12, model.published?.karmaDelta)
        assertEquals(listOf(TapHaptic.SUCCESS), recorded)
    }

    /** A hard failure never reaches step 10 — the draft, and the way out, stay put (D10). */
    @Test
    fun publishFailureStaysOnPrivacy() {
        val model = model()
        model.goTo(RecordStep.PRIVACY)
        model.beginPublish()
        model.fail(SOME_MESSAGE_RES)
        assertEquals(RecordStep.PRIVACY, model.step)
        assertFalse(model.isPublishing)
        assertEquals(SOME_MESSAGE_RES, model.publishErrorRes)
        assertNull(model.published)
        assertEquals(listOf(TapHaptic.WARNING), recorded)
    }

    @Test
    fun beginningAPublishClearsThePreviousError() {
        val model = model()
        model.fail(SOME_MESSAGE_RES)
        model.beginPublish()
        assertNull(model.publishErrorRes)
    }

    private companion object {
        const val EMOTION_ID = "11111111-1111-1111-1111-111111111111"
        const val DOMAIN_ID = "22222222-2222-2222-2222-222222222222"
        const val SOUL_ID = "33333333-3333-3333-3333-333333333333"
        const val PEBBLE_ID = "44444444-4444-4444-4444-444444444444"

        /** Any resource id — the model only carries it, never resolves it. */
        const val SOME_MESSAGE_RES = 12345
    }
}
