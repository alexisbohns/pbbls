"use client"

import { useState, type FormEvent, type KeyboardEvent } from "react"
import { Pencil } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import type { Soul } from "@/lib/types"
import { pluralize } from "@/lib/utils/formatters"

type SoulDetailHeaderProps = {
  soul: Soul
  pebbleCount: number
  onUpdateName: (name: string) => Promise<void>
}

export function SoulDetailHeader({
  soul,
  pebbleCount,
  onUpdateName,
}: SoulDetailHeaderProps) {
  const [isEditing, setIsEditing] = useState(false)
  const [editValue, setEditValue] = useState(soul.name)

  const handleSave = async (e?: FormEvent) => {
    e?.preventDefault()
    const trimmed = editValue.trim()
    if (!trimmed || trimmed === soul.name) {
      setIsEditing(false)
      setEditValue(soul.name)
      return
    }
    await onUpdateName(trimmed)
    setIsEditing(false)
  }

  const handleCancel = () => {
    setEditValue(soul.name)
    setIsEditing(false)
  }

  const handleKeyDown = (e: KeyboardEvent) => {
    if (e.key === "Escape") handleCancel()
  }

  return (
    <header className="mb-6">
      {isEditing ? (
        <form onSubmit={handleSave} className="flex items-center gap-2">
          <Input
            value={editValue}
            onChange={(e) => setEditValue(e.target.value)}
            onKeyDown={handleKeyDown}
            aria-label="Soul name"
            autoFocus
            className="text-2xl font-semibold"
          />
          <Button type="submit" size="sm" disabled={!editValue.trim()}>
            Save
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleCancel}
          >
            Cancel
          </Button>
        </form>
      ) : (
        <div className="flex items-center gap-2">
          <h1 className="text-2xl font-semibold">{soul.name}</h1>
          <Button
            variant="ghost"
            size="icon-xs"
            onClick={() => {
              setEditValue(soul.name)
              setIsEditing(true)
            }}
            aria-label="Edit soul name"
          >
            <Pencil className="size-3.5" />
          </Button>
        </div>
      )}

      <p className="mt-2 text-sm text-muted-foreground">
        {pluralize(pebbleCount, "pebble")}
      </p>
    </header>
  )
}
