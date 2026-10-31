"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { toast } from "sonner"
import { Camera } from "@pbbls/icons"
import { captureSavePayload } from "@/lib/data/moment-payloads"
import { momentErrorKey } from "@/lib/data/moment-write-error"
import { useMoments } from "@/lib/data/useMoments"
import { useCapturePhoto } from "@/lib/hooks/useCapturePhoto"
import type { PebbleSnap } from "@/lib/types"
import { quickActionCell } from "./quick-action-cell"

/**
 * Camera (#1384, spec D11; iOS D12): the browser's camera or photo picker
 * behind the cell, then one bare moment with the photo, dated by its EXIF date
 * or now. The toast says it was saved and offers to name it. The photo's
 * upload, EXIF and cleanup rules are `useCapturePhoto`'s: a failed upload or
 * save says so and keeps nothing.
 */
export function CameraAction() {
  const t = useTranslations("home.actions")
  const tErrors = useTranslations("moment.errors")
  const router = useRouter()
  const { saveMoment, uploadSnap, removeSnapFiles } = useMoments()
  const [saving, setSaving] = useState(false)
  const snapRef = useRef<PebbleSnap | null>(null)
  /** undefined while the photo's EXIF date is still being read. */
  const dateRef = useRef<string | null | undefined>(undefined)
  const toastRef = useRef<string | number | null>(null)
  const savingRef = useRef(false)
  const photoRef = useRef<ReturnType<typeof useCapturePhoto> | null>(null)

  const reset = useCallback(() => {
    snapRef.current = null
    dateRef.current = undefined
    toastRef.current = null
    savingRef.current = false
    setSaving(false)
  }, [])

  const fail = useCallback(
    (message: string) => {
      toast.error(message, { id: toastRef.current ?? undefined })
      reset()
    },
    [reset],
  )

  const saveWhenReady = useCallback(async () => {
    const snap = snapRef.current
    if (!snap || dateRef.current === undefined || savingRef.current) return
    savingRef.current = true
    setSaving(true)
    let id: string
    try {
      id = await saveMoment(captureSavePayload({ name: "", happenedAt: dateRef.current ?? new Date().toISOString(), snap }))
    } catch (err) {
      console.error("[home] camera save_moment failed", err)
      photoRef.current?.discard()
      fail(tErrors(momentErrorKey(err)))
      return
    }
    photoRef.current?.commit()
    const momentId = id.toLowerCase()
    toast.success(t("cameraSaved"), {
      id: toastRef.current ?? undefined,
      action: { label: t("nameIt"), onClick: () => router.push(`/pebble/${momentId}`) },
    })
    reset()
  }, [saveMoment, fail, reset, router, t, tErrors])

  const photo = useCapturePhoto({
    uploadSnap,
    removeSnapFiles,
    locked: saving,
    onCaptureDate: (iso) => {
      dateRef.current = iso
      void saveWhenReady()
    },
    onPhoto: (action) => {
      if (action.state === "uploaded") {
        snapRef.current = action.snap
        void saveWhenReady()
      } else if (action.state === "failed") {
        fail(t("cameraFailed"))
      }
    },
  })
  useEffect(() => {
    photoRef.current = photo
  })

  return (
    <label className={`${quickActionCell(0)} cursor-pointer`}>
      <input
        type="file"
        accept="image/*"
        capture="environment"
        className="sr-only"
        disabled={saving}
        onChange={(e) => {
          const file = e.target.files?.[0]
          e.target.value = ""
          if (!file) return
          toastRef.current = toast.loading(t("cameraSaving"))
          photo.pick(file)
        }}
      />
      <Camera className="size-7" />
      <span>{t("camera")}</span>
    </label>
  )
}
