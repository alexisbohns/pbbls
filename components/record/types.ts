import type { PebbleCard } from "@/lib/types"

export type RecordFormData = {
  name: string
  description: string
  happened_at: string
  intensity: 1 | 2 | 3
  positiveness: -2 | -1 | 0 | 1 | 2
  emotion_id: string
  soul_ids: string[]
  domain_ids: string[]
  mark_id?: string
  instants: string[]
  cards: PebbleCard[]
}

export type RecordStepProps = {
  data: RecordFormData
  onUpdate: (patch: Partial<RecordFormData>) => void
}

export type StepConfig = {
  label: string
  Component: React.ComponentType<RecordStepProps>
  canAdvance: (data: RecordFormData) => boolean
  onAdvance?: (
    data: RecordFormData,
    onUpdate: (patch: Partial<RecordFormData>) => void,
  ) => Promise<void> | void
}

export type CelebrationData = {
  pebbleId: string
  karmaDelta: number
  bounceBefore: number
  bounceAfter: number
}
