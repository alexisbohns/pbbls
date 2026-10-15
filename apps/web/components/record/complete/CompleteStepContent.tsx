"use client"

import type { Visibility } from "@/lib/types"
import type { CompleteFlowState } from "@/lib/record/complete"
import { RecordValenceStep } from "@/components/record/flow/steps/RecordValenceStep"
import { RecordEmotionStep } from "@/components/record/flow/steps/RecordEmotionStep"
import { RecordDomainStep } from "@/components/record/flow/steps/RecordDomainStep"
import { RecordSoulsStep } from "@/components/record/flow/steps/RecordSoulsStep"
import { RecordPrivacyStep } from "@/components/record/flow/steps/RecordPrivacyStep"
import { CompleteCollectionStep } from "./CompleteCollectionStep"

type CompleteStepContentProps = {
  state: CompleteFlowState
  submitError: string | null
  onSelectValence: (intensity: 1 | 2 | 3, positiveness: -1 | 0 | 1) => void
  onSelectEmotion: (id: string) => void
  onSelectDomain: (id: string) => void
  onToggleSoul: (id: string) => void
  onSelectCollection: (id: string | null) => void
  onSelectVisibility: (value: Visibility) => void
}

/**
 * The body of whichever Complete step the flow is on, built from the record
 * flow's step bodies. Stateless; exhaustive with no `default`, so a new step
 * without a body fails the build.
 */
export function CompleteStepContent(props: CompleteStepContentProps) {
  const { draft, step } = props.state

  switch (step) {
    case "valence":
      return (
        <RecordValenceStep
          intensity={draft.intensity}
          valence={draft.positiveness}
          onSelect={props.onSelectValence}
        />
      )
    case "emotion":
      return (
        <RecordEmotionStep
          selected={draft.emotionId ?? undefined}
          intensity={draft.intensity}
          valence={draft.positiveness}
          onSelect={props.onSelectEmotion}
        />
      )
    case "domain":
      return <RecordDomainStep selected={draft.domainIds[0]} onSelect={props.onSelectDomain} />
    case "souls":
      return <RecordSoulsStep selectedIds={draft.soulIds} onToggle={props.onToggleSoul} />
    case "collection":
      return (
        <CompleteCollectionStep
          selectedIds={draft.collectionIds}
          onSelect={props.onSelectCollection}
        />
      )
    case "privacy":
      return (
        <RecordPrivacyStep
          value={draft.visibility}
          onSelect={props.onSelectVisibility}
          blockedMessage={null}
          publishError={props.submitError}
        />
      )
    case "success":
      // Rendered by `CompleteFlow` outside the scaffold; kept for exhaustiveness.
      return null
  }
}
