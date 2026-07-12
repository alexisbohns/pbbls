"use client"

import { useState, type FormEvent } from "react"
import { Plus } from "lucide-react"
import { useTranslations } from "next-intl"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { GlyphPickerDialog } from "@/components/record/GlyphPickerDialog"
import { SoulGlyphThumbnail } from "@/components/souls/SoulGlyphThumbnail"
import { DEFAULT_GLYPH_ID } from "@/lib/config/glyphs"
import type { Mark } from "@/lib/types"

type AddSoulFormProps = {
  marks: Mark[]
  onAdd: (input: { name: string; glyph_id: string }) => Promise<void>
}

export function AddSoulForm({ marks, onAdd }: AddSoulFormProps) {
  const [name, setName] = useState("")
  const [glyphId, setGlyphId] = useState<string>(DEFAULT_GLYPH_ID)
  const [pickerOpen, setPickerOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const t = useTranslations("souls")
  const tGlyph = useTranslations("souls.glyph")

  const selectedMark = marks.find((m) => m.id === glyphId)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) return

    setSubmitting(true)
    try {
      await onAdd({ name: trimmed, glyph_id: glyphId })
      setName("")
      setGlyphId(DEFAULT_GLYPH_ID)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <form onSubmit={handleSubmit} className="mb-6 flex gap-2">
        <button
          type="button"
          onClick={() => setPickerOpen(true)}
          aria-label={selectedMark ? tGlyph("changeAria") : tGlyph("addAria")}
          disabled={submitting}
          className="flex size-9 shrink-0 items-center justify-center rounded-md border border-input p-1 transition-all duration-100 hover:bg-muted active:scale-95 focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none disabled:opacity-50"
        >
          <SoulGlyphThumbnail mark={selectedMark} className="size-full" />
        </button>
        <Input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder={t("addPlaceholder")}
          aria-label={t("addNameAria")}
          disabled={submitting}
          className="flex-1"
        />
        <Button
          type="submit"
          size="sm"
          disabled={!name.trim() || submitting}
          aria-label={t("addAria")}
        >
          <Plus className="size-4" />
          {t("addCta")}
        </Button>
      </form>

      <GlyphPickerDialog
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        selectedMarkId={glyphId === DEFAULT_GLYPH_ID ? undefined : glyphId}
        onSave={(markId) => setGlyphId(markId ?? DEFAULT_GLYPH_ID)}
      />
    </>
  )
}
