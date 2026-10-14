"use client"

import { useCallback, useEffect, useMemo, useReducer, useState } from "react"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import type { Moment, Visibility } from "@/lib/types"
import { useAuth } from "@/lib/data/auth-context"
import { useMoment } from "@/lib/data/useMoment"
import { useMoments } from "@/lib/data/useMoments"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { completePebblePayload } from "@/lib/data/moment-payloads"
import { momentErrorKey } from "@/lib/data/moment-write-error"
import { useCompleteAutosave } from "@/lib/hooks/useCompleteAutosave"
import { useRecordFlowHistory } from "@/lib/hooks/useRecordFlowHistory"
import { draftFromSnapshot, type CompleteDraftSnapshot } from "@/lib/record/complete-draft-store"
import {
  completeFlowReducer,
  initialCompleteState,
  isDraftComplete,
  optionalButtonIsSkip,
} from "@/lib/record/complete"
import { COMPLETE_COUNTED_STEPS, COMPLETE_STEPS, type CompleteStep } from "@/lib/record/steps"
import { prewarmValenceArt } from "@/lib/valence/stone-art"
import { PebbleNotFound } from "@/components/pebble/PebbleNotFound"
import { RecordFlowChrome } from "@/components/record/flow/RecordFlowChrome"
import {
  RecordStepScaffold,
  type RecordStepAction,
} from "@/components/record/flow/RecordStepScaffold"
import { CompleteStepContent } from "./CompleteStepContent"
import { CompleteSuccessStep } from "./CompleteSuccessStep"
import { useCompleteStepCopy } from "./complete-step-copy"

type CompleteFlowProps = {
  /** The moment to complete, lowercased. */
  momentId: string
  /** `capture` when chained from Capture (PD-W5): ✕ then lands on the Path. */
  from: "capture" | null
}

type Completion = { karmaDelta: number; before: Moment }

/**
 * The Complete flow (#1050): valence · emotion · domain · souls · collection ·
 * privacy → success, turning a moment into the caller's pebble through one
 * `complete_pebble` write. Ported from iOS `CompleteFlowView`.
 *
 * Orchestration only — gating, back, Skip / Done and resume live in the pure
 * reducer (`lib/record/complete.ts`), the autosave in `useCompleteAutosave`.
 * ✕ never asks (PD-W6): the answers are already saved on this device.
 */
