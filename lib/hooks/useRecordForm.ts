import { useCallback, useState } from "react"
import { usePebbles } from "@/lib/data/usePebbles"
import type { RecordFormData } from "@/components/record/RecordStepper"

const INITIAL_DATA: RecordFormData = {
  name: "",
  description: "",
  happened_at: new Date().toISOString(),
  intensity: 2,
  positiveness: 0,
  emotion_id: "",
  soul_ids: [],
  domain_ids: [],
  cards: [],
}

export function useRecordForm(onSaveSuccess: (pebbleId: string) => void) {
  const { addPebble } = usePebbles()
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
      const pebble = await addPebble(formData)
      onSaveSuccess(pebble.id)
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Something went wrong. Please try again."
      setError(message)
    } finally {
      setSaving(false)
    }
  }, [addPebble, formData, onSaveSuccess, saving])

  return { formData, handleUpdate, handleSave, saving, error }
}
