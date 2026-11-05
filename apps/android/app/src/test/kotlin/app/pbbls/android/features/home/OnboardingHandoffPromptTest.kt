package app.pbbls.android.features.home

import app.pbbls.android.R
import app.pbbls.android.core.model.OnboardingReason
import org.junit.Assert.assertEquals
import org.junit.Test

/** The composer's first prompt is chosen by the first reason (#1510 D9, "Copy consumers"). */
class OnboardingHandoffPromptTest {
    @Test
    fun `every reason has its own prompt`() {
        val prompts = OnboardingReason.entries.map { onboardingHandoffPrompt(it) }
        assertEquals(OnboardingReason.entries.size, prompts.toSet().size)
        assertEquals(R.string.onboarding_handoff_prompt_notice_feelings, onboardingHandoffPrompt(OnboardingReason.NOTICE_FEELINGS))
        assertEquals(R.string.onboarding_handoff_prompt_keep_people, onboardingHandoffPrompt(OnboardingReason.KEEP_PEOPLE))
        assertEquals(R.string.onboarding_handoff_prompt_hard_season, onboardingHandoffPrompt(OnboardingReason.HARD_SEASON))
        assertEquals(R.string.onboarding_handoff_prompt_good_days, onboardingHandoffPrompt(OnboardingReason.GOOD_DAYS))
        assertEquals(R.string.onboarding_handoff_prompt_own_patterns, onboardingHandoffPrompt(OnboardingReason.OWN_PATTERNS))
        assertEquals(R.string.onboarding_handoff_prompt_keep_record, onboardingHandoffPrompt(OnboardingReason.KEEP_RECORD))
    }

    @Test
    fun `a skipped reasons step gets the default prompt`() {
        assertEquals(R.string.onboarding_handoff_prompt_default, onboardingHandoffPrompt(null))
    }
}
