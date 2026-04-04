"use client"

import { useCallback, useRef, useState } from "react"
import { ImagePlus, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { compressImage } from "@/lib/utils/image-compress"
import type { RecordStepProps } from "@/components/record/types"

const MAX_INSTANTS = 3

export function StepInstants({ data, onUpdate }: RecordStepProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [processing, setProcessing] = useState(false)
  const [dragging, setDragging] = useState(false)

  const remaining = MAX_INSTANTS - data.instants.length

  const handleFiles = useCallback(
    async (files: FileList | null) => {
      if (!files || files.length === 0) return

      const slots = MAX_INSTANTS - data.instants.length
      if (slots <= 0) return

      const batch = Array.from(files).slice(0, slots)

      setProcessing(true)
      try {
        const compressed = await Promise.all(batch.map(compressImage))
        onUpdate({ instants: [...data.instants, ...compressed] })
      } finally {
        setProcessing(false)
      }
    },
    [data.instants, onUpdate],
  )

  const handleRemove = useCallback(
    (index: number) => {
      onUpdate({ instants: data.instants.filter((_, i) => i !== index) })
    },
    [data.instants, onUpdate],
  )

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setDragging(true)
  }, [])

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setDragging(false)
  }, [])

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      setDragging(false)
      void handleFiles(e.dataTransfer.files)
    },
    [handleFiles],
  )

  return (
    <fieldset className="space-y-6">
      <legend className="text-lg font-semibold">Instants</legend>
      <p className="text-sm text-muted-foreground">
        Attach up to {MAX_INSTANTS} photos to this moment.
      </p>

      {/* Hidden file input */}
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        multiple
        className="hidden"
        aria-hidden="true"
        tabIndex={-1}
        onChange={(e) => {
          void handleFiles(e.target.files)
          e.target.value = ""
        }}
      />

      {/* Drop zone with empty state or preview grid */}
      {data.instants.length === 0 ? (
        <button
          type="button"
          disabled={processing}
          onClick={() => inputRef.current?.click()}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          aria-label="Add photos"
          className={`flex w-full flex-col items-center justify-center gap-3 rounded-lg border-2 border-dashed py-12 text-center transition-colors ${
            dragging
              ? "border-primary bg-primary/5"
              : "border-muted-foreground/25 hover:border-muted-foreground/50"
          }`}
        >
          <div className="flex size-10 items-center justify-center rounded-full bg-muted">
            <ImagePlus className="size-5 text-muted-foreground" aria-hidden="true" />
          </div>
          <div className="space-y-1">
            <p className="text-sm font-medium">Add photos</p>
            <p className="text-xs text-muted-foreground">
              Drag and drop or tap to browse
            </p>
          </div>
        </button>
      ) : (
        <div
          onDragOver={remaining > 0 ? handleDragOver : undefined}
          onDragLeave={remaining > 0 ? handleDragLeave : undefined}
          onDrop={remaining > 0 ? handleDrop : undefined}
          className={`rounded-lg border-2 border-dashed p-3 transition-colors ${
            dragging
              ? "border-primary bg-primary/5"
              : "border-muted-foreground/25"
          }`}
        >
          <ul className="grid grid-cols-3 gap-3" aria-label="Selected instants">
            {data.instants.map((uri, i) => (
              <li key={i} className="group relative">
                <img
                  src={uri}
                  alt={`Instant ${i + 1}`}
                  className="aspect-square w-full rounded-lg object-cover"
                />
                <button
                  type="button"
                  onClick={() => handleRemove(i)}
                  aria-label={`Remove instant ${i + 1}`}
                  className="absolute -right-1.5 -top-1.5 flex size-6 items-center justify-center rounded-full bg-destructive text-destructive-foreground shadow-sm transition-opacity group-focus-within:opacity-100 group-hover:opacity-100 md:opacity-0"
                >
                  <X className="size-3.5" />
                </button>
              </li>
            ))}
          </ul>

          {remaining > 0 && (
            <div className="mt-3 flex justify-center">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="gap-2"
                disabled={processing}
                onClick={() => inputRef.current?.click()}
              >
                <ImagePlus className="size-4" aria-hidden="true" />
                Add more
              </Button>
            </div>
          )}
        </div>
      )}

      {processing && (
        <p className="text-sm text-muted-foreground" aria-live="polite">
          Compressing…
        </p>
      )}

      {/* Counter */}
      <p className="text-center text-xs text-muted-foreground" aria-live="polite">
        {data.instants.length} / {MAX_INSTANTS}
      </p>
    </fieldset>
  )
}
