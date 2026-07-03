"use client"

import { useState } from "react"
import { useTranslations } from "next-intl"
import { Fingerprint } from "lucide-react"
import type { Mark } from "@/lib/types"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
import { GlyphPickerGrid } from "@/components/glyphs/GlyphPickerGrid"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"

type GlyphHeaderProps = {
  /** The currently-staged glyph, resolved to a Mark (or null for placeholder). */
  glyph: Mark | null
  /** All glyphs the user may pick from (own ∪ entitled). */
  glyphs: Mark[]
  selectedGlyphId: string | null
  onSelect: (id: string) => void
}

/**
 * Centered, tappable profile glyph that opens a picker — web port of the iOS
 * SettingsSheet glyph header.
 */
export function GlyphHeader({ glyph, glyphs, selectedGlyphId, onSelect }: GlyphHeaderProps) {
  const t = useTranslations("settings")
  const [open, setOpen] = useState(false)

  const handleSelect = (id: string | undefined) => {
    if (id) onSelect(id)
    setOpen(false)
  }

  return (
    <div className="flex justify-center">
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogTrigger
          render={
            <button
              type="button"
              aria-label={t("glyphAria")}
              className="flex size-24 items-center justify-center rounded-2xl border border-border transition-colors hover:bg-muted/50"
            />
          }
        >
          {glyph && glyph.strokes.length > 0 ? (
            <GlyphPreview mark={glyph} className="size-16" />
          ) : (
            <Fingerprint className="size-8 text-muted-foreground" aria-hidden />
          )}
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("glyphPickerTitle")}</DialogTitle>
          </DialogHeader>
          <GlyphPickerGrid
            marks={glyphs}
            selectedMarkId={selectedGlyphId ?? undefined}
            onSelect={handleSelect}
          />
        </DialogContent>
      </Dialog>
    </div>
  )
}
