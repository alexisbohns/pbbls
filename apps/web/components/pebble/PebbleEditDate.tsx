"use client"

import { useEffect, useRef, useState } from "react"
import { useTranslations } from "next-intl"
import { useFormatPeekDate } from "@/lib/i18n"
import { cn } from "@/lib/utils"

type PebbleEditDateProps = {
  value: string // ISO UTC
  onChange: (next: string) => void
  className?: string
}

// Convert ISO UTC → `YYYY-MM-DDTHH:mm` in local time for `<input
// type="datetime-local">`. The input value has no timezone suffix.
function toInputValue(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ""
  const pad = (n: number) => String(n).padStart(2, "0")
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// Local datetime-local string → ISO UTC. `new Date("YYYY-MM-DDTHH:mm")`
// is interpreted as local time, then `toISOString()` normalizes to UTC.
function fromInputValue(value: string): string {
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ""
  return d.toISOString()
}

// Tap-to-edit date row. Renders the same uppercase meta-typo display the read
// view uses; tapping swaps in a native `datetime-local` picker. Blur or
// pressing Enter commits the staged value to the draft.
export function PebbleEditDate({ value, onChange, className }: PebbleEditDateProps) {
  const t = useTranslations("pebble.edit")
  const formatPeekDate = useFormatPeekDate()
  const [editing, setEditing] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (editing) inputRef.current?.focus()
  }, [editing])

  if (editing) {
    return (
      <div className={cn("text-center", className)}>
        <input
          ref={inputRef}
          type="datetime-local"
          value={toInputValue(value)}
          aria-label={t("dateAria")}
          onChange={(e) => {
            const next = fromInputValue(e.target.value)
            if (next) onChange(next)
          }}
          onBlur={() => setEditing(false)}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === "Escape") {
              e.preventDefault()
              setEditing(false)
            }
          }}
          className={cn(
            "inline-block bg-transparent text-xs font-medium uppercase tracking-wider text-muted-foreground",
            "outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-md",
          )}
        />
      </div>
    )
  }

  return (
    <button
      type="button"
      onClick={() => setEditing(true)}
      aria-label={t("dateAria")}
      className={cn(
        "block w-full text-center text-xs font-medium uppercase tracking-wider text-muted-foreground transition-colors",
        "outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-md hover:text-foreground",
        className,
      )}
    >
      <time dateTime={value}>{formatPeekDate(value)}</time>
    </button>
  )
}
