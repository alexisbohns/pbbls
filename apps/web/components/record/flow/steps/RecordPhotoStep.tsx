"use client"

import { ImagePlus, Loader2 } from "lucide-react"
import { useRef } from "react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"

export type PhotoUploadState = "idle" | "uploading" | "uploaded" | "failed"

type RecordPhotoStepProps = {
  previewUrl: string | undefined
  state: PhotoUploadState
  onPick: (file: File) => void
  onRetry: () => void
  onRemove: () => void
}

/**
 * Step 0 — the picture the flow starts from.
 *
 * Does not auto-advance on pick: the upload runs in the background and its
 * state belongs on screen while the user is still looking at the photo, so
 * picking swaps Skip for Done and waits.
 */
export function RecordPhotoStep({
  previewUrl,
  state,
  onPick,
  onRetry,
  onRemove,
}: RecordPhotoStepProps) {
  const t = useTranslations("record.flow.photo")
  const inputRef = useRef<HTMLInputElement>(null)

  return (
    <div className="flex flex-col items-center gap-4">
      {previewUrl ? (
        <>
          {/* eslint-disable-next-line @next/next/no-img-element -- object URL */}
          <img
            src={previewUrl}
            alt={t("alt")}
            className="max-h-72 w-full rounded-3xl object-cover"
          />
          {state === "uploading" && (
            <p
              className="flex items-center gap-2 text-sm text-muted-foreground"
              aria-live="polite"
            >
              <Loader2 className="size-4 animate-spin" aria-hidden />
              {t("uploading")}
            </p>
          )}
          {state === "uploaded" && (
            <Button variant="ghost" onClick={() => inputRef.current?.click()}>
              {t("chooseAnother")}
            </Button>
          )}
          {state === "failed" && (
            <div className="flex flex-col items-center gap-2">
              <p role="alert" className="text-sm text-destructive">
                {t("failed")}
              </p>
              <div className="flex gap-2">
                <Button variant="outline" onClick={onRetry}>
                  {t("retry")}
                </Button>
                <Button variant="ghost" onClick={onRemove} className="text-muted-foreground">
                  {t("remove")}
                </Button>
              </div>
            </div>
          )}
        </>
      ) : (
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          className="flex h-64 w-full flex-col items-center justify-center gap-2 rounded-3xl border-2 border-dashed border-muted-foreground/30 text-muted-foreground outline-none transition-colors hover:border-muted-foreground/50 hover:bg-muted/30 focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ImagePlus className="size-10" aria-hidden />
          <span className="text-sm font-medium">{t("add")}</span>
        </button>
      )}

      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        className="hidden"
        aria-hidden="true"
        tabIndex={-1}
        onChange={(e) => {
          const file = e.target.files?.[0]
          if (file) onPick(file)
          e.target.value = ""
        }}
      />
    </div>
  )
}
