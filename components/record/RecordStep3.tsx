import type { RecordStepProps } from "@/components/record/RecordStepper"

export function RecordStep3(_props: RecordStepProps) {
  return (
    <fieldset className="space-y-2">
      <legend className="text-lg font-semibold">Cards & Review</legend>
      <p className="text-sm text-muted-foreground">
        Add reflective cards and review your pebble before saving.
      </p>
    </fieldset>
  )
}
