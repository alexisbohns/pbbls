"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { ArrowLeft, X } from "lucide-react"
import { CARD_TYPES } from "@/lib/config"
import { useRecordForm } from "@/lib/hooks/useRecordForm"
import { useStepNavigation } from "@/lib/hooks/useStepNavigation"
import { useHaptics } from "@/lib/hooks/useHaptics"
import { useKeyboardOffset } from "@/lib/hooks/useKeyboardOffset"
import { Button } from "@/components/ui/button"
import type { CelebrationData, RecordStepProps, StepConfig } from "@/components/record/types"
import { StepDateTime } from "@/components/record/StepDateTime"
import { StepName } from "@/components/record/StepName"
import { StepDescription } from "@/components/record/StepDescription"
import { StepIntensity } from "@/components/record/StepIntensity"
import { StepEmotion } from "@/components/record/StepEmotion"
import { StepInstants } from "@/components/record/StepInstants"
import { StepSouls } from "@/components/record/StepSouls"
import { StepDomains } from "@/components/record/StepDomains"
import { StepGlyph, getGlyphPendingSave } from "@/components/record/StepGlyph"
import { StepCardPicker } from "@/components/record/StepCardPicker"
import { StepCardFiller } from "@/components/record/StepCardFiller"
import { StepSummary } from "@/components/record/StepSummary"
import { RecordCelebration } from "@/components/record/RecordCelebration"

const FIXED_STEPS: StepConfig[] = [
  { label: "Date and time", Component: StepDateTime, canAdvance: (d) => d.happened_at !== "" },
  { label: "Name", Component: StepName, canAdvance: (d) => d.name.trim() !== "" },
  { label: "Description", Component: StepDescription, canAdvance: () => true },
  { label: "Intensity & Positiveness", Component: StepIntensity, canAdvance: () => true },
  { label: "Emotion", Component: StepEmotion, canAdvance: (d) => d.emotion_id !== "" },
  { label: "Instants", Component: StepInstants, canAdvance: () => true },
  { label: "Souls", Component: StepSouls, canAdvance: () => true },
  { label: "Domains", Component: StepDomains, canAdvance: () => true },
  {
    label: "Glyph",
    Component: StepGlyph,
    canAdvance: () => true,
    onAdvance: async () => { await getGlyphPendingSave()?.() },
  },
  { label: "Cards", Component: StepCardPicker, canAdvance: () => true },
]

function makeCardFillerStep(cardTypeId: string, label: string): StepConfig {
  function CardFillerStep(props: RecordStepProps) {
    return <StepCardFiller cardTypeId={cardTypeId} {...props} />
  }
  CardFillerStep.displayName = `StepCardFiller_${cardTypeId}`
  return { label, Component: CardFillerStep, canAdvance: () => true }
}

export function RecordStepper() {
  const router = useRouter()
  const [celebrationData, setCelebrationData] = useState<CelebrationData | null>(null)

  const { vibrate } = useHaptics()
  const keyboardOffset = useKeyboardOffset()

  const { formData, handleUpdate, handleSave, saving, error } = useRecordForm(
    (data) => setCelebrationData(data),
  )

  // Derive step list only when the set of selected card types changes,
  // not when card text values change (which would remount the textarea).
  const selectedCardTypeIds = formData.cards.map((c) => c.species_id).join(",")

  const steps = useMemo(() => {
    const ids = selectedCardTypeIds.split(",").filter(Boolean)
    const selectedIds = new Set(ids)
    const cardFillerSteps = CARD_TYPES
      .filter((ct) => selectedIds.has(ct.id))
      .map((ct) => makeCardFillerStep(ct.id, `${ct.name} card`))

    return [
      ...FIXED_STEPS,
      ...cardFillerSteps,
      { label: "Summary", Component: StepSummary, canAdvance: () => true } as StepConfig,
    ]
  }, [selectedCardTypeIds])

  const { currentStep, isFirstStep, isLastStep, goBack, goNext } =
    useStepNavigation(steps.length)

  const canAdvance = steps[currentStep].canAdvance(formData)

  const handleAdvance = useCallback(async () => {
    if (!canAdvance) return
    const step = steps[currentStep]
    if (step.onAdvance) {
      await step.onAdvance(formData, handleUpdate)
    }
    if (isLastStep) {
      vibrate([10, 50, 20])
      void handleSave()
    } else {
      vibrate(10)
      goNext()
    }
  }, [canAdvance, isLastStep, handleSave, goNext, vibrate, steps, currentStep, formData, handleUpdate])

  // Keyboard shortcuts: Enter to advance, Escape to go back
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      const tag = (e.target as HTMLElement).tagName
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || tag === "BUTTON") return

      if (e.key === "Enter") {
        e.preventDefault()
        void handleAdvance()
      } else if (e.key === "Escape") {
        e.preventDefault()
        goBack()
      }
    }

    window.addEventListener("keydown", onKeyDown)
    return () => window.removeEventListener("keydown", onKeyDown)
  }, [handleAdvance, goBack])

  const { Component: ActiveStep } = steps[currentStep]

  if (celebrationData) {
    return (
      <RecordCelebration
        pebbleId={celebrationData.pebbleId}
        karmaDelta={celebrationData.karmaDelta}
        bounceBefore={celebrationData.bounceBefore}
        bounceAfter={celebrationData.bounceAfter}
      />
    )
  }

  return (
    <div className="touch-manipulation">
      {/* Top bar: close button + progress */}
      <div className="flex items-center gap-3">
        <Button
          variant="ghost"
          size="icon"
          onClick={() => router.push("/path")}
          aria-label="Close record flow"
        >
          <X className="size-4" />
        </Button>
        <div className="flex-1 space-y-1">
          <p className="sr-only" aria-live="polite">
            Step {currentStep + 1} of {steps.length}: {steps[currentStep].label}
          </p>
          <div
            role="progressbar"
            aria-valuenow={currentStep + 1}
            aria-valuemin={1}
            aria-valuemax={steps.length}
            aria-label={`Step ${currentStep + 1} of ${steps.length}`}
            className="flex gap-1.5"
          >
            {steps.map((step, i) => (
              <div
                key={step.label}
                className={`h-1.5 flex-1 rounded-full transition-colors ${
                  i <= currentStep ? "bg-primary" : "bg-muted"
                }`}
              />
            ))}
          </div>
        </div>
      </div>

      {/* Active step */}
      <div className="space-y-6 pb-[calc(5rem+var(--safe-area-bottom))] pt-6">
        <ActiveStep data={formData} onUpdate={handleUpdate} />

        {/* Error message */}
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
      </div>

      {/* Bottom-anchored navigation — fixed with keyboard offset for mobile */}
      <nav
        className="fixed inset-x-0 bottom-0 z-40 flex items-center gap-3 border-t border-border bg-background px-4 pb-[calc(0.75rem+var(--safe-area-bottom))] pt-3 transition-transform duration-100"
        style={keyboardOffset > 0 ? { transform: `translateY(-${keyboardOffset}px)` } : undefined}
        aria-label="Step navigation"
      >
        {!isFirstStep && (
          <Button variant="outline" className="h-11 px-4 md:h-9 md:px-2.5" onClick={goBack}>
            <ArrowLeft data-icon="inline-start" className="size-4" />
            Back
          </Button>
        )}

        <Button
          className="h-11 flex-1 px-4 md:h-9 md:px-2.5"
          onClick={() => void handleAdvance()}
          disabled={!canAdvance || saving}
        >
          {isLastStep ? (saving ? "Saving\u2026" : "Save pebble") : "Next"}
        </Button>
      </nav>
    </div>
  )
}
