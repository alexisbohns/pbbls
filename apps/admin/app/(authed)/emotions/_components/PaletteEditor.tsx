"use client"

import { useMemo, useState, useTransition } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import {
  PALETTE_VARIANTS,
  type AdminEmotionCategory,
  type PaletteVariantKey,
} from "@/lib/emotions/types"
import { deriveSurface, isHex8, normalizeHex8 } from "@/lib/emotions/color"
import { updateEmotionPalette } from "../actions"
import { ColorField } from "./ColorField"

type Staged = Record<PaletteVariantKey, string>

function initialStaged(c: AdminEmotionCategory): Staged {
  return {
    primary_color: c.primary_color,
    secondary_color: c.secondary_color,
    light_color: c.light_color,
    shaded_color: c.shaded_color,
    dark_color: c.dark_color,
  }
}

export function PaletteEditor({ category }: { category: AdminEmotionCategory }) {
  const [staged, setStaged] = useState<Staged>(() => initialStaged(category))
  const [pending, startTransition] = useTransition()

  const dirty = useMemo(
    () => PALETTE_VARIANTS.some(({ key }) => normalizeHex8(staged[key]) !== normalizeHex8(category[key])),
    [staged, category],
  )
  const allValid = PALETTE_VARIANTS.every(({ key }) => isHex8(staged[key]))
  const canSave = dirty && allValid && !pending

  // Surface is derived from primary server-side; mirror that in the preview.
  const surface = isHex8(staged.primary_color)
    ? deriveSurface(staged.primary_color)
    : category.surface_color

  const onSave = () => {
    startTransition(async () => {
      const res = await updateEmotionPalette({
        id: category.id,
        primary: normalizeHex8(staged.primary_color),
        secondary: normalizeHex8(staged.secondary_color),
        light: normalizeHex8(staged.light_color),
        shaded: normalizeHex8(staged.shaded_color),
        dark: normalizeHex8(staged.dark_color),
      })
      if (res?.error) {
        toast.error(res.error)
        return
      }
      // Re-normalise the staged values so the row is no longer "dirty".
      setStaged((s) => {
        const next = { ...s }
        for (const { key } of PALETTE_VARIANTS) next[key] = normalizeHex8(s[key])
        return next
      })
      toast.success(`${category.name} palette saved`)
    })
  }

  return (
    <article className="space-y-4 rounded-lg border p-4">
      <header className="flex items-baseline justify-between gap-2">
        <h3 className="font-medium">{category.name}</h3>
        <span className="text-xs text-muted-foreground">{category.slug}</span>
      </header>

      {/* Visualize: the palette applied to a sample chip. */}
      <div
        className="flex items-center gap-3 rounded-md border p-3"
        style={{ backgroundColor: surface }}
      >
        <span
          aria-hidden
          className="size-8 shrink-0 rounded-full"
          style={{ backgroundColor: isHex8(staged.primary_color) ? staged.primary_color : undefined }}
        />
        <div className="min-w-0">
          <p
            className="truncate text-sm font-semibold"
            style={{ color: isHex8(staged.shaded_color) ? staged.shaded_color : undefined }}
          >
            {category.name}
          </p>
          <div className="mt-1 flex gap-1">
            {PALETTE_VARIANTS.map(({ key }) => (
              <span
                key={key}
                aria-hidden
                className="h-2 w-6 rounded-full border"
                style={{ backgroundColor: isHex8(staged[key]) ? staged[key] : undefined }}
              />
            ))}
          </div>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        {PALETTE_VARIANTS.map(({ key, label }) => (
          <ColorField
            key={key}
            label={label}
            value={staged[key]}
            onChange={(next) => setStaged((s) => ({ ...s, [key]: next }))}
          />
        ))}
      </div>

      <p className="text-xs text-muted-foreground">
        Surface is derived from Primary (10% opacity) and updates automatically.
      </p>

      <Button size="sm" disabled={!canSave} onClick={onSave}>
        {pending ? "Saving…" : "Save palette"}
      </Button>
    </article>
  )
}
