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
  cards: [],
}

export function useRecordForm(onSaveSuccess: (data: CelebrationData) => void) {
  const { addPebble } = usePebbles()
  const { store } = useDataProvider()
  const [formData, setFormData] = useState<RecordFormData>(INITIAL_DATA)
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
