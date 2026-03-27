import type { RecordStepProps } from "@/components/record/RecordStepper"

export function RecordStep2(_props: RecordStepProps) {
  return (
    <fieldset className="space-y-2">
      <legend className="text-lg font-semibold">Souls & Domains</legend>
      <p className="text-sm text-muted-foreground">
        Who was involved? Which life domains does this touch?
      </p>
    </fieldset>
  )
}
