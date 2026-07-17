"use client"

import { useId, useState, useTransition } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import type { AdminEmotion } from "@/lib/emotions/types"
import { updateEmotionEmoji } from "../actions"

export function EmojiRow({ emotion }: { emotion: AdminEmotion }) {
  const id = useId()
  const [emoji, setEmoji] = useState(emotion.emoji)
  const [pending, startTransition] = useTransition()

  const trimmed = emoji.trim()
  const dirty = trimmed !== emotion.emoji
  const canSave = dirty && trimmed.length > 0 && trimmed.length <= 16 && !pending

  const onSave = () => {
    startTransition(async () => {
      const res = await updateEmotionEmoji({ id: emotion.id, emoji: trimmed })
      if (res?.error) {
        toast.error(res.error)
        return
      }
      setEmoji(trimmed)
      toast.success(`${emotion.name} emoji saved`)
    })
  }

  return (
    <div className="flex items-center gap-3 rounded-lg border p-3">
      <span aria-hidden className="grid size-10 shrink-0 place-items-center text-2xl">
        {trimmed || "—"}
      </span>
      <label htmlFor={id} className="min-w-0 flex-1">
        <span className="block truncate text-sm font-medium">{emotion.name}</span>
        <span className="block text-xs text-muted-foreground">{emotion.slug}</span>
      </label>
      <Input
        id={id}
        value={emoji}
        onChange={(e) => setEmoji(e.target.value)}
        aria-label={`${emotion.name} emoji`}
        className="w-16 text-center text-lg"
      />
      <Button
        size="sm"
        variant="secondary"
        disabled={!canSave}
        onClick={onSave}
        aria-label={`Save ${emotion.name} emoji`}
      >
        {pending ? "…" : "Save"}
      </Button>
    </div>
  )
}
