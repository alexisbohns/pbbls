"use client"

import { useCallback, useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import { usePebbles } from "@/lib/data/usePebbles"
import type { PebbleCard } from "@/lib/types"
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

const STEPS = [
  { label: "Time, Intensity & Emotion", Component: RecordStep1 },
  { label: "Souls & Domains", Component: RecordStep2 },
  { label: "Cards & Review", Component: RecordStep3 },
] as const

const INITIAL_DATA: RecordFormData = {
  name: "",
  description: "",
  happened_at: new Date().toISOString(),
  intensity: 2,
  positiveness: 0,
  emotion_id: "",
  soul_ids: [],
  domain_ids: [],
  cards: [],
}

export function RecordStepper() {
  const router = useRouter()
  const { addPebble } = usePebbles()
  const [currentStep, setCurrentStep] = useState(0)
  const [formData, setFormData] = useState<RecordFormData>(INITIAL_DATA)
  const [saving, setSaving] = useState(false)

  const totalSteps = STEPS.length
  const isFirstStep = currentStep === 0
  const isLastStep = currentStep === totalSteps - 1

  const handleUpdate = useCallback((patch: Partial<RecordFormData>) => {
    setFormData((prev) => ({ ...prev, ...patch }))
  }, [])

  const goBack = useCallback(() => {
    if (!isFirstStep) setCurrentStep((s) => s - 1)
  }, [isFirstStep])

  const goNext = useCallback(() => {
    if (!isLastStep) setCurrentStep((s) => s + 1)
  }, [isLastStep])

  const handleSave = useCallback(async () => {
    if (saving) return
    setSaving(true)
    try {
      const pebble = await addPebble(formData)
      router.push(`/pebble/${pebble.id}`)
    } finally {
      setSaving(false)
    }
  }, [addPebble, formData, router, saving])

  const handleAdvance = useCallback(() => {
    if (isLastStep) {
      void handleSave()
    } else {
      goNext()
    }
  }, [isLastStep, handleSave, goNext])

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      // Don't capture keyboard shortcuts when an input/textarea/select is focused
      const tag = (e.target as HTMLElement).tagName
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return

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
          Step {currentStep + 1} of {totalSteps}
          <span className="sr-only">: {STEPS[currentStep].label}</span>
        </p>
        <div
          role="progressbar"
          aria-valuenow={currentStep + 1}
          aria-valuemin={1}
          aria-valuemax={totalSteps}
          aria-label={`Step ${currentStep + 1} of ${totalSteps}`}
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
          <Button onClick={goNext}>Next</Button>
        )}
      </nav>
    </div>
  )
}
