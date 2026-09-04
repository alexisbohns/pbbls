"use client"

import { useCallback, useEffect, useMemo, useReducer, useState } from "react"
import { useTranslations } from "next-intl"
import type { Mark, Pebble, PebbleSnap, Visibility } from "@/lib/types"
import type { PebbleDraftPayload } from "@/lib/data/data-provider"
import { useDataProvider } from "@/lib/data/provider-context"
import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { useComposerDrafts } from "@/lib/hooks/useComposerDrafts"
import { useRecordFlowHistory } from "@/lib/hooks/useRecordFlowHistory"
import { prewarmValenceArt } from "@/lib/valence/stone-art"
import { applyDraftPayload } from "@/components/record/draft-payload"
import {
  initialFlowState,
  isAnswered,
  optionalButtonIsSkip,
  recordFlowReducer,
} from "@/lib/record/flow"
import { exifCaptureDate } from "@/lib/record/exif-capture-date"
import type { RecordStep } from "@/lib/record/steps"
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
  /** Resumed server draft (M47): its payload enters the flow at the first gap. */
  draftId?: string
  initialPayload?: PebbleDraftPayload
  /** Leave the composer for the Path — after success, discard, or a bare close. */
  onExit: () => void
  /** Leave for /drafts after a deliberate "save as draft". */
  onDraftSaved: () => void
}

type PublishedPebble = { pebble: Pebble; mark: Mark | null; karmaDelta: number }

/**
 * The step-by-step pebble composer (M58) — the default way to record a pebble
 * on web, aligned with the iOS flow shipped in #724. `QuickPebbleEditor`
 * remains reachable at `/record?composer=form`.
 *
 * Owns the orchestration — photo upload, drafts, publish — while everything
 * about *the flow itself* (gating, back, skip labels, resume) lives in the pure
 * reducer in `lib/record/flow.ts`, where it is unit tested without rendering.
 *
 * One thing deliberately does not cross from iOS: the haptic on every tap. The
 * Vibration API is unsupported in Safari/iOS and inconsistent elsewhere, so it
 * is an iOS and Android pillar with no honest web equivalent — and substituting
 * an animation for it would be pretending. The web flow is the quiet one.
 */
