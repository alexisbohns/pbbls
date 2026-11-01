"use client"

import {
  useState,
  type FormEvent,
  type KeyboardEvent,
  type ReactNode,
} from "react"
import { DrawDotted, Trash } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { useTranslations } from "next-intl"
import type { Mark } from "@/lib/types"
import { useFormatDate } from "@/lib/i18n"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { ConfirmDialog } from "@/components/ui/ConfirmDialog"

type GlyphDetailProps = {
  mark: Mark
  onDelete: () => void
  onUpdateName: (name: string | null) => Promise<void>
  locked?: boolean // listed/bought → no edit/delete
  submitSlot?: ReactNode // SubmitToCommunity rendered by the page
}

export function GlyphDetail({
  mark,
  onDelete,
  onUpdateName,
  locked,
  submitSlot,
}: GlyphDetailProps) {
  const tDetail = useTranslations("glyphs.detail")
  const tSubmit = useTranslations("glyphs.submit")
  const tCard = useTranslations("glyphs.card")
  const formatDate = useFormatDate()
  const created = formatDate(mark.created_at, { dateStyle: "long" })

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
              placeholder={tDetail("namePlaceholder")}
              aria-label={tDetail("nameAria")}
              autoFocus
              maxLength={80}
              className="text-2xl font-semibold"
            />
            <Button type="submit" size="sm">
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
          <div className="flex items-center gap-2">
            {/* The name is the detail's title, in the top bar (#1384, two-area spec D6): here, only its edit. */}
            {!locked && (
              <Button
                variant="ghost"
                className="h-10 rounded-full px-3 text-m3-primary"
                onClick={() => {
                  setEditValue(mark.name ?? "")
                  setIsEditing(true)
                }}
              >
                <DrawDotted className={PBBLS_ICON_SIZE.inline} />
                {tDetail("editAria")}
              </Button>
            )}
            {submitSlot}
          </div>
        )}

        <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          <span>{tCard("strokeCount", { count: mark.strokes.length })}</span>
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
        {locked ? (
          <Badge variant="secondary">{tSubmit("locked")}</Badge>
        ) : (
          <ConfirmDialog
            trigger={
              <Button variant="outline" size="sm">
                <Trash className={PBBLS_ICON_SIZE.inline} />
                {tDetail("deleteCta")}
              </Button>
            }
            title={tDetail("deleteTitle")}
            description={tDetail("deleteDescription")}
            confirmLabel={tDetail("deleteConfirm")}
            onConfirm={onDelete}
          />
        )}
      </div>
    </article>
  )
}
