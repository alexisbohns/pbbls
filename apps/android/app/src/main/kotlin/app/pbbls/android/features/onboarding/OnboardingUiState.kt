package app.pbbls.android.features.onboarding

import androidx.annotation.StringRes
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.OnboardingAnswers
import app.pbbls.android.core.model.OnboardingPace
import app.pbbls.android.core.model.OnboardingReason
import app.pbbls.android.core.model.OnboardingSoulDraft
import app.pbbls.android.core.model.PromiseLetter

/** Step 2's four switches (#1510 D3). [recorded] acts are on and locked; [on] are the ones switched on here. */
data class OnboardingConsentState(
    val recorded: Set<ConsentKind> = emptySet(),
    val on: Set<ConsentKind> = emptySet(),
    val saving: Set<ConsentKind> = emptySet(),
    @StringRes val errorRes: Int? = null,
) {
    val canContinue: Boolean get() = saving.isEmpty() && (recorded + on).containsAll(ConsentKind.entries)
}

/** One piece of step 5's stone: a named person, or the empty place being typed into. */
data class OnboardingPerson(
    val name: String = "",
    /** The picked glyph, or null until a pick: the first system glyph is used at completion. */
    val glyphId: String? = null,
    /** True while the name field is open under the piece (S3). */
    val editing: Boolean = true,
)

/** The onboarding flow's whole state: the current step, every answer, and the satellites' state. */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingSteps.all.first(),
    val letter: PromiseLetter? = null,
    val letterUnavailable: Boolean = false,
    val systemGlyphs: List<Glyph> = emptyList(),
    val consent: OnboardingConsentState = OnboardingConsentState(),
    val reasons: Set<OnboardingReason> = emptySet(),
    val name: String = "",
    val glyphId: String? = null,
    /** Glyphs carved in the flow (S1), shown on the stone beside the system ones. */
    val carved: List<Glyph> = emptyList(),
    val people: List<OnboardingPerson> = emptyList(),
    /** Which person's glyph sheet is open (S2), or null. */
    val glyphSheetFor: Int? = null,
    /** The carve studio is open (S1), for the profile (-1) or a person's index. */
    val carvingFor: Int? = null,
    val pace: OnboardingPace? = null,
    val isCompleting: Boolean = false,
    @StringRes val errorRes: Int? = null,
    val isDone: Boolean = false,
) {
    val stepIndex: Int get() = OnboardingSteps.visible.indexOf(step)

    /** The answers as they would be sent now; null where a step was skipped or left blank. */
    val answers: OnboardingAnswers
        get() =
            OnboardingAnswers(
                displayName = name.trim().takeIf { it.isNotEmpty() },
                glyphId = glyphId,
                // In pick order (a Set keeps insertion order): the first one picked chooses the hand-off's prompt (D9).
                reasons = reasons.takeIf { it.isNotEmpty() }?.toList(),
                pace = pace,
                souls =
                    people
                        .filter { !it.editing && it.name.isNotBlank() }
                        .map { OnboardingSoulDraft(it.name.trim(), it.glyphId ?: systemGlyphs.firstOrNull()?.id ?: "") },
            )
}
