"use client"

import { ChevronLeft, X } from "lucide-react"
import { useTranslations } from "next-intl"
import { COUNTED_STEPS, dotIndex, previousStep, type RecordStep } from "@/lib/record/steps"
import { cn } from "@/lib/utils"

type RecordFlowChromeProps = {
  step: RecordStep
  onBack: () => void
  onClose: () => void
}

/**
 * The flow's top bar: back chevron, progress dots, close.
 *
 * Minimal by design — picking is the advance, so there is no Next button
 * competing with the dots, and "Save as draft" lives in the close confirmation
 * rather than taking permanent residence here.
 */
export function RecordFlowChrome({ step, onBack, onClose }: RecordFlowChromeProps) {
  const t = useTranslations("record.flow")
  const current = dotIndex(step)
  const canGoBack = previousStep(step) !== null

  return (
    <div className="flex items-center justify-between">
      <button
        type="button"
        onClick={onBack}
        aria-label={t("back")}
        aria-hidden={!canGoBack}
        disabled={!canGoBack}
        tabIndex={canGoBack ? undefined : -1}
        className={cn(
          "-ml-2 inline-flex size-11 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
          !canGoBack && "invisible",
        )}
      >
        <ChevronLeft className="size-5" aria-hidden />
      </button>

      {/* One element to a screen reader, not ten: "Step 4 of 10" is the useful
          reading, and ten unlabeled dots is not. */}
      <div
        role="img"
        aria-label={
          current === null
            ? t("stepDone")
            : t("stepOf", { current: current + 1, total: COUNTED_STEPS.length })
        }
        className="flex items-center gap-1.5"
      >
        {COUNTED_STEPS.map((candidate) => {
          const index = dotIndex(candidate)
          const filled = current !== null && index !== null && index <= current
          return (
            <span
              key={candidate}
              aria-hidden
              className={cn(
                "size-1.5 rounded-full transition-colors",
                filled ? "bg-primary" : "bg-muted-foreground/30",
              )}
            />
          )
        })}
      </div>

      <button
        type="button"
        onClick={onClose}
        aria-label={t("close")}
        className="-mr-2 inline-flex size-11 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <X className="size-5" aria-hidden />
      </button>
    </div>
  )
}
