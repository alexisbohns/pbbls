package app.pbbls.android.core.ui

import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.model.OnboardingReason

/**
 * The moment page's first prompt after the onboarding (#1510 D9, "Copy consumers"): one line per
 * reason, chosen by the first reason picked in step 3, and a default when that step was skipped. It is
 * the placeholder of the NAME field (record-flow D13).
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

/** The wire value for "the reasons step was skipped": the hand-off happened, with the default prompt. */
const val ONBOARDING_PROMPT_DEFAULT_SLUG = "default"

/**
 * How the reason travels to the moment page: as the stable [OnboardingReason.slug] on the `Capture` key,
 * because the navigation stack survives process death and must serialize it. Null reason is the default's slug.
 */
fun onboardingPromptSlug(reason: OnboardingReason?): String = reason?.slug ?: ONBOARDING_PROMPT_DEFAULT_SLUG

/**
 * The prompt for a key's `promptReason`: null when there is none (no hand-off, the plain placeholder),
 * the default's for [ONBOARDING_PROMPT_DEFAULT_SLUG] or a slug this build does not know.
 */
@StringRes
fun onboardingPromptForSlug(slug: String?): Int? =
    slug?.let { s -> onboardingHandoffPrompt(OnboardingReason.entries.firstOrNull { it.slug == s }) }
