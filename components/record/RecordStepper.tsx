"use client"

import { useCallback, useEffect } from "react"
import { useRouter } from "next/navigation"
import type { PebbleCard } from "@/lib/types"
import { useRecordForm } from "@/lib/hooks/useRecordForm"
import { useStepNavigation } from "@/lib/hooks/useStepNavigation"
import { Button } from "@/components/ui/button"
import { RecordStep1 } from "@/components/record/RecordStep1"
import { RecordStep2 } from "@/components/record/RecordStep2"
import { RecordStep3 } from "@/components/record/RecordStep3"

export type RecordFormData = {
  name: string
  description: string
  happened_at: string
  intensity: 1 | 2 | 3
  positiveness: -2 | -1 | 0 | 1 | 2
  emotion_id: string
  soul_ids: string[]
  domain_ids: string[]
  cards: PebbleCard[]
}

export type RecordStepProps = {
  data: RecordFormData
  onUpdate: (patch: Partial<RecordFormData>) => void
}

type StepConfig = {
  label: string
  Component: React.ComponentType<RecordStepProps>
  canAdvance: (data: RecordFormData) => boolean
}

const STEPS: StepConfig[] = [
  {
    label: "Time, Intensity & Emotion",
    Component: RecordStep1,
    canAdvance: (data) => data.happened_at !== "" && data.emotion_id !== "",
  },
  { label: "Souls & Domains", Component: RecordStep2, canAdvance: () => true },
  { label: "Cards & Review", Component: RecordStep3, canAdvance: () => true },
]

export function RecordStepper() {
  const router = useRouter()

  const { formData, handleUpdate, handleSave, saving, error } = useRecordForm(
    (pebbleId) => router.push(`/pebble/${pebbleId}`),
  )

  const { currentStep, isFirstStep, isLastStep, goBack, goNext } =
    useStepNavigation(STEPS.length)

  const canAdvance = STEPS[currentStep].canAdvance(formData)

  const handleAdvance = useCallback(() => {
    if (!canAdvance) return
    if (isLastStep) {
      void handleSave()
    } else {
      goNext()
    }
  }, [canAdvance, isLastStep, handleSave, goNext])

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

  const { Component: ActiveStep } = STEPS[currentStep]

  return (
    <div className="space-y-6">
      {/* Step indicator */}
      <div className="space-y-2">
        <p className="text-sm text-muted-foreground" aria-live="polite">
          Step {currentStep + 1} of {STEPS.length}
          <span className="sr-only">: {STEPS[currentStep].label}</span>
        </p>
        <div
          role="progressbar"
          aria-valuenow={currentStep + 1}
          aria-valuemin={1}
          aria-valuemax={STEPS.length}
          aria-label={`Step ${currentStep + 1} of ${STEPS.length}`}
          className="flex gap-1.5"
        >
          {STEPS.map((step, i) => (
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
          <Button variant="ghost" onClick={goBack}>
            Back
          </Button>
        ) : (
          <span />
        )}

        {isLastStep ? (
          <Button onClick={() => void handleSave()} disabled={saving}>
            {saving ? "Saving\u2026" : "Save pebble"}
          </Button>
        ) : (
          <Button onClick={goNext} disabled={!canAdvance}>Next</Button>
        )}
      </nav>
    </div>
  )
}
