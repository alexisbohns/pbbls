"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import type { PebbleSnap } from "@/lib/types"
import type { CaptureAction } from "@/lib/record/capture"
import { exifCaptureDate } from "@/lib/record/exif-capture-date"
import { createLatestRequest } from "@/lib/utils/latest-request"

type PhotoAction = Extract<CaptureAction, { type: "photo" }>

type UseCapturePhotoOptions = {
  uploadSnap: (file: File) => Promise<PebbleSnap>
  /** Best-effort removal of uploaded files; never throws. */
  removeSnapFiles: (storagePaths: string[]) => Promise<void>
  /** Mirrors the photo's state into the capture reducer. */
  onPhoto: (action: PhotoAction) => void
  /** The picked photo's EXIF date, or null when it has none. */
  onCaptureDate: (iso: string | null) => void
  /**
   * True while a save is in flight: the payload already carries the photo on
   * screen, so neither a new pick nor Remove may change it under the save.
   */
  locked: boolean
}

/**
 * Capture's photo (#1050): pick → preview → EXIF date → upload, with Retry,
 * Remove and the close dialog's Discard. Ported from the M58 record flow, with
 * iOS `SnapUploadCoordinator`'s cleanup rules:
 *
 * - One token per pick (`createLatestRequest`), so a quick re-pick can't stage
 *   the earlier photo: a stale upload deletes its own files instead.
 * - A photo replaced, removed or discarded before any save has its files
 *   deleted (best-effort; the orphan sweep is the backstop).
 * - A failed save keeps the upload for the retry. A successful one hands the
 *   files to the moment (`commit`), and nothing here touches them again.
 * - Leaving the flow any other way (browser Back past the first step) is a
 *   discard: there is no Capture autosave to come back to (PD-W7).
 */
export function useCapturePhoto(options: UseCapturePhotoOptions) {
  const [previewUrl, setPreviewUrl] = useState<string | undefined>(undefined)

  // The data hooks hand out fresh functions every render; the handlers below
  // read the latest ones through a ref so they (and the unmount cleanup) stay
  // stable instead of re-running on every render.
  const optionsRef = useRef(options)
  useEffect(() => {
    optionsRef.current = options
  })

  const requestsRef = useRef(createLatestRequest())
  /** The uploaded snap no saved moment holds yet: the files a discard deletes. */
  const stagedRef = useRef<PebbleSnap | null>(null)
  const fileRef = useRef<File | null>(null)
  const previewRef = useRef<string | undefined>(undefined)

  const removeFiles = useCallback((snap: PebbleSnap) => {
    void optionsRef.current.removeSnapFiles([snap.storage_path])
  }, [])

  const discardStaged = useCallback(() => {
    const staged = stagedRef.current
    stagedRef.current = null
    if (staged) removeFiles(staged)
  }, [removeFiles])

  const replacePreview = useCallback((next: string | undefined) => {
    if (previewRef.current) URL.revokeObjectURL(previewRef.current)
    previewRef.current = next
    setPreviewUrl(next)
  }, [])

  useEffect(() => {
    const requests = requestsRef.current
    return () => {
      requests.invalidate()
      if (previewRef.current) URL.revokeObjectURL(previewRef.current)
      // A save in flight may still succeed with these files, so they stay.
      if (!optionsRef.current.locked) discardStaged()
    }
  }, [discardStaged])

  const upload = useCallback(
    async (file: File, token: number) => {
      const { onPhoto, uploadSnap } = optionsRef.current
      onPhoto({ type: "photo", state: "uploading" })
      try {
        const snap = await uploadSnap(file)
        if (!requestsRef.current.isLatest(token)) {
          // A newer pick, a Remove, a Discard or leaving superseded this one.
          removeFiles(snap)
          return
        }
        stagedRef.current = snap
        optionsRef.current.onPhoto({ type: "photo", state: "uploaded", snap })
      } catch (err) {
        if (!requestsRef.current.isLatest(token)) return
        // The photo stays on screen with Retry / Remove: the bytes are still
        // in hand, and silently dropping the user's picture would be worse.
        console.error("[capture] snap upload failed", err)
        optionsRef.current.onPhoto({ type: "photo", state: "failed" })
      }
    },
    [removeFiles],
  )

  const pick = useCallback(
    (file: File) => {
      if (optionsRef.current.locked) return
      const token = requestsRef.current.begin()
      discardStaged()
      fileRef.current = file
      replacePreview(URL.createObjectURL(file))

      // Read the capture date before the upload pipeline strips it, so the
      // when step is already right by the time the user gets there.
      void (async () => {
        try {
          const captured = exifCaptureDate(await file.arrayBuffer())
          if (requestsRef.current.isLatest(token)) {
            optionsRef.current.onCaptureDate(captured?.toISOString() ?? null)
          }
        } catch (err) {
          console.warn("[capture] could not read the photo's capture date", err)
          // An unreadable file has no date to credit, like one without EXIF.
          if (requestsRef.current.isLatest(token)) optionsRef.current.onCaptureDate(null)
        }
      })()

      void upload(file, token)
    },
    [discardStaged, replacePreview, upload],
  )

  const retry = useCallback(() => {
    if (optionsRef.current.locked || !fileRef.current) return
    void upload(fileRef.current, requestsRef.current.begin())
  }, [upload])

  const remove = useCallback(() => {
    if (optionsRef.current.locked) return
    requestsRef.current.invalidate()
    discardStaged()
    fileRef.current = null
    replacePreview(undefined)
    optionsRef.current.onPhoto({ type: "photo", state: "none" })
  }, [discardStaged, replacePreview])

  /** The close dialog's Discard: deletes whatever this capture uploaded. */
  const discard = useCallback(() => {
    requestsRef.current.invalidate()
    discardStaged()
  }, [discardStaged])

  /** After a successful save: the files are the moment's now. */
  const commit = useCallback(() => {
    requestsRef.current.invalidate()
    stagedRef.current = null
  }, [])

  return { previewUrl, pick, retry, remove, discard, commit }
}