export function RecordFlow({ draftId, initialPayload, onExit, onDraftSaved }: RecordFlowProps) {
  const t = useTranslations("record.flow")
  const tDraft = useTranslations("record.draft")
  const tPhoto = useTranslations("record.flow.photo")

  const { provider, loading: storeLoading } = useDataProvider()
  const { addPebble, uploadSnap } = usePebbles()
  const { souls } = useSouls()
  const { collections } = useCollections()
  const { glyphs: marks } = useUsableGlyphs()

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
  const [savingDraft, setSavingDraft] = useState(false)
  const [draftError, setDraftError] = useState<string | null>(null)

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
  const { goBack } = useRecordFlowHistory(flow.step, goTo)

  // The valence fan wobbles nine artworks on first sight, which is a visible
  // hitch if it lands the moment step 3 appears. Three steps of runway is
  // plenty, and the prewarm spreads itself across ticks so it never costs a
  // frame here either.
  useEffect(() => prewarmValenceArt(), [])

  // ---------------------------------------------------------------------------
  // Drafts (M47) — the same coordinator the single-screen composer uses.
  // ---------------------------------------------------------------------------

  const applyPayload = useCallback(
    (payload: PebbleDraftPayload) => {
      // Sanitize against what the user actually still owns: a soul or glyph
      // deleted behind the draft's back costs a chip, not an opaque FK error at
      // publish time.
      dispatch({
        type: "hydrate",
        draft: applyDraftPayload(payload, {
          soulIds: new Set(souls.map((s) => s.id)),
          collectionIds: new Set(collections.map((c) => c.id)),
          markIds: new Set(marks.map((m) => m.id)),
        }),
      })
    },
    [souls, collections, marks],
  )

  const {
    isEmpty,
    restorePromptOpen,
    setRestorePromptOpen,
    restore,
    discardSnapshot,
    saveAsDraft,
    consumeAfterPublish,
  } = useComposerDrafts({
    draftId,
    initialPayload,
    state: flow.draft,
    applyPayload,
    ready: !storeLoading,
  })

  // ---------------------------------------------------------------------------
  // Photo
  // ---------------------------------------------------------------------------

  // Revoke any outstanding object URL on replace and on unmount. A signed URL
  // for a resumed draft's photo is not a blob and is left alone.
  useEffect(() => {
    return () => {
      if (previewUrl?.startsWith("blob:")) URL.revokeObjectURL(previewUrl)
    }
  }, [previewUrl])

  // A resumed draft's photo is already in Storage but has no `blob:` preview —
  // mint a signed URL so the photo step shows it (mirrors the edit screen).
  const resumedSnap = flow.draft.pendingSnap
  useEffect(() => {
    if (!provider || !resumedSnap || previewUrl) return
    let cancelled = false
    void (async () => {
      // Deferred past the synchronous render boundary (mirrors
      // usePebbleDrafts.ts) to satisfy react-hooks/set-state-in-effect.
      await Promise.resolve()
      if (cancelled) return
      // Say "uploaded" before the round trip, not after: the bytes are already
      // in Storage, and an empty-looking photo step in the meantime invites the
      // user to pick again and orphan the snap the draft still references.
      setPhotoState("uploaded")
      const url = await provider.getDraftSnapUrl(resumedSnap.storage_path)
      if (cancelled || !url) return
      setPreviewUrl(url)
    })()
    return () => {
      cancelled = true
    }
  }, [provider, resumedSnap, previewUrl])

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

  const canPublish =
    blockedMessage === null &&
    flow.draft.name.trim().length > 0 &&
    flow.draft.emotionId !== ""

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
          mark_id: draft.markId,
          snaps: draft.pendingSnap ? [draft.pendingSnap] : [],
          cards: [],
        },
        // The success screen shows the amount, so the pill would be redundant.
        { onKarmaEarned: (delta) => { karmaDelta = delta } },
      )

      // The pebble exists (`addPebble` resolves on soft-success too, keyed off
      // pebble_id), so the draft has served its purpose.
      await consumeAfterPublish()

      setPublished({
        pebble,
        mark: marks.find((m) => m.id === pebble.mark_id) ?? null,
        karmaDelta,
      })
      dispatch({ type: "published" })
    } catch (err) {
      // A hard failure never reaches the success step: the flow stays put, so
      // the draft — and the way out via ✕ → Save as draft — is untouched.
      console.error("[record-flow] publish failed", err)
      setPublishError(tDraft("publishFailed"))
    } finally {
      setPublishing(false)
    }
  }, [publishing, canPublish, flow.draft, addPebble, consumeAfterPublish, marks, tDraft])

  // ---------------------------------------------------------------------------
  // Leaving
  // ---------------------------------------------------------------------------

  /** ✕ only asks when there is something to keep. */
  const handleClose = useCallback(() => {
    if (isEmpty) {
      onExit()
      return
    }
    setDraftError(null)
    setCloseConfirmOpen(true)
  }, [isEmpty, onExit])

  const handleSaveAsDraft = useCallback(async () => {
    if (savingDraft) return
    setSavingDraft(true)
    setDraftError(null)
    const saved = await saveAsDraft()
    setSavingDraft(false)
    if (saved) {
      setCloseConfirmOpen(false)
      onDraftSaved()
      return
    }
    // Stay in the dialog: the failure has nowhere else to be read from — the
    // publish error slot lives on the privacy step, and the user leaving from
    // step 2 would never see it.
    setDraftError(tDraft("saveFailed"))
  }, [savingDraft, saveAsDraft, onDraftSaved, tDraft])

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
      case "glyph":
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
          <RecordFlowChrome step={flow.step} onBack={goBack} onClose={handleClose} />
        )}

        {flow.step === "success" && published ? (
          <RecordSuccessStep
            pebble={published.pebble}
            mark={published.mark}
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
              onSelectGlyph={(id) => dispatch({ type: "selectGlyph", id })}
              onSelectVisibility={(value: Visibility) =>
                dispatch({ type: "selectVisibility", value })
              }
            />
          </RecordStepScaffold>
        )}
      </section>

      {/* Crash-insurance restore. Only offered when not already resuming a
          server draft, and only once per mount. */}
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

      {/* The moment a user wants to keep a half-finished pebble is precisely the
          moment they try to leave, so the choice lives here rather than in a
          permanent toolbar button. */}
      <AlertDialog open={closeConfirmOpen} onOpenChange={setCloseConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("keepTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{t("keepDescription")}</AlertDialogDescription>
          </AlertDialogHeader>
          {draftError && (
            <p role="alert" className="text-sm text-destructive">
              {draftError}
            </p>
          )}
          <AlertDialogFooter>
            <AlertDialogCancel>{t("keepGoing")}</AlertDialogCancel>
            <Button variant="ghost" onClick={handleDiscard} className="text-destructive">
              {t("discard")}
            </Button>
            <Button disabled={savingDraft} onClick={() => void handleSaveAsDraft()}>
              {tDraft("save")}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
