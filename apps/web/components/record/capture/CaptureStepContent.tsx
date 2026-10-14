"use client"

import { useTranslations } from "next-intl"
import type { CaptureState } from "@/lib/record/capture"
import { NAME_LIMIT } from "@/lib/record/name"
import { RecordPhotoStep, type PhotoUploadState } from "@/components/record/flow/steps/RecordPhotoStep"
import { RecordWhenStep } from "@/components/record/flow/steps/RecordWhenStep"
import { RecordNameStep } from "@/components/record/flow/steps/RecordNameStep"

type CaptureStepContentProps = {
  state: CaptureState
  previewUrl: string | undefined
  /** Non-null while the attached photo blocks saving. */
  blockedMessage: string | null
  saveError: string | null
  onPickPhoto: (file: File) => void
  onRetryPhoto: () => void
  onRemovePhoto: () => void
  onSetHappenedAt: (iso: string) => void
  onSetName: (value: string) => void
  /** Enter on the name field: the plain save. */
  onSubmitName: () => void
}

/** The record flow's photo step reads `picking` as an upload in progress. */
function photoStepState(photo: CaptureState["photo"]): PhotoUploadState {
  switch (photo) {
    case "none":
      return "idle"
    case "picking":
    case "uploading":
      return "uploading"
    case "uploaded":
      return "uploaded"
    case "failed":
      return "failed"
  }
}

/**
 * The body of whichever Capture step the flow is on, built from the record
 * flow's step bodies. Stateless; exhaustive with no `default`, so a new step
 * without a body fails the build.
 */
export function CaptureStepContent(props: CaptureStepContentProps) {
  const tCapture = useTranslations("record.capture")
  const { state } = props

  switch (state.step) {
    case "photo":
      return (
        <RecordPhotoStep
          previewUrl={props.previewUrl}
          state={photoStepState(state.photo)}
          onPick={props.onPickPhoto}
          onRetry={props.onRetryPhoto}
          onRemove={props.onRemovePhoto}
        />
      )
    case "when":
      return (
        <RecordWhenStep
          happenedAt={state.happenedAt}
          seededFromPhoto={state.seededFromPhoto}
          onChange={props.onSetHappenedAt}
        />
      )
    case "name":
      return (
        <div className="flex flex-col items-center gap-3">
          <RecordNameStep
            name={state.name}
            limit={NAME_LIMIT}
            label={tCapture("nameLabel")}
            charactersLeft={(count) => tCapture("charactersLeft", { count })}
            onChange={props.onSetName}
            onSubmit={props.onSubmitName}
          />
          {props.blockedMessage && (
            <p className="text-center text-sm text-muted-foreground" aria-live="polite">
              {props.blockedMessage}
            </p>
          )}
          {props.saveError && (
            <p role="alert" className="text-center text-sm text-destructive">
              {props.saveError}
            </p>
          )}
        </div>
      )
  }
}
