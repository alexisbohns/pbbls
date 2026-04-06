"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { ArrowLeft, X } from "lucide-react"
import { CARD_TYPES } from "@/lib/config"
import { useRecordForm, parseRecordPrefill } from "@/lib/hooks/useRecordForm"
import { useStepNavigation } from "@/lib/hooks/useStepNavigation"
import { useHaptics } from "@/lib/hooks/useHaptics"
import { useKeyboardOffset } from "@/lib/hooks/useKeyboardOffset"
import { Button } from "@/components/ui/button"
import type { CelebrationData, RevelationData, RecordFormData, RecordStepProps, StepConfig } from "@/components/record/types"
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
import { PebbleRevelation } from "@/components/record/PebbleRevelation"
import { RecordCelebration } from "@/components/record/RecordCelebration"
import { RecordComposer } from "@/components/record/RecordComposer"

const FIXED_STEPS: StepConfig[] = [
  { label: "Date and time", Component: StepDateTime, canAdvance: (d) => d.happened_at !== "" },
  {
    label: "Name",
    Component: StepName,
    canAdvance: (d) => d.name.trim() !== "",
    composer: { field: "name", placeholder: "What happened?", mode: "input" },
  },
  {
    label: "Description",
    Component: StepDescription,
    canAdvance: () => true,
    composer: { field: "description", placeholder: "A bit more context…", mode: "textarea" },
  },
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
  const cardType = CARD_TYPES.find((ct) => ct.id === cardTypeId)
  function CardFillerStep(props: RecordStepProps) {
    return <StepCardFiller cardTypeId={cardTypeId} {...props} />
  }
  CardFillerStep.displayName = `StepCardFiller_${cardTypeId}`
  return {
    label,
    Component: CardFillerStep,
    canAdvance: () => true,
    composer: {
      field: `card:${cardTypeId}`,
      placeholder: cardType?.prompt ?? "Write…",
      mode: "textarea",
    },
  }
}

/** Read the composer value from form data (handles both top-level fields and card fillers) */
function getComposerValue(data: RecordFormData, field: string): string {
  if (field.startsWith("card:")) {
    const cardTypeId = field.slice(5)
    return data.cards.find((c) => c.species_id === cardTypeId)?.value ?? ""
  }
  return (data[field as keyof RecordFormData] as string) ?? ""
}

/** Write the composer value into form data */
function makeComposerPatch(field: string, value: string, data: RecordFormData): Partial<RecordFormData> {
  if (field.startsWith("card:")) {
    const cardTypeId = field.slice(5)
    return {
      cards: data.cards.map((c) =>
        c.species_id === cardTypeId ? { ...c, value } : c,
      ),
    }
  }
  return { [field]: value }
}

export function RecordStepper() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const [revelationData, setRevelationData] = useState<RevelationData | null>(null)
  const [savedData, setSavedData] = useState<CelebrationData | null>(null)
  const [celebrationData, setCelebrationData] = useState<CelebrationData | null>(null)

  const { vibrate } = useHaptics()
  const keyboardOffset = useKeyboardOffset()

  const prefill = useMemo(() => parseRecordPrefill(searchParams), [searchParams])

  const { formData, handleUpdate, handleSave, saving, error } = useRecordForm(
    (data) => {
      setSavedData(data)
      setRevelationData({ pebbleId: data.pebbleId, pebbleName: data.pebbleName })
    },
    prefill,
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
  const activeComposer = steps[currentStep].composer

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
  // (disabled when composer is active — the composer handles its own Enter)
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

  if (revelationData && savedData && !celebrationData) {
    return (
      <PebbleRevelation
        pebbleId={revelationData.pebbleId}
        pebbleName={revelationData.pebbleName}
        onContinue={() => setCelebrationData(savedData)}
      />
    )
  }

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
    <div className="flex min-h-dvh touch-manipulation flex-col">
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
      <div className="flex-1 space-y-6 pt-6">
        <ActiveStep data={formData} onUpdate={handleUpdate} />

        {/* Error message */}
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
      </div>

      {/* Bottom bar — composer for text steps, normal nav for others */}
      <div
        className="sticky bottom-0 z-40 border-t border-border bg-background px-4 pb-[calc(0.75rem+var(--safe-area-bottom))] pt-3 transition-transform duration-100"
        style={keyboardOffset > 0 ? { transform: `translateY(-${keyboardOffset}px)` } : undefined}
      >
        {activeComposer ? (
          <div className="space-y-2">
            {!isFirstStep && (
              <div>
                <Button variant="ghost" size="sm" onClick={goBack}>
                  <ArrowLeft data-icon="inline-start" className="size-3.5" />
                  Back
                </Button>
              </div>
            )}
            <RecordComposer
              composer={activeComposer}
              value={getComposerValue(formData, activeComposer.field)}
              onChange={(v) => handleUpdate(makeComposerPatch(activeComposer.field, v, formData))}
              onSubmit={() => void handleAdvance()}
              disabled={!canAdvance || saving}
              submitLabel={isLastStep ? "Save pebble" : "Next"}
            />
          </div>
        ) : (
          <nav className="flex items-center gap-3" aria-label="Step navigation">
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
        )}
      </div>
    </div>
  )
}
