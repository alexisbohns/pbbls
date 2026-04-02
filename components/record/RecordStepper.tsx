"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { CARD_TYPES } from "@/lib/config"
import { useRecordForm } from "@/lib/hooks/useRecordForm"
import { useStepNavigation } from "@/lib/hooks/useStepNavigation"
import { useHaptics } from "@/lib/hooks/useHaptics"
import { Button } from "@/components/ui/button"
import type { CelebrationData, RecordStepProps, StepConfig } from "@/components/record/types"
import { StepDateTime } from "@/components/record/StepDateTime"
import { StepName } from "@/components/record/StepName"
import { StepDescription } from "@/components/record/StepDescription"
import { StepIntensity } from "@/components/record/StepIntensity"
import { StepEmotion } from "@/components/record/StepEmotion"
import { StepSouls } from "@/components/record/StepSouls"
import { StepDomains } from "@/components/record/StepDomains"
import { StepGlyph } from "@/components/record/StepGlyph"
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
  { label: "Souls", Component: StepSouls, canAdvance: () => true },
  { label: "Domains", Component: StepDomains, canAdvance: () => true },
  { label: "Glyph", Component: StepGlyph, canAdvance: () => true },
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
  const [celebrationData, setCelebrationData] = useState<CelebrationData | null>(null)

  const { vibrate } = useHaptics()

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
      { label: "Summary", Component: StepSummary, canAdvance: () => true } satisfies StepConfig,
    ]
  }, [selectedCardTypeIds])

  const { currentStep, isFirstStep, isLastStep, goBack, goNext } =
    useStepNavigation(steps.length)

  const canAdvance = steps[currentStep].canAdvance(formData)

  const handleAdvance = useCallback(() => {
    if (!canAdvance) return
    if (isLastStep) {
      vibrate([10, 50, 20])
      void handleSave()
    } else {
      vibrate(10)
      goNext()
    }
  }, [canAdvance, isLastStep, handleSave, goNext, vibrate])

  // Keyboard shortcuts: Enter to advance, Escape to go back
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      const tag = (e.target as HTMLElement).tagName
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || tag === "BUTTON") return

      if (e.key === "Enter") {
        e.preventDefault()
        handleAdvance()
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
    <div className="touch-manipulation space-y-6">
      {/* Step indicator */}
      <div className="space-y-2">
        <p className="text-sm text-muted-foreground" aria-live="polite">
          Step {currentStep + 1} of {steps.length}
          <span className="sr-only">: {steps[currentStep].label}</span>
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

      {/* Active step */}
      <ActiveStep data={formData} onUpdate={handleUpdate} />

      {/* Error message */}
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}

      {/* Navigation */}
      <nav className="flex items-center justify-between" aria-label="Step navigation">
        {!isFirstStep ? (
          <Button variant="ghost" className="h-11 px-4 md:h-8 md:px-2.5" onClick={goBack}>
            Back
          </Button>
        ) : (
          <span />
        )}

        {isLastStep ? (
          <Button className="h-11 px-4 md:h-8 md:px-2.5" onClick={() => void handleSave()} disabled={saving}>
            {saving ? "Saving\u2026" : "Save pebble"}
          </Button>
        ) : (
          <Button className="h-11 px-4 md:h-8 md:px-2.5" onClick={goNext} disabled={!canAdvance}>Next</Button>
        )}
      </nav>
    </div>
  )
}
