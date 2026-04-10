import { useCallback, useState } from "react"

export function useStepNavigation(totalSteps: number) {
  const [rawStep, setRawStep] = useState(0)

  // Clamp to valid range when totalSteps shrinks (e.g. card deselection)
  const currentStep = Math.min(rawStep, totalSteps - 1)

  const isFirstStep = currentStep === 0
  const isLastStep = currentStep === totalSteps - 1

  const goBack = useCallback(() => {
    if (!isFirstStep) setRawStep((s) => s - 1)
  }, [isFirstStep])

  const goNext = useCallback(() => {
    if (!isLastStep) setRawStep((s) => s + 1)
  }, [isLastStep])

  return { currentStep, isFirstStep, isLastStep, goBack, goNext }
}
