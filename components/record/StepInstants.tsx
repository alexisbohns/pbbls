"use client"

import { useCallback, useRef, useState } from "react"
import { Camera, ImagePlus, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { compressImage } from "@/lib/utils/image-compress"
import type { RecordStepProps } from "@/components/record/types"

const MAX_INSTANTS = 3

export function StepInstants({ data, onUpdate }: RecordStepProps) {
  const cameraRef = useRef<HTMLInputElement>(null)
  const libraryRef = useRef<HTMLInputElement>(null)
  const [processing, setProcessing] = useState(false)

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

  return (
    <fieldset className="space-y-6">
      <legend className="text-lg font-semibold">Instants</legend>
      <p className="text-sm text-muted-foreground">
        Attach up to {MAX_INSTANTS} photos to this moment.
      </p>

      {/* Hidden file inputs */}
      <input
        ref={cameraRef}
        type="file"
        accept="image/*"
        capture="environment"
        className="hidden"
        aria-hidden="true"
        tabIndex={-1}
        onChange={(e) => {
          void handleFiles(e.target.files)
          e.target.value = ""
        }}
      />
      <input
        ref={libraryRef}
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

      {/* Action buttons */}
      {remaining > 0 && (
        <div className="flex gap-3">
          <Button
            type="button"
            variant="outline"
            className="h-11 flex-1 gap-2 md:h-8"
            disabled={processing}
            onClick={() => cameraRef.current?.click()}
          >
            <Camera className="size-4" aria-hidden="true" />
            Take photo
          </Button>
          <Button
            type="button"
            variant="outline"
            className="h-11 flex-1 gap-2 md:h-8"
            disabled={processing}
            onClick={() => libraryRef.current?.click()}
          >
            <ImagePlus className="size-4" aria-hidden="true" />
            Choose file
          </Button>
        </div>
      )}

      {processing && (
        <p className="text-sm text-muted-foreground" aria-live="polite">
          Compressing…
        </p>
      )}

      {/* Preview grid */}
      {data.instants.length > 0 && (
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
      )}

      {/* Counter */}
      <p className="text-center text-xs text-muted-foreground" aria-live="polite">
        {data.instants.length} / {MAX_INSTANTS}
      </p>
    </fieldset>
  )
}
