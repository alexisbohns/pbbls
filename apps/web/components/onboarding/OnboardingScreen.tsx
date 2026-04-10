import type { OnboardingStepConfig } from "@/lib/config/onboarding-steps"

interface OnboardingScreenProps {
  step: OnboardingStepConfig
}

export function OnboardingScreen({ step }: OnboardingScreenProps) {
  return (
    <section
      className="flex flex-1 flex-col items-center justify-center px-6 text-center"
      aria-labelledby={`onboarding-heading-${step.id}`}
    >
      <h1
        id={`onboarding-heading-${step.id}`}
        className="mb-4 text-3xl font-bold tracking-tight"
      >
        {step.heading}
      </h1>
      <p className="max-w-sm text-lg text-muted-foreground">
        {step.body}
      </p>
    </section>
  )
}
