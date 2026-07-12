"use client"

import { useState, type FormEvent, type KeyboardEvent } from "react"
import { Pencil } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { GlyphPickerDialog } from "@/components/record/GlyphPickerDialog"
import { SoulGlyphThumbnail } from "@/components/souls/SoulGlyphThumbnail"
import { DEFAULT_GLYPH_ID } from "@/lib/config/glyphs"
import type { Mark, Soul } from "@/lib/types"

type SoulDetailHeaderProps = {
  soul: Soul
  pebbleCount: number
  marks: Mark[]
  onUpdateName: (name: string) => Promise<void>
  onUpdateGlyph: (glyphId: string) => Promise<void>
}

export function SoulDetailHeader({
  soul,
  pebbleCount,
  marks,
  onUpdateName,
  onUpdateGlyph,
}: SoulDetailHeaderProps) {
  const [isEditing, setIsEditing] = useState(false)
  const [editValue, setEditValue] = useState(soul.name)
  const [pickerOpen, setPickerOpen] = useState(false)
  const t = useTranslations("souls")
  const tDetail = useTranslations("souls.detail")
  const tGlyph = useTranslations("souls.glyph")

  const selectedMark = marks.find((m) => m.id === soul.glyph_id)

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

  const handleGlyphSave = async (markId: string | undefined) => {
    const next = markId ?? DEFAULT_GLYPH_ID
    if (next === soul.glyph_id) return
    await onUpdateGlyph(next)
  }

  return (
    <header className="mb-6">
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={() => setPickerOpen(true)}
          aria-label={selectedMark ? tGlyph("changeAria") : tGlyph("addAria")}
          className="flex size-12 shrink-0 items-center justify-center rounded-md border border-input p-1 transition-all duration-100 hover:bg-muted active:scale-95 focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
        >
          <SoulGlyphThumbnail mark={selectedMark} className="size-full" />
        </button>

        {isEditing ? (
          <form onSubmit={handleSave} className="flex flex-1 items-center gap-2">
            <Input
              value={editValue}
              onChange={(e) => setEditValue(e.target.value)}
              onKeyDown={handleKeyDown}
              aria-label={tDetail("nameAria")}
              autoFocus
              className="text-2xl font-semibold"
            />
            <Button type="submit" size="sm" disabled={!editValue.trim()}>
              {tDetail("save")}
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleCancel}
            >
              {tDetail("cancel")}
            </Button>
          </form>
        ) : (
          <div className="flex flex-1 items-center gap-2">
            <h1 className="text-2xl font-semibold">{soul.name}</h1>
            <Button
              variant="ghost"
              size="icon-xs"
              onClick={() => {
                setEditValue(soul.name)
                setIsEditing(true)
              }}
              aria-label={tDetail("editAria")}
            >
              <Pencil className="size-3.5" />
            </Button>
          </div>
        )}
      </div>

      <p className="mt-2 text-sm text-muted-foreground">
        {t("pebbleCount", { count: pebbleCount })}
      </p>

      <GlyphPickerDialog
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        selectedMarkId={
          soul.glyph_id === DEFAULT_GLYPH_ID ? undefined : soul.glyph_id
        }
        onSave={handleGlyphSave}
      />
    </header>
  )
}
