"use client"

import { useMemo, useState, useTransition } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { GlyphPreview } from "@/components/pebblestore/GlyphPreview"
import {
  GLYPH_CANVAS_VIEWBOX,
  IDENTITY_ADJUST,
  type Adjust,
  type GlyphStroke,
} from "@/lib/pebblestore/types"
import { svgToStrokes } from "@/lib/pebblestore/svg-to-strokes"
import { bakeAdjust } from "@/lib/pebblestore/transform-path"
import type { AdminDomain } from "@/lib/domains/types"
import { setDomainGlyph, updateDomain } from "../../actions"

export function DomainEditor({ domain }: { domain: AdminDomain }) {
  // Text fields
  const [name, setName] = useState(domain.name)
  const [label, setLabel] = useState(domain.label)

  // Glyph editing: null strokes = keep the current glyph (no new SVG staged).
  const [strokes, setStrokes] = useState<GlyphStroke[] | null>(null)
  const [viewBox, setViewBox] = useState(domain.view_box ?? GLYPH_CANVAS_VIEWBOX)
  const [skipped, setSkipped] = useState<string[]>([])
  const [parseError, setParseError] = useState<string | null>(null)
  const [adjust, setAdjust] = useState<Adjust>(IDENTITY_ADJUST)

  const [formError, setFormError] = useState<string | null>(null)
  const [pending, startTransition] = useTransition()

  const hasNewGlyph = strokes !== null && strokes.length > 0

  // Preview: staged (adjusted) strokes if a new SVG is loaded, else the current
  // domain glyph as stored.
  const previewStrokes = useMemo(() => {
    if (hasNewGlyph) return bakeAdjust(strokes as GlyphStroke[], viewBox, adjust)
    return domain.strokes ?? []
  }, [hasNewGlyph, strokes, viewBox, adjust, domain.strokes])

  const previewViewBox = hasNewGlyph ? viewBox : domain.view_box ?? GLYPH_CANVAS_VIEWBOX

  const onFile = async (file: File | undefined) => {
    if (!file) return
    setParseError(null)
    try {
      const text = await file.text()
      const r = svgToStrokes(text)
      if (r.strokes.length === 0) {
        setParseError("No supported strokes found in this SVG (see the supported subset).")
        setStrokes(null)
        setSkipped(r.skipped)
        return
      }
      setStrokes(r.strokes)
      setViewBox(r.viewBox)
      setSkipped(r.skipped)
      setAdjust(IDENTITY_ADJUST)
    } catch (e) {
      setParseError(e instanceof Error ? e.message : String(e))
      setStrokes(null)
    }
  }

  const onSave = () => {
    setFormError(null)
    startTransition(async () => {
      const textRes = await updateDomain({ id: domain.id, name: name.trim(), label: label.trim() })
      if (textRes?.error) {
        setFormError(textRes.error)
        return
      }
      if (hasNewGlyph) {
        const baked = bakeAdjust(strokes as GlyphStroke[], viewBox, adjust)
        const glyphRes = await setDomainGlyph({ id: domain.id, strokes: baked, viewBox })
        if (glyphRes?.error) {
          setFormError(glyphRes.error)
          return
        }
        // Clear the staged glyph so the preview now reflects the saved state.
        setStrokes(null)
      }
      toast.success("Domain saved")
    })
  }

  const canSave = name.trim().length > 0 && !pending

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-lg font-semibold">{domain.name}</h1>
        <p className="text-xs text-muted-foreground">{domain.slug}</p>
      </div>

      <GlyphPreview
        strokes={previewStrokes}
        viewBox={previewViewBox}
        className="mx-auto aspect-square w-40 rounded-md border bg-card text-foreground"
      />

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="domain-name">Name</Label>
          <Input id="domain-name" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="domain-label">Description</Label>
          <Input id="domain-label" value={label} onChange={(e) => setLabel(e.target.value)} />
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="svg-file">Replace glyph (SVG)</Label>
        <Input
          id="svg-file"
          type="file"
          accept=".svg,image/svg+xml"
          onChange={(e) => onFile(e.target.files?.[0])}
        />
        {parseError ? <p className="text-sm text-destructive">{parseError}</p> : null}
        {skipped.length > 0 ? (
          <p className="text-xs text-muted-foreground">
            Skipped (unsupported): {skipped.join(", ")}. Glyphs are stroke-only — filled
            shapes import as outlines.
          </p>
        ) : null}
      </div>

      {hasNewGlyph ? (
        <fieldset className="space-y-3 rounded-lg border p-4">
          <legend className="px-1 text-sm font-medium">Adjust</legend>
          <div className="space-y-1">
            <Label htmlFor="adjust-scale">Scale: {adjust.scale.toFixed(2)}</Label>
            <input
              id="adjust-scale"
              type="range"
              min={0.3}
              max={1.5}
              step={0.05}
              value={adjust.scale}
              onChange={(e) => setAdjust((a) => ({ ...a, scale: Number(e.target.value) }))}
              className="w-full"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label htmlFor="adjust-x">Offset X: {adjust.offsetX}</Label>
              <input
                id="adjust-x"
                type="range"
                min={-50}
                max={50}
                step={1}
                value={adjust.offsetX}
                onChange={(e) => setAdjust((a) => ({ ...a, offsetX: Number(e.target.value) }))}
                className="w-full"
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="adjust-y">Offset Y: {adjust.offsetY}</Label>
              <input
                id="adjust-y"
                type="range"
                min={-50}
                max={50}
                step={1}
                value={adjust.offsetY}
                onChange={(e) => setAdjust((a) => ({ ...a, offsetY: Number(e.target.value) }))}
                className="w-full"
              />
            </div>
          </div>
          <div className="flex gap-6">
            <label className="flex items-center gap-2 text-sm">
              <Switch
                checked={adjust.flipH}
                onCheckedChange={(v) => setAdjust((a) => ({ ...a, flipH: v }))}
              />
              Flip H
            </label>
            <label className="flex items-center gap-2 text-sm">
              <Switch
                checked={adjust.flipV}
                onCheckedChange={(v) => setAdjust((a) => ({ ...a, flipV: v }))}
              />
              Flip V
            </label>
          </div>
        </fieldset>
      ) : null}

      {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      <Button disabled={!canSave} onClick={onSave}>
        {pending ? "Saving…" : "Save"}
      </Button>
    </div>
  )
}
