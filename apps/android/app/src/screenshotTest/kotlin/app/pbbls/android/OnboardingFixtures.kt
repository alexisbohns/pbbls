package app.pbbls.android

import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.OnboardingPace
import app.pbbls.android.core.model.OnboardingReason
import app.pbbls.android.core.model.PromiseLetter
import app.pbbls.android.features.onboarding.OnboardingConsentState
import app.pbbls.android.features.onboarding.OnboardingPerson
import app.pbbls.android.features.onboarding.OnboardingSteps
import app.pbbls.android.features.onboarding.OnboardingUiState

/**
 * Fixed states for the onboarding previews (#1510), one per step. The screenshot source set cannot
 * see `src/test`'s `UiFixtures`, so the eight starter glyphs and the letter live here.
 */
internal object OnboardingFixtures {
    val promiseLetter =
        PromiseLetter(
            bodyEn = "There will never be a feed here.\n\nWhat you write stays yours.",
            bodyFr = "Il n'y aura jamais de fil ici.\n\nCe que tu écris reste à toi.",
            signedByEn = "The Pebbles team",
            signedByFr = "L'équipe Pebbles",
        )

    /** Eight system glyphs, two or three strokes each, in a 200 × 200 view box like the carved ones. */
    val systemGlyphs: List<Glyph> =
        listOf(
            listOf("M 40 160 L 100 40 L 160 160", "M 65 110 L 135 110"),
            listOf("M 40 100 Q 100 20 160 100", "M 40 100 Q 100 180 160 100"),
            listOf("M 100 30 L 100 170", "M 30 100 L 170 100", "M 55 55 L 145 145"),
            listOf("M 50 50 L 150 50 L 150 150 L 50 150 Z", "M 50 50 L 150 150"),
            listOf("M 100 40 A 60 60 0 1 1 99 40", "M 100 70 L 100 130"),
            listOf("M 40 140 Q 70 40 100 140 Q 130 240 160 140"),
            listOf("M 100 30 L 170 100 L 100 170 L 30 100 Z", "M 100 70 L 100 130", "M 70 100 L 130 100"),
            listOf("M 40 60 L 160 60", "M 40 100 L 160 100", "M 40 140 L 160 140"),
        ).mapIndexed { index, paths ->
            Glyph(
                id = "glyph-system-${index + 1}",
                name = "Starter ${index + 1}",
                strokes = paths.map { GlyphStroke(d = it, width = 6.0) },
                viewBox = "0 0 200 200",
                isSystem = true,
            )
        }

    private fun at(id: String) =
        OnboardingUiState(
            step =
                OnboardingSteps.all.first {
                    it.id == id
                },
            letter = promiseLetter,
            systemGlyphs = systemGlyphs,
        )

    val promise = at("promise")

    /** Two acts on record (sign-up wrote them), one switched on here, one still off. */
    val consent =
        at("consent").copy(
            consent = OnboardingConsentState(recorded = setOf(ConsentKind.TERMS, ConsentKind.PRIVACY), on = setOf(ConsentKind.HEALTH_DATA)),
        )

    val reasons = at("reasons").copy(reasons = setOf(OnboardingReason.KEEP_PEOPLE, OnboardingReason.GOOD_DAYS))

    val you = at("you").copy(name = "Alexis", glyphId = systemGlyphs[1].id)

    /** Two named places and the third still empty. */
    val people =
        at("people").copy(
            people =
                listOf(
                    OnboardingPerson(name = "Noé", glyphId = systemGlyphs[4].id, editing = false),
                    OnboardingPerson(name = "Mo", glyphId = null, editing = false),
                ),
        )

    val pace = at("pace").copy(pace = OnboardingPace.WEEKLY_WRAP)
}
