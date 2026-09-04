"use client"

import { useEffect, useRef } from "react"
import { useTranslations } from "next-intl"
import { cn } from "@/lib/utils"

type RecordNameStepProps = {
  name: string
  limit: number
  onChange: (value: string) => void
  /** Advances the flow, so Enter commits the way the Continue button does. */
  onSubmit: () => void
}

/** The counter stays out of the way until the end is in sight. */
const COUNTDOWN_FROM = 15

/**
 * Step 2 — what to call it. Clamped to the limit as you type, so the counter
 * can never show an over-limit value and there is no error state to design.
 * The limit is front-end only.
 *
 * The field is bare — no background, handwritten — with the countdown fading in
 * beneath it. A controlled input is what makes the clamp honest: the value the
 * field renders is the value the reducer holds, so an over-limit keystroke is
 * rejected on screen rather than trimmed at publish. Do not add a "same value,
 * skip the update" guard here: that is exactly the shape of the iOS bug this
 * step was written around, where the field kept its own buffer and the user
 * watched a name get silently shortened at the end.
 */
export function RecordNameStep({ name, limit, onChange, onSubmit }: RecordNameStepProps) {
  const t = useTranslations("record.flow.name")
  const ref = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    ref.current?.focus()
  }, [])

  const remaining = Math.max(0, limit - name.length)
  const showsCountdown = remaining <= COUNTDOWN_FROM

  return (
    <div className="flex flex-col items-center gap-2 pt-6">
      <textarea
        ref={ref}
        value={name}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault()
            onSubmit()
          }
        }}
        placeholder={t("placeholder")}
        rows={1}
        // Native enforcement alongside the reducer's clamp: `maxLength` is what
        // tells assistive tech the field has a limit, and the clamp is what
        // catches the paths it does not cover (paste, IME composition).
        maxLength={limit}
        aria-label={t("label")}
        className="w-full resize-none border-none bg-transparent text-center font-hand text-3xl font-bold text-balance text-foreground outline-none field-sizing-content placeholder:text-muted-foreground/40"
      />

      {/* Always laid out, only faded — otherwise the field jumps when the
          counter appears mid-typing. */}
      {/* Decorative: the limit itself reaches assistive tech through the
          field's `maxLength`, and announcing a new count on every keystroke
          would be noise rather than help. */}
      <span
        aria-hidden
        className={cn(
          "text-sm tabular-nums transition-opacity duration-200",
          remaining === 0 ? "text-primary" : "text-muted-foreground",
          showsCountdown ? "opacity-100" : "opacity-0",
        )}
      >
        {remaining}
      </span>
    </div>
  )
}
