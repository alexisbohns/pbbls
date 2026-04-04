"use client"

import { useRef, useEffect } from "react"
import { ArrowUp } from "lucide-react"
import { Button } from "@/components/ui/button"
import type { StepComposer } from "@/components/record/types"

type RecordComposerProps = {
  composer: StepComposer
  value: string
  onChange: (value: string) => void
  onSubmit: () => void
  disabled?: boolean
  submitLabel?: string
}

export function RecordComposer({
  composer,
  value,
  onChange,
  onSubmit,
  disabled,
  submitLabel = "Next",
}: RecordComposerProps) {
  const ref = useRef<HTMLTextAreaElement | HTMLInputElement>(null)

  // Auto-focus on mount
  useEffect(() => {
    ref.current?.focus()
  }, [])

  function handleKeyDown(e: React.KeyboardEvent) {
    // Enter submits (without shift for textarea, always for input)
    if (e.key === "Enter" && (composer.mode === "input" || !e.shiftKey)) {
      e.preventDefault()
      if (!disabled) onSubmit()
    }
  }

  return (
    <div className="flex items-end gap-2 rounded-2xl border border-border bg-muted/30 p-2 dark:bg-input/30">
      {composer.mode === "textarea" ? (
        <textarea
          ref={ref as React.RefObject<HTMLTextAreaElement>}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={composer.placeholder}
          rows={1}
          className="max-h-40 min-h-[2.5rem] flex-1 resize-none bg-transparent px-2 py-1.5 text-base outline-none field-sizing-content md:text-sm"
        />
      ) : (
        <input
          ref={ref as React.RefObject<HTMLInputElement>}
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={composer.placeholder}
          className="h-10 flex-1 bg-transparent px-2 py-1.5 text-base outline-none md:text-sm"
        />
      )}

      <Button
        size="icon"
        className="size-9 shrink-0 rounded-xl"
        onClick={onSubmit}
        disabled={disabled}
        aria-label={submitLabel}
      >
        <ArrowUp className="size-4" />
      </Button>
    </div>
  )
}
