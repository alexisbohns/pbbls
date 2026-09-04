"use client"

import { useMemo, useState, useTransition } from "react"
import { useRouter } from "next/navigation"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Badge } from "@/components/ui/badge"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { GlyphPreview } from "@/components/pebblestore/GlyphPreview"
import {
  GLYPH_CANVAS_VIEWBOX,
  IDENTITY_ADJUST,
  type Adjust,
  type GlyphStroke,
} from "@/lib/pebblestore/types"
import { svgToStrokes } from "@/lib/pebblestore/svg-to-strokes"
import { bakeAdjust } from "@/lib/pebblestore/transform-path"
import { familyLabel, type AdminAchievement } from "@/lib/achievements/types"
import { deleteAchievement, setAchievementGlyph, updateAchievement } from "../../actions"

/**
 * Badge editor. What it can change is deliberately narrow: copy, karma, order,
 * retirement and the visual. Family, threshold and the emotion/domain binding
 * define what the badge *means* — editing them under users who already unlocked
 * it would rewrite history, so the RPC refuses and they render read-only here.
 */
export function AchievementEditor({ achievement }: { achievement: AdminAchievement }) {
  const router = useRouter()

  const [titleEn, setTitleEn] = useState(achievement.title_en ?? "")
  const [titleFr, setTitleFr] = useState(achievement.title_fr ?? "")
  const [descriptionEn, setDescriptionEn] = useState(achievement.description_en ?? "")
  const [descriptionFr, setDescriptionFr] = useState(achievement.description_fr ?? "")
  const [karma, setKarma] = useState(String(achievement.karma_reward))
  const [sortOrder, setSortOrder] = useState(String(achievement.sort_order))
  const [isActive, setIsActive] = useState(achievement.is_active)

  // Glyph editing: null strokes = keep the current glyph (no new SVG staged).
  const [strokes, setStrokes] = useState<GlyphStroke[] | null>(null)
  const [viewBox, setViewBox] = useState(achievement.view_box ?? GLYPH_CANVAS_VIEWBOX)
  const [skipped, setSkipped] = useState<string[]>([])
  const [parseError, setParseError] = useState<string | null>(null)
  const [adjust, setAdjust] = useState<Adjust>(IDENTITY_ADJUST)

  const [formError, setFormError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [pending, startTransition] = useTransition()

  const hasNewGlyph = strokes !== null && strokes.length > 0

  const previewStrokes = useMemo(() => {
    if (hasNewGlyph) return bakeAdjust(strokes as GlyphStroke[], viewBox, adjust)
    return achievement.strokes ?? []
  }, [hasNewGlyph, strokes, viewBox, adjust, achievement.strokes])

  const previewViewBox = hasNewGlyph ? viewBox : achievement.view_box ?? GLYPH_CANVAS_VIEWBOX

  const karmaValue = Number(karma)
  const karmaValid =
    karma.trim() !== "" && Number.isInteger(karmaValue) && karmaValue >= 0 && karmaValue <= 32767
  const sortValue = Number(sortOrder)
  const sortValid = sortOrder.trim() !== "" && Number.isInteger(sortValue)

  const canSave =
    titleEn.trim().length > 0 && titleFr.trim().length > 0 && karmaValid && sortValid && !pending

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
      const res = await updateAchievement({
        id: achievement.id,
        titleEn: titleEn.trim(),
        titleFr: titleFr.trim(),
        descriptionEn: descriptionEn.trim(),
        descriptionFr: descriptionFr.trim(),
        karmaReward: karmaValue,
        sortOrder: sortValue,
        isActive,
      })
      if (res?.error) {
        setFormError(res.error)
        return
      }
      if (hasNewGlyph) {
        const baked = bakeAdjust(strokes as GlyphStroke[], viewBox, adjust)
        const glyphRes = await setAchievementGlyph({
          id: achievement.id,
          strokes: baked,
          viewBox,
        })
        if (glyphRes?.error) {
          setFormError(glyphRes.error)
          return
        }
        setStrokes(null)
      }
      toast.success("Achievement saved")
    })
  }

  const onDelete = () => {
    startTransition(async () => {
      const res = await deleteAchievement(achievement.id)
      if (res?.error) {
        setConfirmDelete(false)
        setFormError(res.error)
        return
      }
      router.push("/achievements")
    })
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-lg font-semibold">{achievement.title_en ?? achievement.slug}</h1>
          <p className="text-xs text-muted-foreground">{achievement.slug}</p>
        </div>
        {achievement.is_active ? null : <Badge variant="outline">Retired</Badge>}
      </div>

      <GlyphPreview
        strokes={previewStrokes}
        viewBox={previewViewBox}
        className="mx-auto aspect-square w-40 rounded-md border bg-card text-foreground"
      />

      <dl className="grid gap-2 rounded-lg border p-4 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-muted-foreground">Family</dt>
          <dd>{familyLabel(achievement.family)}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Threshold</dt>
          <dd>{achievement.threshold ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Bound to</dt>
          <dd>{achievement.emotion_slug ?? achievement.domain_slug ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Unlocked by</dt>
          <dd>
            {achievement.unlock_count} {achievement.unlock_count === 1 ? "user" : "users"}
          </dd>
        </div>
      </dl>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="title-en">Title (EN)</Label>
          <Input id="title-en" value={titleEn} onChange={(e) => setTitleEn(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="title-fr">Title (FR)</Label>
          <Input id="title-fr" value={titleFr} onChange={(e) => setTitleFr(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="desc-en">Description (EN)</Label>
          <Input
            id="desc-en"
            value={descriptionEn}
            onChange={(e) => setDescriptionEn(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="desc-fr">Description (FR)</Label>
          <Input
            id="desc-fr"
            value={descriptionFr}
            onChange={(e) => setDescriptionFr(e.target.value)}
          />
        </div>
      </div>
      <p className="text-xs text-muted-foreground">
        Titles here override the apps&rsquo; built-in copy for this badge, so both
        languages are required. Leave descriptions empty to fall back to the
        app&rsquo;s own wording.
      </p>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="karma">Karma reward</Label>
          <Input
            id="karma"
            type="number"
            min={0}
            max={32767}
            value={karma}
            onChange={(e) => setKarma(e.target.value)}
          />
          {karmaValid ? null : (
            <p className="text-xs text-destructive">A whole number from 0 to 32767.</p>
          )}
        </div>
        <div className="space-y-2">
          <Label htmlFor="sort-order">Sort order</Label>
          <Input
            id="sort-order"
            type="number"
            value={sortOrder}
            onChange={(e) => setSortOrder(e.target.value)}
          />
          {sortValid ? null : <p className="text-xs text-destructive">A whole number.</p>}
        </div>
      </div>

      <label className="flex items-center gap-3 text-sm">
        <Switch checked={isActive} onCheckedChange={setIsActive} />
        <span>
          Active
          <span className="block text-xs text-muted-foreground">
            Retiring a badge stops new unlocks and its karma. Users who already earned
            it keep it.
          </span>
        </span>
      </label>

      <div className="space-y-2">
        <Label htmlFor="svg-file">Replace badge glyph (SVG)</Label>
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

      <div className="flex items-center justify-between gap-4">
        <Button disabled={!canSave} onClick={onSave}>
          {pending ? "Saving…" : "Save"}
        </Button>
        {achievement.unlock_count === 0 ? (
          <Button
            type="button"
            variant="destructive"
            disabled={pending}
            onClick={() => setConfirmDelete(true)}
          >
            Delete
          </Button>
        ) : null}
      </div>

      <Dialog open={confirmDelete} onOpenChange={setConfirmDelete}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete this achievement?</DialogTitle>
            <DialogDescription>
              &ldquo;{achievement.title_en ?? achievement.slug}&rdquo; will be removed from
              the catalog. Nobody has unlocked it, so nothing is taken away from a user —
              but this cannot be undone. To take a badge out of circulation without
              deleting it, switch it to inactive instead.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setConfirmDelete(false)}>
              Cancel
            </Button>
            <Button type="button" variant="destructive" disabled={pending} onClick={onDelete}>
              {pending ? "Deleting…" : "Delete"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
