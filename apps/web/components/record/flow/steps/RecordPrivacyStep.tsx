"use client"

import { useTranslations } from "next-intl"
import type { Visibility } from "@/lib/types"
import { VISIBILITY_GRADES } from "@/components/record/VisibilityMenu"
import { cn } from "@/lib/utils"

type RecordPrivacyStepProps = {
  value: Visibility
  onSelect: (value: Visibility) => void
  /** Non-nil while the attached photo blocks publishing (uploading or failed). */
  blockedMessage: string | null
  publishError: string | null
}

/**
 * Step 9 — who gets to see it, and the publish button.
 *
 * The grade is the decision most coupled to "am I ready for other people to see
 * this", which is why it sits against publish rather than in a toolbar chip
 * eight fields away.
 *
 * A tap selects and does not advance. The photo's state and any publish error
 * live here too, because this is where the user is standing when publishing is
 * blocked or fails.
 */
export function RecordPrivacyStep({
  value,
  onSelect,
  blockedMessage,
  publishError,
}: RecordPrivacyStepProps) {
  const t = useTranslations("record.visibility")
  const tFlow = useTranslations("record.flow.privacy")

  return (
    <div className="flex flex-col gap-2">
      {VISIBILITY_GRADES.map(({ value: grade, icon: Icon }) => {
        const selected = grade === value
        return (
          <button
            key={grade}
            type="button"
            onClick={() => onSelect(grade)}
            aria-pressed={selected}
            className={cn(
              "flex w-full items-start gap-3 rounded-xl border p-3 text-left outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring",
              selected
                ? "border-primary bg-primary/10"
                : "border-border bg-card hover:bg-muted",
            )}
          >
            <Icon
              className={cn("mt-0.5 size-5 shrink-0", selected ? "text-primary" : "text-muted-foreground")}
              aria-hidden
            />
            <span className="flex flex-col gap-0.5">
              <span className={cn("text-sm font-medium", selected && "text-primary")}>
                {t(grade)}
              </span>
              {/* A sentence, so it takes a plain small style — never one of the
                  uppercasing label tokens. */}
              <span className="text-xs text-muted-foreground">{tFlow(grade)}</span>
            </span>
          </button>
        )
      })}

      {blockedMessage && (
        <p className="pt-2 text-center text-sm text-muted-foreground">{blockedMessage}</p>
      )}

      {publishError && (
        <p
          role="alert"
          className="mt-2 rounded-md bg-destructive/10 px-3 py-2 text-center text-sm text-destructive"
        >
          {publishError}
        </p>
      )}
    </div>
  )
}
