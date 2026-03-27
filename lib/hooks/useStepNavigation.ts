import { useCallback, useState } from "react"

export function useStepNavigation(totalSteps: number) {
  const [currentStep, setCurrentStep] = useState(0)

  const isFirstStep = currentStep === 0
  const isLastStep = currentStep === totalSteps - 1

  const goBack = useCallback(() => {
    if (!isFirstStep) setCurrentStep((s) => s - 1)
  }, [isFirstStep])

  const goNext = useCallback(() => {
    if (!isLastStep) setCurrentStep((s) => s + 1)
  }, [isLastStep])

  return { currentStep, isFirstStep, isLastStep, goBack, goNext }
}
