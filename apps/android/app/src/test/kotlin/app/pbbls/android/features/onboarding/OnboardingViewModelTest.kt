package app.pbbls.android.features.onboarding

import app.pbbls.android.R
import app.pbbls.android.core.data.ConsentPreferences
import app.pbbls.android.core.data.OnboardingHandoff
import app.pbbls.android.core.model.ActiveConsent
import app.pbbls.android.core.model.ConsentGateLogic
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.OnboardingPace
import app.pbbls.android.core.model.OnboardingReason
import app.pbbls.android.testing.FakeConsentService
import app.pbbls.android.testing.FakeOnboardingService
import app.pbbls.android.testing.InMemoryPrefs
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class OnboardingViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val service = FakeOnboardingService()
    private val consents = FakeConsentService(rows = mutableListOf()) // nothing on record: every act is asked
    private val cache = ConsentPreferences(InMemoryPrefs())
    private val handoff = OnboardingHandoff()

    private fun vm() = OnboardingViewModel(service, consents, cache, handoff, userId = "user-1")

    @Test
    fun `starts on the promise with the letter`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            assertEquals("promise", vm.uiState.value.step.id)
            assertEquals(
                "There will never be a feed here.",
                vm.uiState.value.letter
                    ?.bodyEn,
            )
            assertEquals(8, vm.uiState.value.systemGlyphs.size)
        }

    @Test
    fun `a letter that fails to load still lets the flow start`() =
        runTest {
            service.letterFailure = IOException("offline")
            val vm = vm()
            advanceUntilIdle()
            assertNull(vm.uiState.value.letter)
            assertTrue(vm.uiState.value.letterUnavailable)
        }

    @Test
    fun `consent asks only the missing acts and records each as it is switched on`() =
        runTest {
            consents.rows.add(ActiveConsent(ConsentKind.TERMS.wire, ConsentGateLogic.versionFor(ConsentKind.TERMS)))
            val vm = vm()
            advanceUntilIdle()
            vm.next() // promise → consent
            advanceUntilIdle()
            val consent = vm.uiState.value.consent
            assertEquals(setOf(ConsentKind.TERMS), consent.recorded)
            assertFalse(consent.canContinue)
            vm.onConsentToggle(ConsentKind.HEALTH_DATA, true)
            advanceUntilIdle()
            assertEquals(
                listOf(Triple(ConsentKind.HEALTH_DATA, ConsentGateLogic.versionFor(ConsentKind.HEALTH_DATA), ConsentGateLogic.SOURCE)),
                consents.recordCalls,
            )
            vm.onConsentToggle(ConsentKind.PRIVACY, true)
            vm.onConsentToggle(ConsentKind.AGE_ASSURANCE, true)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.consent.canContinue)
            assertTrue(cache.isSatisfied("user-1", ConsentGateLogic.fingerprint()))
        }

    @Test
    fun `a recorded act is locked on, so a further toggle writes nothing`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.next()
            vm.onConsentToggle(ConsentKind.HEALTH_DATA, true)
            advanceUntilIdle()
            vm.onConsentToggle(ConsentKind.HEALTH_DATA, false)
            vm.onConsentToggle(ConsentKind.HEALTH_DATA, true)
            advanceUntilIdle()
            assertTrue(ConsentKind.HEALTH_DATA in vm.uiState.value.consent.on)
            assertEquals(1, consents.recordCalls.size)
        }

    @Test
    fun `a failed record keeps the switch off with an error`() =
        runTest {
            consents.recordFailure = IOException("offline")
            val vm = vm()
            advanceUntilIdle()
            vm.next()
            vm.onConsentToggle(ConsentKind.HEALTH_DATA, true)
            advanceUntilIdle()
            assertFalse(ConsentKind.HEALTH_DATA in vm.uiState.value.consent.on)
            assertEquals(R.string.onboarding_consent_error, vm.uiState.value.consent.errorRes)
        }

    @Test
    fun `skip leaves the answer null and back returns`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.next()
            ConsentKind.entries.forEach { vm.onConsentToggle(it, true) }
            advanceUntilIdle()
            vm.next() // consent → reasons
            vm.onReasonToggle(OnboardingReason.KEEP_PEOPLE)
            vm.skip() // reasons → you, reasons dropped
            assertEquals("you", vm.uiState.value.step.id)
            assertNull(vm.uiState.value.answers.reasons)
            vm.back()
            assertEquals("reasons", vm.uiState.value.step.id)
            assertTrue(
                vm.uiState.value.reasons
                    .isEmpty(),
            )
        }

    @Test
    fun `back from the promise does nothing and consent cannot be skipped`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.back()
            assertEquals("promise", vm.uiState.value.step.id)
            vm.next()
            vm.skip()
            assertEquals("consent", vm.uiState.value.step.id)
        }

    @Test
    fun `completing sends every answer once and leaves the hand-off note`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.next()
            ConsentKind.entries.forEach { vm.onConsentToggle(it, true) }
            advanceUntilIdle()
            vm.next()
            vm.onReasonToggle(OnboardingReason.GOOD_DAYS)
            vm.onReasonToggle(OnboardingReason.KEEP_PEOPLE)
            vm.next()
            vm.onNameChange("Maya")
            vm.onGlyphPick("sys-2")
            vm.next()
            vm.onPersonAdd()
            vm.onPersonNameChange(0, "Noé")
            vm.onPersonCommit(0)
            vm.next()
            vm.onPaceSelect(OnboardingPace.DAILY)
            vm.next() // pace → handoff: complete
            advanceUntilIdle()
            val sent = service.completeCalls.single()
            assertEquals("Maya", sent.displayName)
            assertEquals("sys-2", sent.glyphId)
            assertEquals(listOf(OnboardingReason.GOOD_DAYS, OnboardingReason.KEEP_PEOPLE), sent.reasons)
            assertEquals(OnboardingPace.DAILY, sent.pace)
            assertEquals("Noé", sent.souls.single().name)
            assertEquals("sys-0", sent.souls.single().glyphId) // the first system glyph when none was picked
            val note = handoff.take()
            assertEquals(OnboardingReason.GOOD_DAYS, note?.reason)
            assertEquals("Noé", note?.firstPersonName)
            assertTrue(vm.uiState.value.isDone)
        }

    @Test
    fun `a refused completion shows the error and stays on pace`() =
        runTest {
            service.completeFailure = IOException("offline")
            val vm = vm()
            advanceUntilIdle()
            vm.next()
            ConsentKind.entries.forEach { vm.onConsentToggle(it, true) }
            advanceUntilIdle()
            vm.next() // consent → reasons
            repeat(4) { vm.skip() } // reasons, you, people (Later), pace
            advanceUntilIdle()
            assertEquals("pace", vm.uiState.value.step.id)
            assertEquals(R.string.onboarding_complete_error, vm.uiState.value.errorRes)
            assertFalse(vm.uiState.value.isDone)
        }

    @Test
    fun `a blank person is dropped on commit and at most three places exist`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.next() // promise → consent
            ConsentKind.entries.forEach { vm.onConsentToggle(it, true) }
            advanceUntilIdle()
            vm.next() // consent → reasons
            vm.next() // reasons → you
            vm.next() // you → people
            assertEquals("people", vm.uiState.value.step.id)
            vm.onPersonAdd()
            vm.onPersonCommit(0)
            assertTrue(
                vm.uiState.value.people
                    .isEmpty(),
            )
            repeat(3) { i ->
                vm.onPersonAdd()
                vm.onPersonNameChange(i, "P$i")
                vm.onPersonCommit(i)
            }
            vm.onPersonAdd()
            assertEquals(3, vm.uiState.value.people.size)
            assertTrue(
                vm.uiState.value.people
                    .none { it.editing },
            )
            vm.onPersonGlyphSheet(1)
            assertEquals(1, vm.uiState.value.glyphSheetFor)
            vm.onPersonGlyphPick(1, "sys-5")
            assertEquals(
                "sys-5",
                vm.uiState.value.people[1]
                    .glyphId,
            )
            assertNull(vm.uiState.value.glyphSheetFor)
            vm.onDrawForPerson(2)
            assertEquals(2, vm.uiState.value.carvingFor)
            vm.onCarved(Glyph(id = "mine", strokes = emptyList(), viewBox = "0 0 200 200"))
            assertEquals(
                "mine",
                vm.uiState.value.people[2]
                    .glyphId,
            )
            assertEquals(
                listOf("mine"),
                vm.uiState.value.carved
                    .map { it.id },
            )
            assertNull(vm.uiState.value.carvingFor)
            vm.onPersonRemove(0)
            assertEquals(
                listOf("P1", "P2"),
                vm.uiState.value.people
                    .map { it.name },
            )
        }

    @Test
    fun `drawing for you keeps the carved glyph as the pick, and cancelling keeps the old one`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.onGlyphPick("sys-2")
            vm.onDrawForYou()
            assertEquals(-1, vm.uiState.value.carvingFor)
            vm.onCarveCancelled()
            assertNull(vm.uiState.value.carvingFor)
            assertEquals("sys-2", vm.uiState.value.glyphId)
            vm.onDrawForYou()
            vm.onCarved(Glyph(id = "mine", strokes = emptyList(), viewBox = "0 0 200 200"))
            assertEquals("mine", vm.uiState.value.glyphId)
            assertEquals(
                listOf("mine"),
                vm.uiState.value.carved
                    .map { it.id },
            )
        }

    @Test
    fun `leaving people commits a typed name and drops a blank place`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.next()
            ConsentKind.entries.forEach { vm.onConsentToggle(it, true) }
            advanceUntilIdle()
            repeat(3) { vm.next() } // → people
            vm.onPersonAdd()
            vm.onPersonNameChange(0, " Noé ")
            vm.next() // Continue with the field still open
            assertEquals("pace", vm.uiState.value.step.id)
            assertEquals(
                listOf("Noé"),
                vm.uiState.value.people
                    .map { it.name },
            )
            assertTrue(
                vm.uiState.value.people
                    .none { it.editing },
            )
            vm.back()
            vm.onPersonAdd()
            vm.skip() // Later with a blank place open
            assertEquals("pace", vm.uiState.value.step.id)
            assertTrue(
                vm.uiState.value.people
                    .isEmpty(),
            )
        }
}
