"use client"

import { useState, type FormEvent, type KeyboardEvent } from "react"
import { Pencil, Trash2 } from "lucide-react"
import { PEBBLE_SHAPES } from "@/lib/config"
import type { Mark } from "@/lib/types"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ConfirmDialog } from "@/components/ui/ConfirmDialog"

type GlyphDetailProps = {
  mark: Mark
  onDelete: () => void
  onUpdateName: (name: string | null) => Promise<void>
}

export function GlyphDetail({ mark, onDelete, onUpdateName }: GlyphDetailProps) {
  const shape = PEBBLE_SHAPES.find((s) => s.id === mark.shape_id)
  const created = new Intl.DateTimeFormat(undefined, {
    dateStyle: "long",
  }).format(new Date(mark.created_at))

  const [isEditing, setIsEditing] = useState(false)
  const [editValue, setEditValue] = useState(mark.name ?? "")

  const handleSave = async (e?: FormEvent) => {
    e?.preventDefault()
    const trimmed = editValue.trim()
    const current = mark.name ?? ""
    if (trimmed === current) {
      setIsEditing(false)
      return
    }
    await onUpdateName(trimmed.length > 0 ? trimmed : null)
    setIsEditing(false)
  }

  const handleCancel = () => {
    setEditValue(mark.name ?? "")
    setIsEditing(false)
  }

  const handleKeyDown = (e: KeyboardEvent) => {
    if (e.key === "Escape") handleCancel()
  }

  return (
    <article>
      <header className="mb-6">
        {isEditing ? (
          <form onSubmit={handleSave} className="flex items-center gap-2">
            <Input
              value={editValue}
              onChange={(e) => setEditValue(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Name (optional)"
              aria-label="Glyph name"
              autoFocus
              maxLength={80}
              className="text-2xl font-semibold"
            />
            <Button type="submit" size="sm">
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
            <h1 className="text-2xl font-semibold">
              {mark.name || "Untitled glyph"}
            </h1>
            <Button
              variant="ghost"
              size="icon-xs"
              onClick={() => {
                setEditValue(mark.name ?? "")
                setIsEditing(true)
              }}
              aria-label="Edit glyph name"
            >
              <Pencil className="size-3.5" />
            </Button>
          </div>
        )}

        <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          {shape && <span>{shape.name}</span>}
          <span aria-label={`${mark.strokes.length} strokes`}>
            {mark.strokes.length}{" "}
            {mark.strokes.length === 1 ? "stroke" : "strokes"}
          </span>
          <time dateTime={mark.created_at}>{created}</time>
        </div>
      </header>

      <div className="flex justify-center">
        <GlyphPreview
          mark={mark}
          className="w-full max-w-[240px] aspect-square"
        />
      </div>

      <div className="mt-8 flex justify-center">
        <ConfirmDialog
          trigger={
            <Button variant="outline" size="sm">
              <Trash2 className="size-4" aria-hidden="true" />
              Delete glyph
            </Button>
          }
          title="Delete this glyph?"
          description="This action cannot be undone. The glyph will be permanently removed."
          confirmLabel="Delete"
          onConfirm={onDelete}
        />
      </div>
    </article>
  )
}
