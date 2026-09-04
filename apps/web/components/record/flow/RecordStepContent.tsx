"use client"

import type { Visibility } from "@/lib/types"
import { NAME_LIMIT, type RecordFlowState } from "@/lib/record/flow"
import { RecordPhotoStep, type PhotoUploadState } from "./steps/RecordPhotoStep"
import { RecordWhenStep } from "./steps/RecordWhenStep"
import { RecordNameStep } from "./steps/RecordNameStep"
import { RecordValenceStep } from "./steps/RecordValenceStep"
import { RecordEmotionStep } from "./steps/RecordEmotionStep"
import { RecordDomainStep } from "./steps/RecordDomainStep"
import { RecordSoulsStep } from "./steps/RecordSoulsStep"
import { RecordCollectionStep } from "./steps/RecordCollectionStep"
import { RecordGlyphStep } from "./steps/RecordGlyphStep"
import { RecordPrivacyStep } from "./steps/RecordPrivacyStep"

type RecordStepContentProps = {
  state: RecordFlowState
  photo: { previewUrl: string | undefined; state: PhotoUploadState }
  blockedMessage: string | null
  publishError: string | null
  onPickPhoto: (file: File) => void
  onRetryPhoto: () => void
  onRemovePhoto: () => void
  onSetHappenedAt: (iso: string) => void
  onSetName: (value: string) => void
  onAdvance: () => void
  onSelectValence: (intensity: 1 | 2 | 3, valence: -1 | 0 | 1) => void
  onSelectEmotion: (id: string) => void
  onSelectDomain: (id: string) => void
  onToggleSoul: (id: string) => void
  onToggleCollection: (id: string) => void
  onSelectGlyph: (id: string | undefined) => void
  onSelectVisibility: (value: Visibility) => void
}

/**
 * Renders the body of whichever step the flow is on.
 *
 * Split out of `RecordFlow` so the container keeps to chrome, the action table
 * and orchestration. Holds no state of its own — every input arrives as a
 * parameter, so the whole flow's state stays in one place.
 *
 * The switch is exhaustive with no `default`, deliberately: a step added to the
 * enum without a body here should fail the build, not render nothing.
 */
export function RecordStepContent(props: RecordStepContentProps) {
  const { state } = props
  const { draft } = state

  switch (state.step) {
    case "photo":
      return (
        <RecordPhotoStep
          previewUrl={props.photo.previewUrl}
          state={props.photo.state}
          onPick={props.onPickPhoto}
          onRetry={props.onRetryPhoto}
          onRemove={props.onRemovePhoto}
        />
      )
    case "when":
      return (
        <RecordWhenStep
          happenedAt={draft.happenedAt}
          seededFromPhoto={state.seededFromPhoto}
          onChange={props.onSetHappenedAt}
        />
      )
    case "name":
      return (
        <RecordNameStep
          name={draft.name}
          limit={NAME_LIMIT}
          onChange={props.onSetName}
          onSubmit={props.onAdvance}
        />
      )
    case "valence":
      return (
        <RecordValenceStep
          intensity={draft.intensity}
          valence={draft.valence}
          onSelect={props.onSelectValence}
        />
      )
    case "emotion":
      return (
        <RecordEmotionStep
          selected={draft.emotionId || undefined}
          intensity={draft.intensity}
          valence={draft.valence}
          onSelect={props.onSelectEmotion}
        />
      )
    case "domain":
      return <RecordDomainStep selected={draft.domainIds[0]} onSelect={props.onSelectDomain} />
    case "souls":
      return <RecordSoulsStep selectedIds={draft.soulIds} onToggle={props.onToggleSoul} />
    case "collection":
      return (
        <RecordCollectionStep
          selectedIds={draft.collectionIds}
          onToggle={props.onToggleCollection}
        />
      )
    case "glyph":
      return <RecordGlyphStep selected={draft.markId} onSelect={props.onSelectGlyph} />
    case "privacy":
      return (
        <RecordPrivacyStep
          value={draft.visibility}
          onSelect={props.onSelectVisibility}
          blockedMessage={props.blockedMessage}
          publishError={props.publishError}
        />
      )
    case "success":
      // `RecordFlow` renders the success step outside the scaffold so it can own
      // its full-height layout. This branch exists only to keep the switch
      // exhaustive, so a step added later cannot silently render nothing.
      return null
  }
}
