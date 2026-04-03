import { useCallback } from "react"
import { useRouter } from "next/navigation"
import { useStepNavigation } from "@/lib/hooks/useStepNavigation"
import type { OnboardingStepConfig } from "@/lib/config/onboarding-steps"

const ONBOARDING_KEY = "pebbles_onboarding_completed"

export function useOnboarding(steps: OnboardingStepConfig[]) {
  const { currentStep, isFirstStep, isLastStep, goBack, goNext } =
    useStepNavigation(steps.length)
  const router = useRouter()

  const complete = useCallback(() => {
    localStorage.setItem(ONBOARDING_KEY, "true")
    router.push("/record")
  }, [router])

  const skip = useCallback(() => {
    localStorage.setItem(ONBOARDING_KEY, "true")
    router.push("/record")
  }, [router])

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

export function isOnboardingCompleted(): boolean {
  if (typeof window === "undefined") return true
  return localStorage.getItem(ONBOARDING_KEY) === "true"
}
