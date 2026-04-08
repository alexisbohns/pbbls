import { useCallback } from "react"
import { useRouter } from "next/navigation"
import { useStepNavigation } from "@/lib/hooks/useStepNavigation"
import type { OnboardingStepConfig } from "@/lib/config/onboarding-steps"

export function useOnboarding(
  steps: OnboardingStepConfig[],
  onComplete: () => Promise<void> | void,
) {
  const { currentStep, isFirstStep, isLastStep, goBack, goNext } =
    useStepNavigation(steps.length)
  const router = useRouter()

  const complete = useCallback(async () => {
    await onComplete()
    router.push("/path")
  }, [onComplete, router])

  const skip = useCallback(async () => {
    await onComplete()
    router.push("/path")
  }, [onComplete, router])

  return {
    currentStep,
    isFirstStep,
    isLastStep,
    step: steps[currentStep],
    back: goBack,
    next: goNext,
    skip,
    complete,
  }
}
