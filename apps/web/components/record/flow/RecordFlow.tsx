"use client"

import { useCallback, useEffect, useMemo, useReducer, useState } from "react"
import { useTranslations } from "next-intl"
import type { Pebble, PebbleSnap, Visibility } from "@/lib/types"
import type { PebbleDraftPayload } from "@/lib/data/data-provider"
import { useDataProvider } from "@/lib/data/provider-context"
import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { publishErrorMessageKey } from "@/lib/data/compose-error"
import { useComposerDrafts } from "@/lib/hooks/useComposerDrafts"
import { useRecordFlowHistory } from "@/lib/hooks/useRecordFlowHistory"
import { prewarmValenceArt } from "@/lib/valence/stone-art"
import { applyDraftPayload, hasPublishMinimum } from "@/components/record/draft-payload"
import {
  initialFlowState,
  isAnswered,
  optionalButtonIsSkip,
  recordFlowReducer,
} from "@/lib/record/flow"
import { exifCaptureDate } from "@/lib/record/exif-capture-date"
import { COUNTED_STEPS, RECORD_STEPS, type RecordStep } from "@/lib/record/steps"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { RecordFlowChrome } from "./RecordFlowChrome"
import { RecordStepContent } from "./RecordStepContent"
import { RecordStepScaffold, type RecordStepAction } from "./RecordStepScaffold"
import { RecordSuccessStep } from "./steps/RecordSuccessStep"
import { useRecordStepCopy } from "./step-copy"
import type { PhotoUploadState } from "./steps/RecordPhotoStep"

type RecordFlowProps = {
  /** Leave the composer for the Path — after success, discard, or a bare close. */
  onExit: () => void
}

type PublishedPebble = { pebble: Pebble; karmaDelta: number }

/**
 * The step-by-step pebble composer (M58) — the default way to record a pebble
 * on web, aligned with the iOS flow shipped in #724. `QuickPebbleEditor`
 * remains reachable at `/record?composer=form`.
 *
 * Owns the orchestration — photo upload, local autosave, publish — while everything
 * about *the flow itself* (gating, back, skip labels, resume) lives in the pure
 * reducer in `lib/record/flow.ts`, where it is unit tested without rendering.
 *
 * One thing deliberately does not cross from iOS: the haptic on every tap. The
 * Vibration API is unsupported in Safari/iOS and inconsistent elsewhere, so it
 * is an iOS and Android pillar with no honest web equivalent — and substituting
 * an animation for it would be pretending. The web flow is the quiet one.
 */
