"use client"

import { useCallback, useEffect } from "react"
import { useTranslations } from "next-intl"
import { ONBOARDING_STEPS } from "@/lib/config/onboarding-steps"
import { useOnboarding } from "@/lib/hooks/useOnboarding"
import { useAuth } from "@/lib/data/auth-context"
import { useHaptics } from "@/lib/hooks/useHaptics"
import { Button } from "@/components/ui/button"
import { OnboardingScreen } from "@/components/onboarding/OnboardingScreen"
import { cn } from "@/lib/utils"

export function OnboardingStepper() {
  const { updateProfile } = useAuth()
  const t = useTranslations("onboarding")

  const handleComplete = useCallback(async () => {
    await updateProfile({ onboarding_completed: true })
  }, [updateProfile])

  const {
    currentStep,
    isFirstStep,
    isLastStep,
    step,
    back,
    next,
    skip,
    complete,
  } = useOnboarding(ONBOARDING_STEPS, handleComplete)

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
      <nav className="flex justify-end p-4 pt-[calc(1rem+var(--safe-area-top))]" aria-label={t("controlsAria")}>
        <button
          type="button"
          onClick={skip}
          className="text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          {t("skip")}
        </button>
      </nav>

      {/* Active screen */}
      <OnboardingScreen step={step} />

      {/* Progress dots and navigation */}
      <div className="flex flex-col items-center gap-4 p-6 pb-[calc(1.5rem+var(--safe-area-bottom))]">
        {/* Dots */}
        <ol className="flex gap-2" aria-label={t("progressAria")}>
          {ONBOARDING_STEPS.map((s, i) => (
            <li
              key={s.id}
              aria-current={i === currentStep ? "step" : undefined}
              aria-label={t("stepAria", { current: i + 1, total: ONBOARDING_STEPS.length })}
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
              {t("back")}
            </Button>
          ) : (
            <span />
          )}

          {isLastStep ? (
            <Button className="h-11 px-6" onClick={complete}>
              {t("complete")}
            </Button>
          ) : (
            <Button className="h-11 px-6" onClick={() => { vibrate(10); next() }}>
              {t("next")}
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
