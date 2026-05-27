"use client"

import { useEffect, useRef, useState } from "react"
import { useTranslations } from "next-intl"
import { cn } from "@/lib/utils"

type PebbleEditDescriptionProps = {
  value: string
  onChange: (next: string) => void
  className?: string
}

// Auto-growing textarea that swaps in on tap. When empty AND blurred we show
// a dashed "Add a description" affordance so the missing field reads as an
// invitation, not a void (per issue #481 spec).
export function PebbleEditDescription({
  value,
  onChange,
  className,
}: PebbleEditDescriptionProps) {
  const t = useTranslations("pebble.edit")
  const [editing, setEditing] = useState(false)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  // Sync textarea height to its content so it grows naturally as the user
  // types. Tailwind 4's `field-sizing-content` would also work, but reading
  // scrollHeight is broadly supported and keeps the height correct across
  // initial focus + paste events.
  const syncHeight = () => {
    const el = textareaRef.current
    if (!el) return
    el.style.height = "auto"
    el.style.height = `${el.scrollHeight}px`
  }

  useEffect(() => {
    if (editing) {
      textareaRef.current?.focus()
      // Cursor at end of existing content.
      const len = value.length
      textareaRef.current?.setSelectionRange(len, len)
      syncHeight()
    }
  }, [editing, value.length])

  if (editing) {
    return (
      <textarea
        ref={textareaRef}
        value={value}
        aria-label={t("descriptionAria")}
        placeholder={t("descriptionPlaceholder")}
        rows={1}
        onChange={(e) => {
          onChange(e.target.value)
          syncHeight()
        }}
        onBlur={() => setEditing(false)}
        onKeyDown={(e) => {
          if (e.key === "Escape") {
            e.preventDefault()
            setEditing(false)
          }
        }}
        className={cn(
          "block w-full resize-none bg-transparent text-base leading-[1.4] text-foreground",
          "outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-md",
          "placeholder:text-muted-foreground/60",
          className,
        )}
      />
    )
  }

  if (value) {
    return (
      <button
        type="button"
        onClick={() => setEditing(true)}
        aria-label={t("descriptionAria")}
        className={cn(
          "block w-full whitespace-pre-wrap text-left text-base leading-[1.4] text-foreground",
          "outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-md transition-colors hover:text-primary",
          className,
        )}
      >
        {value}
      </button>
    )
  }

  return (
    <button
      type="button"
      onClick={() => setEditing(true)}
      aria-label={t("descriptionAria")}
      className={cn(
        "block w-full rounded-2xl border-2 border-dashed border-muted-foreground/30 px-4 py-3 text-left text-base text-muted-foreground/70 transition-colors",
        "hover:text-foreground active:scale-[0.99] outline-none focus-visible:ring-2 focus-visible:ring-ring",
        className,
      )}
    >
      {t("descriptionPlaceholder")}
    </button>
  )
}