export function RecordFlow({ onExit }: RecordFlowProps) {
  const t = useTranslations("record.flow")
  const tDraft = useTranslations("record.draft")
  const tPhoto = useTranslations("record.flow.photo")

  const { loading: storeLoading } = useDataProvider()
  const { addPebble, uploadSnap } = usePebbles()
  const { souls } = useSouls()
  const { collections } = useCollections()

  const [flow, dispatch] = useReducer(recordFlowReducer, undefined, () => initialFlowState())

  // Photo state lives here rather than in the reducer: the reducer owns the
  // draft, and a `File` plus its object URL is neither draft nor payload.
  const [photoFile, setPhotoFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | undefined>(undefined)
  const [photoState, setPhotoState] = useState<PhotoUploadState>("idle")

  const [publishing, setPublishing] = useState(false)
  const [publishError, setPublishError] = useState<string | null>(null)
  const [published, setPublished] = useState<PublishedPebble | null>(null)
  const [closeConfirmOpen, setCloseConfirmOpen] = useState(false)

  const goTo = useCallback(
    (step: RecordStep) => {
      // Back out of the success screen leaves the composer rather than walking
      // into it. The pebble is already written, and landing on the privacy step
      // again would offer a Publish button that would write a second one.
      if (published) {
        onExit()
        return
      }
      dispatch({ type: "goTo", step })
    },
    [published, onExit],
  )
  const { goBack } = useRecordFlowHistory(RECORD_STEPS, flow.step, goTo)

  // The valence fan wobbles nine artworks on first sight, which is a visible
  // hitch if it lands the moment step 3 appears. Three steps of runway is
  // plenty, and the prewarm spreads itself across ticks so it never costs a
  // frame here either.
  useEffect(() => prewarmValenceArt(), [])

  // ---------------------------------------------------------------------------
  // Local autosave (M47) — the same coordinator the single-screen composer uses.
  // ---------------------------------------------------------------------------

  const applyPayload = useCallback(
    (payload: PebbleDraftPayload) => {
      // Sanitize against what the user actually still owns: a soul or
      // collection deleted behind the draft's back costs a chip, not an opaque
      // FK error at publish time.
      dispatch({
        type: "hydrate",
        draft: applyDraftPayload(payload, {
          soulIds: new Set(souls.map((s) => s.id)),
          collectionIds: new Set(collections.map((c) => c.id)),
        }),
      })
    },
    [souls, collections],
  )

  const {
    isEmpty,
    restorePromptOpen,
    setRestorePromptOpen,
    restore,
    discardSnapshot,
    consumeAfterPublish,
  } = useComposerDrafts({
    state: flow.draft,
    applyPayload,
    ready: !storeLoading,
  })

  // ---------------------------------------------------------------------------
  // Photo
  // ---------------------------------------------------------------------------

  // Revoke any outstanding object URL on replace and on unmount. (A restored
  // snapshot never carries a photo: autosave excludes media, D3.)
  useEffect(() => {
    return () => {
      if (previewUrl?.startsWith("blob:")) URL.revokeObjectURL(previewUrl)
    }
  }, [previewUrl])

  const upload = useCallback(
    async (file: File) => {
      setPhotoState("uploading")
      try {
        const snap: PebbleSnap = await uploadSnap(file)
        dispatch({ type: "setPhoto", snap, attached: true })
        setPhotoState("uploaded")
      } catch (err) {
        console.error("[record-flow] snap upload failed", err)
        // The photo stays on screen with Retry / Remove: the bytes are still in
        // hand, and silently dropping the user's picture would be worse.
        setPhotoState("failed")
      }
    },
    [uploadSnap],
  )

  const handlePickPhoto = useCallback(
    (file: File) => {
      setPreviewUrl((prev) => {
        if (prev?.startsWith("blob:")) URL.revokeObjectURL(prev)
        return URL.createObjectURL(file)
      })
      setPhotoFile(file)
      dispatch({ type: "setPhoto", snap: undefined, attached: true })

      // Read the capture date before the upload pipeline strips it, so the
      // `when` step is already right by the time the user gets there.
      void (async () => {
        try {
          const captured = exifCaptureDate(await file.arrayBuffer())
          dispatch({ type: "applyCaptureDate", value: captured?.toISOString() ?? null })
        } catch (err) {
          console.warn("[record-flow] could not read the photo's capture date", err)
        }
      })()

      void upload(file)
    },
    [upload],
  )

  const handleRetryPhoto = useCallback(() => {
    if (photoFile) void upload(photoFile)
  }, [photoFile, upload])

  const handleRemovePhoto = useCallback(() => {
    setPreviewUrl((prev) => {
      if (prev?.startsWith("blob:")) URL.revokeObjectURL(prev)
      return undefined
    })
    setPhotoFile(null)
    setPhotoState("idle")
    dispatch({ type: "setPhoto", snap: undefined, attached: false })
  }, [])

  /** Non-null while the attached photo blocks publishing. */
  const blockedMessage = useMemo(() => {
    if (photoState === "uploading") return tPhoto("blockedUploading")
    if (photoState === "failed") return tPhoto("blockedFailed")
    return null
  }, [photoState, tPhoto])

  // ---------------------------------------------------------------------------
  // Publish
  // ---------------------------------------------------------------------------

  const canPublish = blockedMessage === null && hasPublishMinimum(flow.draft)

  const handlePublish = useCallback(async () => {
    if (publishing || !canPublish) return
    setPublishing(true)
    setPublishError(null)

    let karmaDelta = 0
    try {
      const draft = flow.draft
      const pebble = await addPebble(
        {
          name: draft.name.trim(),
          description: draft.description.trim(),
          happened_at: draft.happenedAt,
          intensity: draft.intensity,
          positiveness: draft.valence,
          visibility: draft.visibility,
          emotion_id: draft.emotionId,
          soul_ids: draft.soulIds,
          domain_ids: draft.domainIds,
          collection_ids: draft.collectionIds,
          snaps: draft.pendingSnap ? [draft.pendingSnap] : [],
          cards: [],
        },
        // The success screen shows the amount, so the pill would be redundant.
        { onKarmaEarned: (delta) => { karmaDelta = delta } },
      )

      // The pebble exists, so the draft has served its purpose.
      await consumeAfterPublish()

      setPublished({
        pebble,
        karmaDelta,
      })
      dispatch({ type: "published" })
    } catch (err) {
      // A hard failure never reaches the success step: the flow stays put, so
      // the draft and its local snapshot are untouched.
      console.error("[record-flow] publish failed", err)
      setPublishError(tDraft(publishErrorMessageKey(err)))
    } finally {
      setPublishing(false)
    }
  }, [publishing, canPublish, flow.draft, addPebble, consumeAfterPublish, tDraft])

  // ---------------------------------------------------------------------------
  // Leaving
  // ---------------------------------------------------------------------------

  /** ✕ only asks when there is something to keep. */
  const handleClose = useCallback(() => {
    if (isEmpty) {
      onExit()
      return
    }
    setCloseConfirmOpen(true)
  }, [isEmpty, onExit])

  const handleDiscard = useCallback(() => {
    // The uploaded snap is left to the server-side sweep, exactly as the
    // single-screen composer leaves it when it is abandoned.
    discardSnapshot()
    setCloseConfirmOpen(false)
    onExit()
  }, [discardSnapshot, onExit])

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------

  const copy = useRecordStepCopy(flow.step)

  /** The one action a step offers, if any. Tile steps offer none — the pick is the advance. */
  const action = ((): RecordStepAction | undefined => {
    switch (flow.step) {
      case "emotion":
      case "domain":
      case "success":
        return undefined
      case "when":
      case "valence":
        return {
          kind: "primary",
          label: t("continue"),
          enabled: true,
          onPress: () => dispatch({ type: "advance" }),
        }
      case "name":
        return {
          kind: "primary",
          label: t("continue"),
          enabled: isAnswered(flow),
          onPress: () => dispatch({ type: "advance" }),
        }
      case "privacy":
        return {
          kind: "primary",
          label: t("publish"),
          enabled: canPublish,
          loading: publishing,
          onPress: () => void handlePublish(),
        }
      case "photo":
      case "souls":
      case "collection":
        return {
          kind: "text",
          label: optionalButtonIsSkip(flow) ? t("skip") : t("done"),
          onPress: () => dispatch({ type: "advance" }),
        }
    }
  })()

  return (
    <>
      <section
        aria-label={t("aria")}
        className="mx-auto flex min-h-[calc(100dvh-4rem-var(--safe-area-top)-var(--safe-area-bottom))] w-full max-w-lg flex-col gap-4"
      >
        {flow.step !== "success" && (
          <RecordFlowChrome
            steps={RECORD_STEPS}
            counted={COUNTED_STEPS}
            step={flow.step}
            onBack={goBack}
            onClose={handleClose}
          />
        )}

        {flow.step === "success" && published ? (
          <RecordSuccessStep
            pebble={published.pebble}
            karmaDelta={published.karmaDelta}
            onExit={onExit}
          />
        ) : (
          <RecordStepScaffold title={copy.title} subtitle={copy.subtitle} action={action}>
            <RecordStepContent
              state={flow}
              photo={{ previewUrl, state: photoState }}
              blockedMessage={blockedMessage}
              publishError={publishError}
              onPickPhoto={handlePickPhoto}
              onRetryPhoto={handleRetryPhoto}
              onRemovePhoto={handleRemovePhoto}
              onSetHappenedAt={(value) => dispatch({ type: "setHappenedAt", value })}
              onSetName={(value) => dispatch({ type: "setName", value })}
              onAdvance={() => dispatch({ type: "advance" })}
              onSelectValence={(intensity, valence) =>
                dispatch({ type: "selectValence", intensity, valence })
              }
              onSelectEmotion={(id) => dispatch({ type: "selectEmotion", id })}
              onSelectDomain={(id) => dispatch({ type: "selectDomain", id })}
              onToggleSoul={(id) => dispatch({ type: "toggleSoul", id })}
              onToggleCollection={(id) => dispatch({ type: "toggleCollection", id })}
              onSelectVisibility={(value: Visibility) =>
                dispatch({ type: "selectVisibility", value })
              }
            />
          </RecordStepScaffold>
        )}
      </section>

      {/* Crash-insurance restore, offered once per mount. */}
      <AlertDialog open={restorePromptOpen} onOpenChange={setRestorePromptOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{tDraft("restoreTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{tDraft("restoreDescription")}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={discardSnapshot}>
              {tDraft("restoreDiscard")}
            </AlertDialogCancel>
            <AlertDialogAction onClick={restore}>{tDraft("restoreConfirm")}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Server drafts are retired (#1046, D7): leaving with something typed
          means discarding it or staying to finish. */}
      <AlertDialog open={closeConfirmOpen} onOpenChange={setCloseConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("leaveTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{t("leaveDescription")}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t("keepGoing")}</AlertDialogCancel>
            <Button variant="ghost" onClick={handleDiscard} className="text-destructive">
              {t("discard")}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
