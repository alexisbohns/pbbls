"use client"

import { useMemo, useState, useTransition } from "react"
import { useRouter } from "next/navigation"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  ACHIEVEMENT_FAMILIES,
  COUNTING_FAMILIES,
  REFERENCE_FAMILIES,
  familyLabel,
  type AchievementFamily,
} from "@/lib/achievements/types"
import { createAchievement } from "../../actions"

type ReferenceRow = { id: string; slug: string; name: string }
type ExistingRow = {
  slug: string
  family: string
  threshold: number | null
  sortOrder: number
}

/**
 * Adds a new tier to an existing family. The family list is fixed: it mirrors
 * the column's CHECK, which is exactly what `check_achievements()` knows how to
 * evaluate — a new *kind* of rule needs a migration, not a form.
 *
 * The form mirrors the RPC's validation so the common mistakes are caught before
 * a round-trip, but the RPC stays the authority.
 */
export function NewAchievementForm({
  existing,
  emotions,
  domains,
}: {
  existing: ExistingRow[]
  emotions: ReferenceRow[]
  domains: ReferenceRow[]
}) {
  const router = useRouter()

  const [family, setFamily] = useState<AchievementFamily>("pebble_count")
  const [slug, setSlug] = useState("")
  const [threshold, setThreshold] = useState("")
  const [referenceId, setReferenceId] = useState<string | null>(null)
  const [titleEn, setTitleEn] = useState("")
  const [titleFr, setTitleFr] = useState("")
  const [descriptionEn, setDescriptionEn] = useState("")
  const [descriptionFr, setDescriptionFr] = useState("")
  const [karma, setKarma] = useState("0")
  const [sortOrder, setSortOrder] = useState("")
  const [formError, setFormError] = useState<string | null>(null)
  const [pending, startTransition] = useTransition()

  const needsThreshold = COUNTING_FAMILIES.includes(family)
  const needsReference = REFERENCE_FAMILIES.includes(family)
  const references = family === "emotion_first" ? emotions : domains

  // Existing tiers of the chosen family, so the ladder being extended is visible
  // and the next sort_order can be suggested rather than guessed.
  const siblings = useMemo(
    () =>
      existing
        .filter((a) => a.family === family)
        .sort((a, b) => a.sortOrder - b.sortOrder),
    [existing, family],
  )
  const suggestedSort = siblings.length > 0 ? siblings[siblings.length - 1].sortOrder + 10 : 900

  const trimmedSlug = slug.trim()
  const slugValid = /^[a-z0-9]+(-[a-z0-9]+)*$/.test(trimmedSlug)
  const slugTaken = existing.some((a) => a.slug === trimmedSlug)
  const thresholdValue = Number(threshold)
  const thresholdValid = needsThreshold
    ? threshold.trim() !== "" && Number.isInteger(thresholdValue) && thresholdValue > 0
    : true
  const karmaValue = Number(karma)
  const karmaValid =
    karma.trim() !== "" && Number.isInteger(karmaValue) && karmaValue >= 0 && karmaValue <= 32767
  const sortValue = sortOrder.trim() === "" ? suggestedSort : Number(sortOrder)
  const sortValid = Number.isInteger(sortValue)

  const canSave =
    slugValid &&
    !slugTaken &&
    thresholdValid &&
    karmaValid &&
    sortValid &&
    (!needsReference || referenceId !== null) &&
    titleEn.trim().length > 0 &&
    titleFr.trim().length > 0 &&
    !pending

  // Base UI hands back `null` when a select is cleared; the family always has a
  // value here, so a null is simply ignored.
  const onFamilyChange = (next: AchievementFamily | null) => {
    if (next === null) return
    setFamily(next)
    setReferenceId(null)
    setThreshold("")
  }

  const onCreate = () => {
    setFormError(null)
    startTransition(async () => {
      const res = await createAchievement({
        slug: trimmedSlug,
        family,
        threshold: needsThreshold ? thresholdValue : null,
        emotionId: family === "emotion_first" ? referenceId : null,
        domainId: family === "domain_first" ? referenceId : null,
        titleEn: titleEn.trim(),
        titleFr: titleFr.trim(),
        descriptionEn: descriptionEn.trim(),
        descriptionFr: descriptionFr.trim(),
        karmaReward: karmaValue,
        sortOrder: sortValue,
      })
      if ("error" in res) {
        setFormError(res.error)
        return
      }
      toast.success("Achievement created")
      router.push(`/achievements/${res.id}`)
    })
  }

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-lg font-semibold">New achievement</h1>
        <p className="text-sm text-muted-foreground">
          Adds a tier to one of the eight families the apps already evaluate. A new
          kind of rule needs a migration.
        </p>
      </header>

      <div className="space-y-2">
        <Label htmlFor="family">Family</Label>
        <Select name="family" value={family} onValueChange={onFamilyChange}>
          <SelectTrigger id="family">
            <SelectValue placeholder="Select a family" />
          </SelectTrigger>
          <SelectContent>
            {ACHIEVEMENT_FAMILIES.map((f) => (
              <SelectItem key={f} value={f}>
                {familyLabel(f)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {siblings.length > 0 ? (
          <p className="text-xs text-muted-foreground">
            Existing:{" "}
            {siblings
              .map((s) => (s.threshold !== null ? String(s.threshold) : s.slug))
              .join(" · ")}
          </p>
        ) : null}
      </div>

      {needsReference ? (
        <div className="space-y-2">
          <Label htmlFor="reference">
            {family === "emotion_first" ? "Emotion" : "Domain"}
          </Label>
          <Select
            name="reference"
            value={referenceId}
            onValueChange={(v) => setReferenceId(v as string | null)}
          >
            <SelectTrigger id="reference">
              <SelectValue placeholder="Select one" />
            </SelectTrigger>
            <SelectContent>
              {references.map((r) => (
                <SelectItem key={r.id} value={r.id}>
                  {r.name} ({r.slug})
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">
            Most of these already have a badge — the catalog generates one per emotion
            and per domain automatically.
          </p>
        </div>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="slug">Slug</Label>
          <Input
            id="slug"
            value={slug}
            onChange={(e) => setSlug(e.target.value)}
            placeholder="pebble-count-2000"
          />
          {trimmedSlug !== "" && !slugValid ? (
            <p className="text-xs text-destructive">
              Lowercase words joined by hyphens.
            </p>
          ) : null}
          {slugTaken ? (
            <p className="text-xs text-destructive">That slug is already taken.</p>
          ) : null}
        </div>
        {needsThreshold ? (
          <div className="space-y-2">
            <Label htmlFor="threshold">Threshold</Label>
            <Input
              id="threshold"
              type="number"
              min={1}
              value={threshold}
              onChange={(e) => setThreshold(e.target.value)}
            />
            {threshold.trim() !== "" && !thresholdValid ? (
              <p className="text-xs text-destructive">A whole number above zero.</p>
            ) : null}
          </div>
        ) : null}
      </div>

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
        Both languages are required: the apps have no built-in copy for a badge
        created here, so this is the only wording users will see.
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
            placeholder={String(suggestedSort)}
          />
          <p className="text-xs text-muted-foreground">
            Leave empty to use {suggestedSort}.
          </p>
        </div>
      </div>

      {formError ? <p className="text-sm text-destructive">{formError}</p> : null}

      <div className="flex items-center gap-3">
        <Button disabled={!canSave} onClick={onCreate}>
          {pending ? "Creating…" : "Create"}
        </Button>
        <p className="text-xs text-muted-foreground">
          The badge visual is set after creating, on its editor page.
        </p>
      </div>
    </div>
  )
}
