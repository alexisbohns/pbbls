export type OnboardingStepKey = "path" | "pebble" | "ritual"

export type OnboardingStepConfig = {
  /** Stable identifier (also used as i18n key, see `onboarding.steps.<key>`). */
  id: OnboardingStepKey
}

export const ONBOARDING_STEPS: OnboardingStepConfig[] = [
  { id: "path" },
  { id: "pebble" },
  { id: "ritual" },
]
