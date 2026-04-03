"use client"

import { useEffect } from "react"
import { ONBOARDING_STEPS } from "@/lib/config/onboarding-steps"
import { useOnboarding } from "@/lib/hooks/useOnboarding"
import { useHaptics } from "@/lib/hooks/useHaptics"
import { Button } from "@/components/ui/button"
import { OnboardingScreen } from "@/components/onboarding/OnboardingScreen"
import { cn } from "@/lib/utils"

export function OnboardingStepper() {
  const {
    currentStep,
    isFirstStep,
    isLastStep,
    step,
    back,
    next,
    skip,
    complete,
  } = useOnboarding(ONBOARDING_STEPS)

  const { vibrate } = useHaptics()

  const advance = isLastStep ? complete : next

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      const tag = (e.target as HTMLElement).tagName
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return

      if (e.key === "Enter" || e.key === "ArrowRight") {
        e.preventDefault()
        vibrate(10)
        advance()
      } else if (e.key === "Escape" || e.key === "ArrowLeft") {
        e.preventDefault()
        back()
      }
    }

    window.addEventListener("keydown", onKeyDown)
    return () => window.removeEventListener("keydown", onKeyDown)
  }, [advance, back, vibrate])

  return (
    <div className="flex h-full flex-col">
      {/* Skip link — always visible */}
      <nav className="flex justify-end p-4 pt-[calc(1rem+var(--safe-area-top))]" aria-label="Onboarding controls">
        <button
          type="button"
          onClick={skip}
          className="text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          Skip
        </button>
      </nav>

      {/* Active screen */}
      <OnboardingScreen step={step} />

      {/* Progress dots and navigation */}
      <div className="flex flex-col items-center gap-4 p-6 pb-[calc(1.5rem+var(--safe-area-bottom))]">
        {/* Dots */}
        <ol className="flex gap-2" aria-label="Onboarding progress">
          {ONBOARDING_STEPS.map((s, i) => (
            <li
              key={s.id}
              aria-current={i === currentStep ? "step" : undefined}
              aria-label={`Step ${i + 1} of ${ONBOARDING_STEPS.length}`}
              className={cn(
                "size-2 rounded-full transition-colors",
                i === currentStep ? "bg-primary" : "bg-muted",
              )}
            />
          ))}
        </ol>

        {/* Buttons */}
        <div className="flex w-full max-w-sm items-center justify-between">
          {!isFirstStep ? (
            <Button variant="ghost" className="h-11 px-4" onClick={back}>
              Back
            </Button>
          ) : (
            <span />
          )}

          {isLastStep ? (
            <Button className="h-11 px-6" onClick={complete}>
              Collect your first pebble
            </Button>
          ) : (
            <Button className="h-11 px-6" onClick={() => { vibrate(10); next() }}>
              Next
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
