package app.pbbls.android.features.home

import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.model.OnboardingReason

/**
 * The quick composer's first prompt after the onboarding (#1510 D9, "Copy consumers"): one line per
 * reason, chosen by the first reason picked in step 3, and a default when that step was skipped.
 */
@StringRes
fun onboardingHandoffPrompt(reason: OnboardingReason?): Int =
    when (reason) {
        OnboardingReason.NOTICE_FEELINGS -> R.string.onboarding_handoff_prompt_notice_feelings
        OnboardingReason.KEEP_PEOPLE -> R.string.onboarding_handoff_prompt_keep_people
        OnboardingReason.HARD_SEASON -> R.string.onboarding_handoff_prompt_hard_season
        OnboardingReason.GOOD_DAYS -> R.string.onboarding_handoff_prompt_good_days
        OnboardingReason.OWN_PATTERNS -> R.string.onboarding_handoff_prompt_own_patterns
        OnboardingReason.KEEP_RECORD -> R.string.onboarding_handoff_prompt_keep_record
        null -> R.string.onboarding_handoff_prompt_default
    }
