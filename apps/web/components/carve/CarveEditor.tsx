"use client"

import { useCallback, useMemo, useState } from "react"
import { motion, useReducedMotion } from "framer-motion"
import { useTranslations } from "next-intl"
import { PEBBLE_SHAPES } from "@/lib/config"
import { useMarks } from "@/lib/data/useMarks"
import { useHaptics } from "@/lib/hooks/useHaptics"
import type { MarkStroke } from "@/lib/types"
import { DrawingCanvas } from "@/components/carve/DrawingCanvas"
import { CarveToolbar } from "@/components/carve/CarveToolbar"
import { StampPicker } from "@/components/carve/StampPicker"
import { Input } from "@/components/ui/input"

type CarveEditorProps = {
  markId?: string
  onSaved?: (markId: string) => void
}

export function CarveEditor({ onSaved }: CarveEditorProps) {
  const { addMark } = useMarks()
  const { vibrate } = useHaptics()
  const prefersReducedMotion = useReducedMotion()
  const t = useTranslations("carve")

  const [strokes, setStrokes] = useState<MarkStroke[]>([])
  const [strokeWidth, setStrokeWidth] = useState(4)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [name, setName] = useState("")

  // Pick a consistent shape for this editor session
  const shape = useMemo(
    () => PEBBLE_SHAPES[Math.floor(Math.random() * PEBBLE_SHAPES.length)],
    [],
  )

  const handleStrokeComplete = useCallback(
    (stroke: MarkStroke) => {
      setStrokes((prev) => [...prev, stroke])
      vibrate(5)
    },
    [vibrate],
  )

  const handleUndo = useCallback(() => {
    setStrokes((prev) => prev.slice(0, -1))
    vibrate(5)
  }, [vibrate])

  const handleClear = useCallback(() => {
    setStrokes([])
    vibrate([5, 30, 5])
  }, [vibrate])

  const handleStampSelect = useCallback(
    (stampStrokes: MarkStroke[]) => {
      setStrokes((prev) => [...prev, ...stampStrokes])
      vibrate(10)
    },
    [vibrate],
  )

  const handleSave = useCallback(async () => {
    if (saving || strokes.length === 0) return
    setSaving(true)
    try {
      const trimmed = name.trim()
      const mark = await addMark({
        shape_id: shape.id,
        strokes,
        viewBox: shape.viewBox,
        name: trimmed.length > 0 ? trimmed : undefined,
      })
      vibrate([10, 50, 10])
      setSaved(true)
      onSaved?.(mark.id)
    } finally {
      setSaving(false)
    }
  }, [saving, strokes, addMark, shape, vibrate, onSaved, name])

  const handleNewMark = useCallback(() => {
    setStrokes([])
    setSaved(false)
    setName("")
  }, [])

  if (saved) {
    return (
      <motion.section
        aria-label={t("savedAria")}
        className="flex flex-col items-center gap-6 py-8"
        initial={prefersReducedMotion ? false : { scale: 0.9, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: "spring", stiffness: 300, damping: 25 }}
      >
        <p className="text-lg font-medium" aria-live="polite">
          {name.trim() ? t("savedNamed", { name: name.trim() }) : t("saved")}
        </p>
        <svg
          viewBox={shape.viewBox}
          className="w-full max-w-[200px] aspect-square"
          aria-hidden="true"
        >
          <path
            d={shape.path}
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            className="text-border"
          />
          <g>
            {strokes.map((s, i) => (
              <path
                key={i}
                d={s.d}
                fill="none"
                stroke="currentColor"
                strokeWidth={s.width}
                strokeLinecap="round"
                strokeLinejoin="round"
                className="text-foreground"
              />
            ))}
          </g>
        </svg>
        <button
          onClick={handleNewMark}
          className="text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground"
        >
          {t("newGlyph")}
        </button>
      </motion.section>
    )
  }

  return (
    <motion.div
      className="flex flex-col items-center gap-6"
      initial={prefersReducedMotion ? false : { opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ type: "spring", stiffness: 300, damping: 25 }}
    >
      <figure className="flex flex-col items-center gap-2">
        <DrawingCanvas
          shape={shape}
          strokes={strokes}
          strokeWidth={strokeWidth}
          onStrokeComplete={handleStrokeComplete}
        />
        <figcaption className="sr-only">
          {t("canvasCaption")}
        </figcaption>
      </figure>

      {/* Screen reader announcement for stroke count */}
      <p className="sr-only" aria-live="polite">
        {t("strokeCountAnnounce", { count: strokes.length })}
      </p>

      <Input
        type="text"
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder={t("namePlaceholder")}
        aria-label={t("nameAria")}
        maxLength={80}
        className="w-full max-w-xs"
      />

      <CarveToolbar
        strokeCount={strokes.length}
        strokeWidth={strokeWidth}
        onUndo={handleUndo}
        onClear={handleClear}
        onWidthChange={setStrokeWidth}
        onSave={handleSave}
        saving={saving}
      />

      <StampPicker onSelect={handleStampSelect} />
    </motion.div>
  )
}
