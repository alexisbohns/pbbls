"use client"

import { useEffect, useRef, useState } from "react"
import { useTranslations } from "next-intl"
import { cn } from "@/lib/utils"

type PebbleEditTitleProps = {
  value: string
  onChange: (next: string) => void
  className?: string
}

// Tap-to-edit single-line title. Renders as static text when blurred and
// swaps to an input on focus. Blur or Enter stages the value into the draft.
export function PebbleEditTitle({ value, onChange, className }: PebbleEditTitleProps) {
  const t = useTranslations("pebble.edit")
  const [editing, setEditing] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (editing) {
      inputRef.current?.focus()
      inputRef.current?.select()
    }
  }, [editing])

  const placeholder = t("titlePlaceholder")

  if (editing) {
    return (
      <input
        ref={inputRef}
        type="text"
        value={value}
        aria-label={t("titleAria")}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        onBlur={() => setEditing(false)}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault()
            setEditing(false)
          }
          if (e.key === "Escape") {
            e.preventDefault()
            setEditing(false)
          }
        }}
        className={cn(
          "block w-full bg-transparent text-center font-heading text-2xl font-semibold text-foreground",
          "outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-md",
          "placeholder:text-muted-foreground/60",
          className,
        )}
      />
    )
  }

  return (
    <button
      type="button"
      onClick={() => setEditing(true)}
      aria-label={t("titleAria")}
      className={cn(
        "block w-full text-center font-heading text-2xl font-semibold transition-colors",
        "outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-md",
        value ? "text-foreground hover:text-primary" : "text-muted-foreground/60 hover:text-foreground",
        className,
      )}
    >
      {value || placeholder}
    </button>
  )
}