export function CompleteFlow({ momentId, from }: CompleteFlowProps) {
  const t = useTranslations("record.flow")
  const tComplete = useTranslations("record.complete")
  const tErrors = useTranslations("moment.errors")
  const router = useRouter()

  const { user } = useAuth()
  const { moment, loading } = useMoment(momentId)
  const { completePebble } = useMoments()
  const { souls } = useSouls()
  const { collections } = useCollections()

  const [flow, dispatch] = useReducer(completeFlowReducer, undefined, () => initialCompleteState())
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [completion, setCompletion] = useState<Completion | null>(null)

  const alreadyCompleted = !loading && moment?.pebble != null && completion === null && !submitting

  // A moment that already carries the caller's pebble has nothing to complete.
  // The current read page is keyed by the pebble id; Part 3 points this at
  // Edit pebble.
  const existingPebbleId = alreadyCompleted ? moment?.pebble?.id : undefined
  useEffect(() => {
    if (existingPebbleId) router.replace(`/pebble/${existingPebbleId}`)
  }, [existingPebbleId, router])

  // The valence fan wobbles nine artworks on first sight.
  useEffect(() => prewarmValenceArt(), [])

  // ---------------------------------------------------------------------------
  // Autosave (PD-W7): restored once the store has loaded, never after success.
  // ---------------------------------------------------------------------------

  const handleRestore = useCallback(
    (snapshot: CompleteDraftSnapshot) => {
      dispatch({
        type: "hydrate",
        draft: draftFromSnapshot(snapshot, {
          soulIds: new Set(souls.map((s) => s.id)),
          collectionIds: new Set(collections.map((c) => c.id)),
        }),
      })
    },
    [souls, collections],
  )

  const { flush: flushAutosave, clear: clearAutosave } = useCompleteAutosave({
    ownerId: user?.id ?? null,
    momentId,
    draft: flow.draft,
    ready: !loading && moment !== undefined && moment.pebble === null && completion === null,
    onRestore: handleRestore,
  })

  // ---------------------------------------------------------------------------
  // Leaving
  // ---------------------------------------------------------------------------

  const exitToPath = useCallback(() => {
    router.push(`/path?focus=${momentId}`)
  }, [router, momentId])

  const handleClose = useCallback(() => {
    flushAutosave()
    router.push(from === "capture" ? `/path?focus=${momentId}` : `/pebble/${momentId}`)
  }, [flushAutosave, router, from, momentId])

  const goTo = useCallback(
    (step: CompleteStep) => {
      // Back out of the success screen leaves the flow: the pebble exists, and
      // the privacy step again would offer a second write.
      if (completion) {
        exitToPath()
        return
      }
      dispatch({ type: "goTo", step })
    },
    [completion, exitToPath],
  )
  const { goBack } = useRecordFlowHistory(COMPLETE_STEPS, flow.step, goTo)

  // ---------------------------------------------------------------------------
  // Completing
  // ---------------------------------------------------------------------------

  const canSubmit = isDraftComplete(flow.draft)

  const handleSubmit = useCallback(async () => {
    if (submitting || !moment) return
    const payload = completePebblePayload(momentId, flow.draft)
    if (!payload) return
    setSubmitting(true)
    setSubmitError(null)
    const before = moment
    try {
      let karmaDelta = 0
      await completePebble(payload, {
        reason: "pebble_created",
        // The success screen shows the amount, so the pill would be redundant.
        onKarmaEarned: (delta) => {
          karmaDelta = delta
        },
      })
      clearAutosave()
      setCompletion({ karmaDelta, before })
      dispatch({ type: "completed" })
    } catch (err) {
      // The flow stays put with its answers and its autosave.
      console.error("[complete-flow] complete_pebble failed", err)
      setSubmitError(tErrors(momentErrorKey(err)))
    } finally {
      setSubmitting(false)
    }
  }, [submitting, moment, momentId, flow.draft, completePebble, clearAutosave, tErrors])

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------

  const copy = useCompleteStepCopy(flow.step)

  const action = useMemo((): RecordStepAction | undefined => {
    switch (flow.step) {
      case "emotion":
      case "domain":
      case "success":
        return undefined
      case "valence":
        return {
          kind: "primary",
          label: t("continue"),
          enabled: true,
          onPress: () => dispatch({ type: "advance" }),
        }
      case "souls":
      case "collection":
        return {
          kind: "text",
          label: optionalButtonIsSkip(flow) ? t("skip") : t("done"),
          onPress: () => dispatch({ type: "advance" }),
        }
      case "privacy":
        return {
          kind: "primary",
          label: tComplete("submit"),
          enabled: canSubmit,
          loading: submitting,
          onPress: () => void handleSubmit(),
        }
    }
  }, [flow, t, tComplete, canSubmit, submitting, handleSubmit])

  if (loading || alreadyCompleted) {
    return <p className="py-12 text-center text-sm text-muted-foreground">{t("loading")}</p>
  }

  if (!moment && !completion) {
    return <PebbleNotFound description={tErrors("momentNotFound")} />
  }

  return (
    <section
      aria-label={tComplete("aria")}
      className="mx-auto flex min-h-[calc(100dvh-4rem-var(--safe-area-top)-var(--safe-area-bottom))] w-full max-w-lg flex-col gap-4"
    >
      {flow.step !== "success" && (
        <RecordFlowChrome
          steps={COMPLETE_STEPS}
          counted={COMPLETE_COUNTED_STEPS}
          step={flow.step}
          onBack={goBack}
          onClose={handleClose}
        />
      )}

      {flow.step === "success" && completion ? (
        <CompleteSuccessStep
          moment={moment}
          before={completion.before}
          draft={flow.draft}
          karmaDelta={completion.karmaDelta}
          onExit={exitToPath}
        />
      ) : (
        <RecordStepScaffold title={copy.title} subtitle={copy.subtitle} action={action}>
          <CompleteStepContent
            state={flow}
            submitError={submitError}
            onSelectValence={(intensity, positiveness) =>
              dispatch({ type: "selectValence", intensity, positiveness })
            }
            onSelectEmotion={(id) => dispatch({ type: "selectEmotion", id })}
            onSelectDomain={(id) => dispatch({ type: "selectDomain", id })}
            onToggleSoul={(id) => dispatch({ type: "toggleSoul", id })}
            onSelectCollection={(id) => dispatch({ type: "selectCollection", id })}
            onSelectVisibility={(value: Visibility) => dispatch({ type: "selectVisibility", value })}
          />
        </RecordStepScaffold>
      )}
    </section>
  )
}
