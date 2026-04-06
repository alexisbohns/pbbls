import { useCallback, useState } from "react"
import { usePebbles } from "@/lib/data/usePebbles"
import { useDataProvider } from "@/lib/data/provider-context"
import { computeKarmaDelta } from "@/lib/data/karma"
import type { RecordFormData, CelebrationData } from "@/components/record/types"

const INITIAL_DATA: RecordFormData = {
  name: "",
  description: "",
  happened_at: new Date().toISOString(),
  intensity: 2,
  positiveness: 0,
  emotion_id: "",
  soul_ids: [],
  domain_ids: [],
  mark_id: undefined,
  instants: [],
  cards: [],
}

export function useRecordForm(
  onSaveSuccess: (data: CelebrationData) => void,
  initialOverrides?: Partial<RecordFormData>,
) {
  const { addPebble } = usePebbles()
  const { store } = useDataProvider()
  const [formData, setFormData] = useState<RecordFormData>(() => ({
    ...INITIAL_DATA,
    happened_at: new Date().toISOString(),
    ...initialOverrides,
  }))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleUpdate = useCallback((patch: Partial<RecordFormData>) => {
    setFormData((prev) => ({ ...prev, ...patch }))
  }, [])

  const handleSave = useCallback(async () => {
    if (saving) return
    setSaving(true)
    setError(null)
    try {
      const cleanedData = {
        ...formData,
        cards: formData.cards.filter((c) => c.value.trim() !== ""),
      }
      const bounceBefore = store.bounce
      const karmaDelta = computeKarmaDelta(cleanedData)
      const pebble = await addPebble(cleanedData)
      onSaveSuccess({
        pebbleId: pebble.id,
        pebbleName: formData.name,
        karmaDelta,
        bounceBefore,
        bounceAfter: store.bounce,
      })
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Something went wrong. Please try again."
      setError(message)
    } finally {
      setSaving(false)
    }
  }, [addPebble, formData, onSaveSuccess, saving, store.bounce])

  return { formData, handleUpdate, handleSave, saving, error }
}

/**
 * Parse URL search params into partial RecordFormData for pre-filling.
 */
export function parseRecordPrefill(params: URLSearchParams): Partial<RecordFormData> | undefined {
  if (!params.get("prefill")) return undefined

  const overrides: Partial<RecordFormData> = {}

  const name = params.get("name")
  if (name) overrides.name = name

  const happenedAt = params.get("happened_at")
  if (happenedAt) overrides.happened_at = happenedAt

  const intensity = params.get("intensity")
  if (intensity) {
    const n = Number(intensity)
    if (n === 1 || n === 2 || n === 3) overrides.intensity = n
  }

  const positiveness = params.get("positiveness")
  if (positiveness) {
    const n = Number(positiveness)
    if (n >= -2 && n <= 2) overrides.positiveness = n as RecordFormData["positiveness"]
  }

  const emotionId = params.get("emotion_id")
  if (emotionId) overrides.emotion_id = emotionId

  const domainIds = params.get("domain_ids")
  if (domainIds) overrides.domain_ids = domainIds.split(",").filter(Boolean)

  const soulIds = params.get("soul_ids")
  if (soulIds) overrides.soul_ids = soulIds.split(",").filter(Boolean)

  return overrides
}
