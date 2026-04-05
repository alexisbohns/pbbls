"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { PEBBLE_SHAPES } from "@/lib/config"
import type { MarkStroke } from "@/lib/types"
import { useMarks } from "@/lib/data/useMarks"
import { useHaptics } from "@/lib/hooks/useHaptics"
import { DrawingCanvas } from "@/components/carve/DrawingCanvas"
import { CarveToolbar } from "@/components/carve/CarveToolbar"
import { StampPicker } from "@/components/carve/StampPicker"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
import { MarkSelector } from "@/components/record/MarkSelector"
import type { RecordStepProps } from "@/components/record/types"

type Mode = "draw" | "select"

// Module-level ref so onAdvance can trigger save without prop threading.
// Safe because only one StepGlyph instance exists at a time.
let pendingSave: (() => Promise<void>) | null = null
export function getGlyphPendingSave() { return pendingSave }

export function StepGlyph({ data, onUpdate }: RecordStepProps) {
  const { marks, addMark } = useMarks()
  const { vibrate } = useHaptics()

  const [mode, setMode] = useState<Mode>("draw")
  const [strokes, setStrokes] = useState<MarkStroke[]>([])
  const [strokeWidth, setStrokeWidth] = useState(4)
  const [saving, setSaving] = useState(false)
  const [confirmed, setConfirmed] = useState(false)

  const shape = useMemo(
    () => PEBBLE_SHAPES[Math.floor(Math.random() * PEBBLE_SHAPES.length)],
    [],
  )

  // Find the currently selected mark for preview
  const selectedMark = useMemo(
    () => marks.find((m) => m.id === data.mark_id),
    [marks, data.mark_id],
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

  const handleConfirmDrawing = useCallback(async () => {
    if (saving || strokes.length === 0) return
    setSaving(true)
    try {
      const mark = await addMark({
        shape_id: shape.id,
        strokes,
        viewBox: shape.viewBox,
      })
      vibrate([10, 50, 10])
      onUpdate({ mark_id: mark.id })
      setConfirmed(true)
    } finally {
      setSaving(false)
    }
  }, [saving, strokes, addMark, shape, vibrate, onUpdate])

  // Expose pending save for the onAdvance hook in RecordStepper
  useEffect(() => {
    pendingSave = strokes.length > 0 && !confirmed ? handleConfirmDrawing : null
    return () => { pendingSave = null }
  }, [strokes, confirmed, handleConfirmDrawing])

  const handleDrawAgain = useCallback(() => {
    setStrokes([])
    setConfirmed(false)
    onUpdate({ mark_id: undefined })
  }, [onUpdate])

  const handleSelectMark = useCallback(
    (markId: string) => {
      onUpdate({ mark_id: markId })
      vibrate(10)
    },
    [onUpdate, vibrate],
  )

  const handleModeChange = useCallback((newMode: Mode) => {
    setMode(newMode)
  }, [])

  return (
    <fieldset className="space-y-4">
      <legend className="text-sm font-medium">
        Draw or choose a glyph
      </legend>
      <p className="text-sm text-muted-foreground">
        Add a symbol to your pebble. This step is optional.
      </p>

      {/* Mode tabs */}
      <div role="tablist" aria-label="Glyph mode" className="flex gap-1">
        <button
          type="button"
          role="tab"
          id="tab-draw"
          aria-selected={mode === "draw"}
          aria-controls="panel-draw"
          tabIndex={mode === "draw" ? 0 : -1}
          onClick={() => handleModeChange("draw")}
          onKeyDown={(e) => {
            if (e.key === "ArrowRight" || e.key === "ArrowLeft") {
              e.preventDefault()
              handleModeChange(mode === "draw" ? "select" : "draw")
            }
          }}
          className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
            mode === "draw"
              ? "bg-primary text-primary-foreground"
              : "bg-muted text-muted-foreground hover:bg-muted/80"
          }`}
        >
          Draw
        </button>
        <button
          type="button"
          role="tab"
          id="tab-select"
          aria-selected={mode === "select"}
          aria-controls="panel-select"
          tabIndex={mode === "select" ? 0 : -1}
          onClick={() => handleModeChange("select")}
          onKeyDown={(e) => {
            if (e.key === "ArrowRight" || e.key === "ArrowLeft") {
              e.preventDefault()
              handleModeChange(mode === "draw" ? "select" : "draw")
            }
          }}
          className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
            mode === "select"
              ? "bg-primary text-primary-foreground"
              : "bg-muted text-muted-foreground hover:bg-muted/80"
          }`}
        >
          Choose existing
        </button>
      </div>

      {/* Draw panel */}
      {mode === "draw" && (
        <div
          role="tabpanel"
          id="panel-draw"
          aria-labelledby="tab-draw"
          className="space-y-4"
        >
          {confirmed && selectedMark ? (
            <div className="flex flex-col items-center gap-4">
              <p className="text-sm font-medium" aria-live="polite">
                Glyph attached
              </p>
              <GlyphPreview
                mark={selectedMark}
                className="w-full max-w-[200px] aspect-square"
              />
              <button
                type="button"
                onClick={handleDrawAgain}
                className="text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground"
              >
                Draw a different glyph
              </button>
            </div>
          ) : (
            <>
              <figure className="flex flex-col items-center gap-2">
                <DrawingCanvas
                  shape={shape}
                  strokes={strokes}
                  strokeWidth={strokeWidth}
                  onStrokeComplete={handleStrokeComplete}
                />
                <figcaption className="sr-only">
                  Draw a symbol on the pebble surface. Your strokes are clipped
                  to the stone outline.
                </figcaption>
              </figure>

              <p className="sr-only" aria-live="polite">
                {strokes.length === 0
                  ? "No strokes drawn"
                  : `${strokes.length} stroke${strokes.length === 1 ? "" : "s"} drawn`}
              </p>

              <CarveToolbar
                strokeCount={strokes.length}
                strokeWidth={strokeWidth}
                onUndo={handleUndo}
                onClear={handleClear}
                onWidthChange={setStrokeWidth}
                onSave={handleConfirmDrawing}
                saving={saving}
                showSave={false}
              />

              <StampPicker onSelect={handleStampSelect} />
            </>
          )}
        </div>
      )}

      {/* Select panel */}
      {mode === "select" && (
        <div
          role="tabpanel"
          id="panel-select"
          aria-labelledby="tab-select"
          className="space-y-3"
        >
          <MarkSelector
            marks={marks}
            selectedMarkId={data.mark_id}
            onSelect={handleSelectMark}
          />
        </div>
      )}
    </fieldset>
  )
}
