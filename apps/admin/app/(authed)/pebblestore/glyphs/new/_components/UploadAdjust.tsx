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
  GLYPH_PRICE_DEFAULT,
  IDENTITY_ADJUST,
  type Adjust,
  type GlyphStroke,
} from "@/lib/pebblestore/types"
import { svgToStrokes } from "@/lib/pebblestore/svg-to-strokes"
import { bakeAdjust } from "@/lib/pebblestore/transform-path"
import { publishGlyph } from "../../actions"

export function UploadAdjust() {
  const [strokes, setStrokes] = useState<GlyphStroke[]>([])
  const [viewBox, setViewBox] = useState(GLYPH_CANVAS_VIEWBOX)
  const [skipped, setSkipped] = useState<string[]>([])
  const [parseError, setParseError] = useState<string | null>(null)

  const [name, setName] = useState("")
  const [price, setPrice] = useState(String(GLYPH_PRICE_DEFAULT))
  const [adjust, setAdjust] = useState<Adjust>(IDENTITY_ADJUST)

  const [formError, setFormError] = useState<string | null>(null)
  const [pending, startTransition] = useTransition()

  const numericPrice = Number(price)
  // Karma prices are whole numbers; the RPC arg is `integer` so a decimal would
  // be rejected server-side with an opaque cast error. Gate it on the client.
  const priceValid = Number.isInteger(numericPrice) && numericPrice > 0
  const canPublish = strokes.length > 0 && priceValid

  // Live preview shows exactly what publish stores: the baked (adjusted) strokes
  // in the square viewBox, stroke held at 6 in glyph space.
  const previewStrokes = useMemo(
    () => bakeAdjust(strokes, viewBox, adjust),
    [strokes, viewBox, adjust],
  )

  const onFile = async (file: File | undefined) => {
    if (!file) return
    setParseError(null)
    try {
      const text = await file.text()
      const r = svgToStrokes(text)
      if (r.strokes.length === 0) {
        setParseError("No supported strokes found in this SVG (see the supported subset).")
        setStrokes([])
        setSkipped(r.skipped)
        return
      }
      setStrokes(r.strokes)
      setViewBox(r.viewBox)
      setSkipped(r.skipped)
      setAdjust(IDENTITY_ADJUST)
    } catch (e) {
      setParseError(e instanceof Error ? e.message : String(e))
      setStrokes([])
    }
  }

  const onPublish = () => {
    setFormError(null)
    const baked = bakeAdjust(strokes, viewBox, adjust)
    startTransition(async () => {
      const res = await publishGlyph({
        name: name.trim(),
        strokes: baked,
        viewBox,
        price: numericPrice,
      })
      // On success the action redirects; only an error path returns here.
      if (res?.error) {
        setFormError(res.error)
        return
      }
      toast.success("Glyph published")
    })
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <Label htmlFor="svg-file">SVG file</Label>
        <Input
          id="svg-file"
          type="file"
          accept=".svg,image/svg+xml"
          onChange={(e) => onFile(e.target.files?.[0])}
        />
        {parseError ? <p className="text-sm text-destructive">{parseError}</p> : null}
        {skipped.length > 0 ? (
          <p className="text-xs text-muted-foreground">
            Skipped (unsupported): {skipped.join(", ")}. Glyphs are stroke-only — filled shapes
            import as outlines.
          </p>
        ) : null}
      </div>

      {strokes.length > 0 ? (
        <>
          <GlyphPreview
            strokes={previewStrokes}
            viewBox={viewBox}
            className="mx-auto aspect-square w-48 rounded-md border bg-card text-foreground"
          />

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="glyph-name">Name</Label>
              <Input id="glyph-name" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="glyph-price">Price (karma)</Label>
              <Input
                id="glyph-price"
                type="number"
                min={1}
                step={1}
                value={price}
                onChange={(e) => setPrice(e.target.value)}
              />
            </div>
          </div>

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

          {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
          <Button disabled={!canPublish || pending} onClick={onPublish}>
            {pending ? "Publishing…" : "Publish to community"}
          </Button>
        </>
      ) : null}
    </div>
  )
}
