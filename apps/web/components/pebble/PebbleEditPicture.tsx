"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { ImagePlus, Loader2, AlertCircle } from "lucide-react"
import { useTranslations } from "next-intl"
import type { PebbleSnap } from "@/lib/types"
import { Sheet, SheetContent, SheetClose } from "@/components/ui/sheet"
import { cn } from "@/lib/utils"

// ---------------------------------------------------------------------------
// Snap staging hook — mirrors iOS `SnapUploadCoordinator`. Owns:
//   - the local blob preview URL while an upload is in flight,
//   - the uploading/failed states (so the parent can gate Save),
//   - cleanup of orphaned uploads on Replace/Remove/Discard.
//
// The hook does NOT persist anything — committing the staged snap happens in
// the Save flow via `compose-pebble-update`. It only handles the in-edit
// lifecycle: any snap uploaded during the edit session that is not the
// pebble's original is "uncommitted" and must be removed from Storage if the
// user cancels/replaces it.
// ---------------------------------------------------------------------------

type SnapStagingOptions = {
  originalSnap: PebbleSnap | null
  originalInstantUrl: string | null
  draftSnap: PebbleSnap | null
  setDraftSnap: (snap: PebbleSnap | null) => void
  uploadSnap: (file: File) => Promise<PebbleSnap>
  deletePebbleMedia: (snapId: string) => Promise<void>
}

export type SnapStagingResult = {
  displayUrl: string | null
  uploading: boolean
  failed: boolean
  pickFile: (file: File) => Promise<void>
  remove: () => Promise<void>
  cleanup: () => Promise<void>
}

export function useSnapStaging({
  originalSnap,
  originalInstantUrl,
  draftSnap,
  setDraftSnap,
  uploadSnap,
  deletePebbleMedia,
}: SnapStagingOptions): SnapStagingResult {
  const [uploading, setUploading] = useState(false)
  const [failed, setFailed] = useState(false)
  // The blob URL for the file the user just picked. Held separately from the
  // draft snap because the snap row only exists after `uploadSnap` returns;
  // showing the blob first gives an instant preview while the network call
  // resolves.
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)

  const previewUrlRef = useRef<string | null>(null)
  previewUrlRef.current = previewUrl

  useEffect(() => {
    return () => {
      if (previewUrlRef.current) {
        URL.revokeObjectURL(previewUrlRef.current)
      }
    }
  }, [])

  const isStagedUncommitted = useCallback(
    (snap: PebbleSnap | null): boolean => {
      if (!snap) return false
      // The original snap (if any) is the only "committed" one; anything else
      // was uploaded during this edit session and is therefore orphan-eligible
      // if we don't end up saving with it.
      return snap.id !== originalSnap?.id
    },
    [originalSnap],
  )

  const removeFromStorage = useCallback(
    async (snap: PebbleSnap) => {
      try {
        await deletePebbleMedia(snap.id)
      } catch (err) {
        // The snap row may not exist if upload completed but the row was
        // never persisted (never happens with our flow today — uploadSnap
        // only writes to Storage, not to the DB). Either way, swallow the
        // RPC error so the user-visible cancel/replace path doesn't fail.
        console.warn("[pebble-edit] staged snap cleanup failed", err)
      }
    },
    [deletePebbleMedia],
  )

  const pickFile = useCallback(
    async (file: File) => {
      // Tear down any previously staged-but-uncommitted snap before
      // replacing — prevents storage orphans across multiple Replace cycles.
      if (isStagedUncommitted(draftSnap) && draftSnap) {
        void removeFromStorage(draftSnap)
      }
      // Free the previous blob preview if any.
      if (previewUrl) URL.revokeObjectURL(previewUrl)
      const nextPreview = URL.createObjectURL(file)
      setPreviewUrl(nextPreview)
      setUploading(true)
      setFailed(false)
      try {
        const snap = await uploadSnap(file)
        setDraftSnap(snap)
      } catch (err) {
        console.error("[pebble-edit] snap upload failed", err)
        setFailed(true)
      } finally {
        setUploading(false)
      }
    },
    [draftSnap, isStagedUncommitted, previewUrl, removeFromStorage, setDraftSnap, uploadSnap],
  )

  const remove = useCallback(async () => {
    if (isStagedUncommitted(draftSnap) && draftSnap) {
      void removeFromStorage(draftSnap)
    }
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl)
      setPreviewUrl(null)
    }
    setFailed(false)
    setDraftSnap(null)
  }, [draftSnap, isStagedUncommitted, previewUrl, removeFromStorage, setDraftSnap])

  const cleanup = useCallback(async () => {
    if (isStagedUncommitted(draftSnap) && draftSnap) {
      void removeFromStorage(draftSnap)
    }
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl)
      setPreviewUrl(null)
    }
  }, [draftSnap, isStagedUncommitted, previewUrl, removeFromStorage])

  // Decide which image URL to show:
  //   - while uploading and we have a preview blob, show the preview;
  //   - if the draft snap is the original, fall back to the signed URL;
  //   - if the draft snap is a freshly uploaded one, show the preview blob;
  //   - otherwise nothing.
  let displayUrl: string | null = null
  if (uploading && previewUrl) {
    displayUrl = previewUrl
  } else if (draftSnap && originalSnap && draftSnap.id === originalSnap.id) {
    displayUrl = originalInstantUrl
  } else if (draftSnap && previewUrl) {
    displayUrl = previewUrl
  }

  return {
    displayUrl,
    uploading,
    failed,
    pickFile,
    remove,
    cleanup,
  }
}

