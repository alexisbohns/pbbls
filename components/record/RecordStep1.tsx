import type { RecordStepProps } from "@/components/record/RecordStepper"

export function RecordStep1(_props: RecordStepProps) {
  return (
    <fieldset className="space-y-2">
      <legend className="text-lg font-semibold">Time, Intensity & Emotion</legend>
      <p className="text-sm text-muted-foreground">
        When did this happen? How intense was it? What emotion best describes it?
      </p>
    </fieldset>
  )
}
