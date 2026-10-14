"use client"

import { useCallback, useState, type ReactNode } from "react"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import type { CompletedMoment, Mark, Pebble } from "@/lib/types"
import { useMoments } from "@/lib/data/useMoments"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { legacyPebbleFromMoment } from "@/lib/data/moment-row"
import { completePebblePayload } from "@/lib/data/moment-payloads"
import { momentErrorKey } from "@/lib/data/moment-write-error"
import { usePebbleEnrichDraft } from "@/lib/hooks/usePebbleEnrichDraft"
import { PebbleFramed } from "@/components/pebble/PebbleFramed"
import { EmotionTile, DomainTile, CollectionTile } from "@/components/pebble/PebbleDetailTiles"
import { PebbleEditToolbar } from "@/components/pebble/PebbleEditToolbar"
import { PebbleEditSoulsGrid } from "@/components/pebble/PebbleEditSoulsGrid"
import { SheetTrigger } from "@/components/ui/sheet"
import { PickerSheet } from "@/components/ui/PickerSheet"
import { ValenceGrid } from "@/components/record/ValenceIntensityGrid"
import { SoulsSheet } from "@/components/record/SoulsSheet"
import { VISIBILITY_ICONS, VisibilityMenu } from "@/components/record/VisibilityMenu"
import { Popover, PopoverTrigger, PopoverContent } from "@/components/ui/popover"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"

type PebbleEnrichProps = {
  moment: CompletedMoment
  marks: Mark[]
}

function FormSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="mt-8">
      <h2 className="mb-3 text-xs font-medium uppercase tracking-[0.1em] text-muted-foreground">{title}</h2>
      {children}
    </section>
  )
}

/**
 * Edit pebble (R5): valence, emotion, domain, souls, collection and privacy,
 * saved through one `complete_pebble` that always sends every key. Ported from
 * iOS `EditPebbleSheet`. What the edit earned shows in the karma pill
 * (`pebble_enriched`), then achievements are checked once.
 *
 * Domain and collection are single-select, but an untouched legacy multi-set
 * goes back verbatim (PD-W11).
 */
export function PebbleEnrich({ moment, marks }: PebbleEnrichProps) {
  const t = useTranslations("moment.editPebble")
  const tEdit = useTranslations("pebble.edit")
  const tPebble = useTranslations("pebble")
  const tErrors = useTranslations("moment.errors")
  const tVisibility = useTranslations("record.visibility")
  const tValencePicker = useTranslations("record.valencePicker")
  const router = useRouter()
  const { completePebble } = useMoments()
  const { souls, addSoul } = useSouls()
  const { collections } = useCollections()

  const form = usePebbleEnrichDraft(moment.pebble)
  const { draft } = form

  const [valenceOpen, setValenceOpen] = useState(false)
  const [soulsOpen, setSoulsOpen] = useState(false)
  const [confirmDiscardOpen, setConfirmDiscardOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  const readPage = `/pebble/${moment.id}`
  const canSave = form.canSave && !saving

  const handleCancel = useCallback(() => {
    if (saving) return
    if (!form.isDirty) {
      router.replace(readPage)
      return
    }
    setConfirmDiscardOpen(true)
  }, [saving, form.isDirty, router, readPage])

  const handleSave = useCallback(async () => {
    if (!canSave) return
    const payload = completePebblePayload(moment.id, draft)
    if (!payload) return
    setSaving(true)
    setSaveError(null)
    try {
      await completePebble(payload, { reason: "pebble_enriched" })
      router.replace(readPage)
    } catch (err) {
      console.error("[pebble-enrich] complete_pebble failed", err)
      setSaveError(tErrors(momentErrorKey(err)))
      setSaving(false)
    }
  }, [canSave, moment.id, draft, completePebble, router, readPage, tErrors])

  const handleAddSoul = useCallback(
    async (name: string) => {
      const soul = await addSoul({ name })
      form.addSoul(soul.id)
    },
    [addSoul, form],
  )

  // The stone reflects the staged look; its glyph is the server's and follows
  // a domain or collection change once saved.
  const draftPebble: Pebble = {
    ...legacyPebbleFromMoment(moment),
    intensity: draft.intensity,
    positiveness: draft.positiveness,
    emotion_id: draft.emotionId ?? moment.pebble.emotion_id,
  }
  const VisibilityIcon = VISIBILITY_ICONS[draft.visibility]

  return (
    <article className="mx-auto max-w-md pb-12">
      <PebbleEditToolbar
        title={t("title")}
        isSaving={saving}
        canSave={canSave}
        onCancel={handleCancel}
        onSave={() => void handleSave()}
      />

      <FormSection title={t("mood")}>
        <PickerSheet
          open={valenceOpen}
          onOpenChange={setValenceOpen}
          title={tValencePicker("title")}
          closeLabel={tValencePicker("close")}
          trigger={
            <SheetTrigger
              aria-label={tPebble("editIntensityAria")}
              className="mx-auto mb-4 block cursor-pointer rounded-2xl outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <PebbleFramed pebble={draftPebble} className="mx-auto size-24" />
            </SheetTrigger>
          }
        >
          {valenceOpen && (
            <ValenceGrid
              intensity={draft.intensity}
              valence={draft.positiveness}
              onSelect={(intensity, positiveness) => {
                form.setValence(intensity, positiveness)
                setValenceOpen(false)
              }}
            />
          )}
        </PickerSheet>
        <div className="grid grid-cols-2 gap-3">
          <EmotionTile editing value={draft.emotionId ?? ""} onChange={(id) => {
              if (id) form.setEmotion(id)
            }} />
          <DomainTile editing single value={draft.domainIds} onChange={form.setDomains} />
        </div>
      </FormSection>

      <FormSection title={t("souls")}>
        <PebbleEditSoulsGrid
          soulIds={draft.soulIds}
          souls={souls}
          marks={marks}
          onAddRequest={() => setSoulsOpen(true)}
          onRemove={form.toggleSoul}
        />
      </FormSection>

      <FormSection title={t("optional")}>
        <CollectionTile
          editing
          single
          value={draft.collectionIds}
          onChange={form.setCollections}
          collections={collections}
          className="w-full"
        />
      </FormSection>

      <FormSection title={t("privacy")}>
        <Popover>
          <PopoverTrigger
            aria-label={tVisibility("aria", { grade: tVisibility(draft.visibility) })}
            className="inline-flex items-center gap-2 rounded-full bg-surface px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-muted/70 outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <VisibilityIcon className="size-4 text-muted-foreground" aria-hidden />
            {tVisibility(draft.visibility)}
          </PopoverTrigger>
          <PopoverContent align="start" className="min-w-[160px]">
            <VisibilityMenu value={draft.visibility} onChange={form.setVisibility} />
          </PopoverContent>
        </Popover>
      </FormSection>

      {saveError && (
        <p
          role="alert"
          className="mt-6 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {saveError}
        </p>
      )}

      <SoulsSheet
        open={soulsOpen}
        onOpenChange={setSoulsOpen}
        selectedIds={draft.soulIds}
        onToggle={form.toggleSoul}
        souls={souls}
        marks={marks}
        onAddSoul={handleAddSoul}
      />

      <AlertDialog open={confirmDiscardOpen} onOpenChange={setConfirmDiscardOpen}>
        <AlertDialogContent size="sm">
          <AlertDialogHeader>
            <AlertDialogTitle>{tEdit("discardTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{tEdit("discardDescription")}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{tEdit("discardKeep")}</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => router.replace(readPage)}>
              {tEdit("discardConfirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </article>
  )
}
