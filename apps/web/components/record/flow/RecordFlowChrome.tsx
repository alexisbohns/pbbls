"use client"

import { ChevronLeft, X } from "lucide-react"
import { useTranslations } from "next-intl"
import { previousIn } from "@/lib/record/steps"
import { cn } from "@/lib/utils"

type RecordFlowChromeProps<S extends string> = {
  /** The flow's whole step list, in order. */
  steps: readonly S[]
  /** The steps that get a dot — everything but the terminal one. */
  counted: readonly S[]
  step: S
  onBack: () => void
  onClose: () => void
}

/**
 * The flow's top bar: back chevron, progress dots, close.
 *
 * Minimal by design — picking is the advance, so there is no Next button
 * competing with the dots, and "Save as draft" lives in the close confirmation
 * rather than taking permanent residence here.
 *
 * Generic over the step list: the record and Complete flows share it.
 */
export function RecordFlowChrome<S extends string>({
  steps,
  counted,
  step,
  onBack,
  onClose,
}: RecordFlowChromeProps<S>) {
  const t = useTranslations("record.flow")
  const currentIndex = counted.indexOf(step)
  const current = currentIndex === -1 ? null : currentIndex
  const canGoBack = previousIn(steps, step) !== null

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

      {/* One element to a screen reader, not one per dot: "Step 4 of 9" is the useful
          reading, and ten unlabeled dots is not. */}
      <div
        role="img"
        aria-label={
          current === null
            ? t("stepDone")
            : t("stepOf", { current: current + 1, total: counted.length })
        }
        className="flex items-center gap-1.5"
      >
        {counted.map((candidate, index) => {
          const filled = current !== null && index <= current
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