// ---------------------------------------------------------------------------
// PebbleEditPicture — the hero picture slot in Edit mode.
// 200×193 slot, image at 150×150, rotated -5°, radius 17. When no picture
// is staged the slot renders a dashed placeholder with a `ImagePlus` icon.
// Tapping a filled picture opens an action sheet with Replace / Remove.
// ---------------------------------------------------------------------------

type PebbleEditPictureProps = {
  displayUrl: string | null
  uploading: boolean
  failed: boolean
  onPick: (file: File) => void
  onRemove: () => void
  className?: string
}

export function PebbleEditPicture({
  displayUrl,
  uploading,
  failed,
  onPick,
  onRemove,
  className,
}: PebbleEditPictureProps) {
  const t = useTranslations("pebble.edit")
  const [sheetOpen, setSheetOpen] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const triggerPick = () => inputRef.current?.click()

  const handleFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = "" // allow picking the same file twice
    if (file) onPick(file)
  }

  return (
    <div className={cn("relative h-[193px] w-[200px]", className)}>
      {displayUrl ? (
        <>
          <button
            type="button"
            onClick={() => setSheetOpen(true)}
            aria-label={t("pictureFilledAria")}
            className="block size-[150px] overflow-hidden rounded-[17px] -rotate-5 outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            {/* eslint-disable-next-line @next/next/no-img-element -- preview blob URL */}
            <img
              src={displayUrl}
              alt=""
              className={cn(
                "size-full object-cover transition-opacity",
                uploading && "opacity-60",
              )}
            />
          </button>
          {uploading && (
            <span
              className="pointer-events-none absolute left-[60px] top-[60px] grid size-8 place-items-center rounded-full bg-background/80 text-foreground"
              aria-label={t("uploading")}
            >
              <Loader2 className="size-4 animate-spin" aria-hidden />
            </span>
          )}
          {failed && (
            <span
              className="pointer-events-none absolute left-[58px] top-[58px] grid size-9 place-items-center rounded-full bg-destructive/90 text-destructive-foreground"
              aria-label={t("uploadFailed")}
              role="alert"
            >
              <AlertCircle className="size-4" aria-hidden />
            </span>
          )}
          <Sheet open={sheetOpen} onOpenChange={setSheetOpen}>
            <SheetContent className="pb-6">
              <div className="flex flex-col gap-2">
                <SheetClose
                  variant="ghost"
                  size="lg"
                  className="justify-start"
                  onClick={() => {
                    setSheetOpen(false)
                    triggerPick()
                  }}
                >
                  {t("replacePhoto")}
                </SheetClose>
                <SheetClose
                  variant="ghost"
                  size="lg"
                  className="justify-start text-destructive"
                  onClick={() => {
                    setSheetOpen(false)
                    onRemove()
                  }}
                >
                  {t("removePhoto")}
                </SheetClose>
              </div>
            </SheetContent>
          </Sheet>
        </>
      ) : (
        <button
          type="button"
          onClick={triggerPick}
          aria-label={t("pictureEmptyAria")}
          className="grid size-[150px] -rotate-5 place-items-center rounded-[17px] border-2 border-dashed border-muted-foreground/30 text-muted-foreground/70 transition-colors hover:border-muted-foreground/60 hover:text-foreground active:scale-[0.98] outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ImagePlus className="size-7" aria-hidden />
        </button>
      )}
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={handleFile}
      />
    </div>
  )
}
