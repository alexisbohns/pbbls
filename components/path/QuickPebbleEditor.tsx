"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import {
  CalendarDays,
  Check,
  Compass,
  Fingerprint,
  Image,
  Layers,
  Lock,
  Globe,
  Plus,
  Search,
  Users,
  X,
} from "lucide-react"
import { motion, useReducedMotion } from "framer-motion"
import { usePebbles } from "@/lib/data/usePebbles"
import { usePebblesCount } from "@/lib/data/usePebblesCount"
import { useBounce } from "@/lib/data/useBounce"
import { todayLocal } from "@/lib/data/bounce-levels"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { useMarks } from "@/lib/data/useMarks"
import { compressImage } from "@/lib/utils/image-compress"
import { EMOTIONS } from "@/lib/config/emotions"
import { DOMAINS } from "@/lib/config/domains"
import { Button } from "@/components/ui/button"
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@/components/ui/popover"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogClose,
} from "@/components/ui/dialog"
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetClose,
} from "@/components/ui/sheet"
import { InlineDatePicker } from "@/components/record/InlineDatePicker"
import { TimeStepPicker } from "@/components/record/TimeStepPicker"
import { ValenceIntensityGrid } from "@/components/record/ValenceIntensityGrid"
import { CustomizationTile } from "@/components/record/CustomizationTile"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
import { cn } from "@/lib/utils"

type Intensity = 1 | 2 | 3
type Valence = -1 | 0 | 1

type QuickPebbleEditorProps = {
  onPebbleCreated?: (pebbleId: string) => void
}

function isNow(dateStr: string): boolean {
  const diff = Math.abs(Date.now() - new Date(dateStr).getTime())
  return diff < 60_000
}

export function QuickPebbleEditor({ onPebbleCreated }: QuickPebbleEditorProps) {
  const { addPebble } = usePebbles()
  const { souls, addSoul } = useSouls()
  const { collections, updateCollection } = useCollections()
  const { marks } = useMarks()
  const { pebblesCount, loading: countLoading } = usePebblesCount()
  const { bounceWindow, loading: bounceLoading } = useBounce()
  const prefersReducedMotion = useReducedMotion()

  // Collapse state
  const [expanded, setExpanded] = useState(false)
  const sectionRef = useRef<HTMLElement>(null)
  const titleInputRef = useRef<HTMLInputElement>(null)
  const hasAutoExpanded = useRef(false)

  // Form state
  const [name, setName] = useState("")
  const [description, setDescription] = useState("")
  const [happenedAt, setHappenedAt] = useState(() => new Date().toISOString())
  const [intensity, setIntensity] = useState<Intensity>(2)
  const [valence, setValence] = useState<Valence>(0)
  const [emotionId, setEmotionId] = useState("")
  const [domainIds, setDomainIds] = useState<string[]>([])
  const [soulIds, setSoulIds] = useState<string[]>([])
  const [markId, setMarkId] = useState<string | undefined>(undefined)
  const [collectionIds, setCollectionIds] = useState<string[]>([])
  const [instant, setInstant] = useState<string | undefined>(undefined)
  const [visibility, setVisibility] = useState<"private" | "public">("private")
  const [saving, setSaving] = useState(false)

  // Dialog/sheet state
  const [dateOpen, setDateOpen] = useState(false)
  const [tempDate, setTempDate] = useState(() => new Date())
  const [glyphOpen, setGlyphOpen] = useState(false)
  const [localMarkId, setLocalMarkId] = useState<string | undefined>(undefined)
  const [soulsOpen, setSoulsOpen] = useState(false)

  // Search state
  const [emotionQuery, setEmotionQuery] = useState("")
  const [soulQuery, setSoulQuery] = useState("")

  // File input ref
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Auto-expand: new users (<5 pebbles) or no pebble created today
  const shouldAutoExpand = useMemo(() => {
    if (countLoading || bounceLoading) return false
    if (pebblesCount < 5) return true
    return !bounceWindow.includes(todayLocal())
  }, [pebblesCount, countLoading, bounceWindow, bounceLoading])

  useEffect(() => {
    if (hasAutoExpanded.current) return
    if (countLoading || bounceLoading) return
    hasAutoExpanded.current = true
    if (shouldAutoExpand) {
      setExpanded(true)
      requestAnimationFrame(() => titleInputRef.current?.focus())
    }
  }, [shouldAutoExpand, countLoading, bounceLoading])

  const selectedEmotion = EMOTIONS.find((e) => e.id === emotionId)
  const selectedDomains = DOMAINS.filter((d) => domainIds.includes(d.id))
  const selectedMark = marks.find((m) => m.id === markId)

  const filteredEmotions = useMemo(() => {
    if (!emotionQuery.trim()) return EMOTIONS
    const q = emotionQuery.toLowerCase()
    return EMOTIONS.filter((e) => e.name.toLowerCase().includes(q))
  }, [emotionQuery])

  const filteredSouls = useMemo(() => {
    if (!soulQuery.trim()) return souls
    const q = soulQuery.toLowerCase()
    return souls.filter((s) => s.name.toLowerCase().includes(q))
  }, [souls, soulQuery])

  const canAddNewSoul = useMemo(() => {
    if (!soulQuery.trim()) return false
    return !souls.some((s) => s.name.toLowerCase() === soulQuery.trim().toLowerCase())
  }, [souls, soulQuery])

  const resetForm = useCallback(() => {
    setName("")
    setDescription("")
    setHappenedAt(new Date().toISOString())
    setIntensity(2)
    setValence(0)
    setEmotionId("")
    setDomainIds([])
    setSoulIds([])
    setMarkId(undefined)
    setCollectionIds([])
    setInstant(undefined)
    setVisibility("private")
  }, [])

  const handleSubmit = useCallback(async () => {
    if (!name.trim() || saving) return
    setSaving(true)

    try {
      const finalHappenedAt = isNow(happenedAt) ? new Date().toISOString() : happenedAt

      const input = {
        name: name.trim(),
        description: description.trim(),
        happened_at: finalHappenedAt,
        intensity,
        positiveness: valence,
        visibility,
        emotion_id: emotionId || "serenity",
        soul_ids: soulIds,
        domain_ids: domainIds,
        mark_id: markId,
        instants: instant ? [instant] : [],
        cards: [],
      }

      const pebble = await addPebble(input)

      // Add pebble to selected collections
      for (const collId of collectionIds) {
        const coll = collections.find((c) => c.id === collId)
        if (coll) {
          await updateCollection(collId, {
            pebble_ids: [...coll.pebble_ids, pebble.id],
          })
        }
      }

      resetForm()
      setExpanded(false)
      titleInputRef.current?.blur()
      onPebbleCreated?.(pebble.id)
    } finally {
      setSaving(false)
    }
  }, [
    name, description, happenedAt, intensity, valence, visibility,
    emotionId, soulIds, domainIds, markId, collectionIds, instant,
    saving, addPebble, collections, updateCollection, resetForm, onPebbleCreated,
  ])

  const handleDateChange = useCallback((date: Date) => {
    setTempDate(date)
  }, [])

  const handleTimeChange = useCallback((date: Date) => {
    setTempDate((prev) => {
      const next = new Date(prev)
      next.setHours(date.getHours(), date.getMinutes(), 0, 0)
      return next
    })
  }, [])

  const toggleDomain = useCallback((id: string) => {
    setDomainIds((prev) =>
      prev.includes(id) ? prev.filter((d) => d !== id) : [...prev, id],
    )
  }, [])

  const toggleSoul = useCallback((id: string) => {
    setSoulIds((prev) =>
      prev.includes(id) ? prev.filter((s) => s !== id) : [...prev, id],
    )
  }, [])

  const handleAddSoul = useCallback(async () => {
    const trimmed = soulQuery.trim()
    if (!trimmed) return
    const soul = await addSoul({ name: trimmed })
    setSoulIds((prev) => [...prev, soul.id])
    setSoulQuery("")
  }, [soulQuery, addSoul])

  const toggleCollection = useCallback((id: string) => {
    setCollectionIds((prev) =>
      prev.includes(id) ? prev.filter((c) => c !== id) : [...prev, id],
    )
  }, [])

  const handleFileChange = useCallback(async (files: FileList | null) => {
    if (!files || files.length === 0) return
    const compressed = await compressImage(files[0])
    setInstant(compressed)
  }, [])

  // Focus tracking — expand on focus, collapse on blur when empty
  const handleFocusCapture = useCallback(() => {
    if (!expanded) setExpanded(true)
  }, [expanded])

  const handleBlurCapture = useCallback(() => {
    // rAF lets the browser settle focus after portal transitions
    requestAnimationFrame(() => {
      const active = document.activeElement
      if (active && sectionRef.current?.contains(active)) return
      if (active instanceof HTMLElement) {
        const inPortal = active.closest(
          '[data-slot="popover-content"], [data-slot="popover-positioner"], [data-slot="dialog-content"], [data-slot="sheet-content"], [role="dialog"]',
        )
        if (inPortal) return
      }
      if (!name.trim()) setExpanded(false)
    })
  }, [name])

  // Collapse/expand animation
  const collapsibleVariants = {
    expanded: { height: "auto", opacity: 1 },
    collapsed: { height: 0, opacity: 0 },
  }
  const collapsibleTransition = prefersReducedMotion
    ? { duration: 0 }
    : { height: { duration: 0.25, ease: "easeInOut" }, opacity: { duration: 0.15 } }

  const dateLabel = isNow(happenedAt)
    ? "Now"
    : new Intl.DateTimeFormat(undefined, {
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit",
      }).format(new Date(happenedAt))

  return (
    <section
      ref={sectionRef}
      className={cn("rounded-xl border bg-card transition-[padding] duration-200", expanded ? "p-4" : "px-4 py-3")}
      aria-label="Pebble editor"
      onFocusCapture={handleFocusCapture}
      onBlurCapture={handleBlurCapture}
    >
      {/* Collapsible: header row above title */}
      <motion.div
        initial={false}
        animate={expanded ? "expanded" : "collapsed"}
        variants={collapsibleVariants}
        transition={collapsibleTransition}
        style={{ overflow: "hidden" }}
        aria-hidden={!expanded}
      >
        {/* Header: date + intensity/valence grid */}
        <div className="mb-3 flex items-center justify-between">
          <button
            type="button"
            onClick={() => {
              setTempDate(new Date(happenedAt))
              setDateOpen(true)
            }}
            className="inline-flex items-center gap-1.5 rounded-md border border-border bg-background px-2.5 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          >
            <CalendarDays className="size-3.5" aria-hidden />
            {dateLabel}
          </button>

          <ValenceIntensityGrid
            intensity={intensity}
            valence={valence}
            onIntensityChange={setIntensity}
            onValenceChange={setValence}
          />
        </div>
      </motion.div>

      {/* Title input — always visible */}
      <input
        ref={titleInputRef}
        type="text"
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="What happened?"
        className={cn(
          "w-full border-none bg-transparent font-heading text-xl font-semibold text-foreground outline-none placeholder:text-muted-foreground/50",
          expanded && "mb-2",
        )}
        onKeyDown={(e) => {
          if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault()
            void handleSubmit()
          }
        }}
      />

      {/* Collapsible: content below title */}
      <motion.div
        initial={false}
        animate={expanded ? "expanded" : "collapsed"}
        variants={collapsibleVariants}
        transition={collapsibleTransition}
        style={{ overflow: "hidden" }}
        aria-hidden={!expanded}
      >
      {/* Qualification pills: domain + emotion */}
      <div className="mb-3 flex items-center gap-2">
        {/* Domain pill */}
        <Popover>
          <PopoverTrigger
            className={cn(
              "inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
              domainIds.length > 0
                ? "border border-border bg-background text-foreground"
                : "border border-dashed border-muted-foreground/30 text-muted-foreground hover:border-muted-foreground/50",
            )}
            aria-label={domainIds.length > 0 ? `Domains: ${selectedDomains.map((d) => d.name).join(", ")}` : "Pick domains"}
          >
            <Compass className="size-3.5" aria-hidden />
            {selectedDomains.length > 0
              ? selectedDomains.map((d) => d.name).join(", ")
              : "Domain"}
          </PopoverTrigger>
          <PopoverContent align="start" className="min-w-[180px]">
            {DOMAINS.map((domain) => {
              const selected = domainIds.includes(domain.id)
              return (
                <button
                  key={domain.id}
                  type="button"
                  onClick={() => toggleDomain(domain.id)}
                  aria-pressed={selected}
                  className={cn(
                    "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm outline-none transition-colors hover:bg-muted",
                    selected && "font-medium",
                  )}
                >
                  <span className="flex size-4 shrink-0 items-center justify-center">
                    {selected && <Check className="size-4" />}
                  </span>
                  <span className="flex flex-col items-start">
                    <span>{domain.name}</span>
                    <span className="text-xs text-muted-foreground">{domain.label}</span>
                  </span>
                </button>
              )
            })}
          </PopoverContent>
        </Popover>

        {/* Emotion pill */}
        <Popover onOpenChange={(open) => { if (!open) setEmotionQuery("") }}>
          <PopoverTrigger
            className={cn(
              "inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
              emotionId
                ? "border border-border bg-background text-foreground"
                : "border border-dashed border-muted-foreground/30 text-muted-foreground hover:border-muted-foreground/50",
            )}
            aria-label={selectedEmotion ? `Emotion: ${selectedEmotion.name}` : "Pick emotion"}
          >
            {selectedEmotion ? (
              <>
                <span
                  className="size-2.5 rounded-full shrink-0"
                  style={{ backgroundColor: selectedEmotion.color }}
                  aria-hidden
                />
                {selectedEmotion.name}
              </>
            ) : (
              <>
                <span className="size-2.5 rounded-full shrink-0 border border-dashed border-muted-foreground/30" aria-hidden />
                Emotion
              </>
            )}
          </PopoverTrigger>
          <PopoverContent align="start" className="min-w-[200px] p-2">
            <div className="flex items-center gap-2 border-b border-border pb-2 mb-1">
              <Search className="size-4 shrink-0 text-muted-foreground" aria-hidden />
              <input
                type="text"
                value={emotionQuery}
                onChange={(e) => setEmotionQuery(e.target.value)}
                placeholder="Search emotions…"
                className="h-7 w-full min-w-0 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
              />
            </div>
            <div className="max-h-[200px] overflow-y-auto">
              {filteredEmotions.map((emotion) => {
                const selected = emotionId === emotion.id
                return (
                  <button
                    key={emotion.id}
                    type="button"
                    onClick={() => setEmotionId(emotion.id)}
                    className={cn(
                      "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm outline-none transition-colors hover:bg-muted",
                      selected && "font-medium",
                    )}
                  >
                    <span className="flex size-4 shrink-0 items-center justify-center">
                      {selected && <Check className="size-4" />}
                    </span>
                    <span
                      className="size-3 rounded-full shrink-0"
                      style={{ backgroundColor: emotion.color }}
                      aria-hidden
                    />
                    {emotion.name}
                  </button>
                )
              })}
              {filteredEmotions.length === 0 && (
                <p className="px-2 py-4 text-center text-sm text-muted-foreground">
                  No emotions found
                </p>
              )}
            </div>
          </PopoverContent>
        </Popover>
      </div>

      {/* Description textarea */}
      <textarea
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        placeholder="What was that awesome?"
        className="mb-4 w-full resize-none border-none bg-transparent text-sm text-foreground outline-none field-sizing-content placeholder:text-muted-foreground/50"
        rows={1}
      />

      {/* Customization tiles: glyph, collection, souls, photo */}
      <div className="mb-4 grid grid-cols-4 gap-2">
        {/* Glyph tile */}
        <CustomizationTile
          icon={Fingerprint}
          filled={!!selectedMark}
          onClick={() => {
            setLocalMarkId(markId)
            setGlyphOpen(true)
          }}
          ariaLabel={selectedMark ? "Change glyph" : "Add glyph"}
        >
          {selectedMark && (
            <GlyphPreview mark={selectedMark} className="size-full p-2" />
          )}
        </CustomizationTile>

        {/* Collection tile */}
        <Popover>
          <PopoverTrigger
            className={cn(
              "relative flex aspect-square items-center justify-center rounded-xl transition-all duration-100 outline-none focus-visible:ring-2 focus-visible:ring-ring active:scale-95 overflow-hidden",
              collectionIds.length > 0
                ? "border border-border bg-muted/50"
                : "border border-dashed border-muted-foreground/30 hover:border-muted-foreground/50 hover:bg-muted/30",
            )}
            aria-label={collectionIds.length > 0 ? `${collectionIds.length} collection(s) selected` : "Add to collection"}
          >
            {collectionIds.length > 0 ? (
              <span className="text-xs font-medium text-muted-foreground">
                {collectionIds.length}
              </span>
            ) : (
              <Layers className="size-5 text-muted-foreground/50" aria-hidden />
            )}
          </PopoverTrigger>
          <PopoverContent align="start" className="min-w-[180px]">
            {collections.length === 0 ? (
              <p className="px-2 py-4 text-center text-sm text-muted-foreground">
                No collections yet
              </p>
            ) : (
              collections.map((coll) => {
                const selected = collectionIds.includes(coll.id)
                return (
                  <button
                    key={coll.id}
                    type="button"
                    onClick={() => toggleCollection(coll.id)}
                    aria-pressed={selected}
                    className={cn(
                      "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm outline-none transition-colors hover:bg-muted",
                      selected && "font-medium",
                    )}
                  >
                    <span className="flex size-4 shrink-0 items-center justify-center">
                      {selected && <Check className="size-4" />}
                    </span>
                    {coll.name}
                  </button>
                )
              })
            )}
          </PopoverContent>
        </Popover>

        {/* Souls tile */}
        <CustomizationTile
          icon={Users}
          filled={soulIds.length > 0}
          onClick={() => {
            setSoulQuery("")
            setSoulsOpen(true)
          }}
          ariaLabel={soulIds.length > 0 ? `${soulIds.length} soul(s) selected` : "Add souls"}
        >
          {soulIds.length > 0 && (
            <span className="text-xs font-medium text-muted-foreground">
              {soulIds.length}
            </span>
          )}
        </CustomizationTile>

        {/* Photo tile */}
        <CustomizationTile
          icon={Image}
          filled={!!instant}
          onClick={() => {
            if (instant) {
              setInstant(undefined)
            } else {
              fileInputRef.current?.click()
            }
          }}
          ariaLabel={instant ? "Remove photo" : "Add photo"}
        >
          {instant && (
            <img
              src={instant}
              alt="Uploaded photo"
              className="size-full object-cover"
            />
          )}
        </CustomizationTile>
      </div>

      {/* Hidden file input for photo upload */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        className="hidden"
        aria-hidden="true"
        tabIndex={-1}
        onChange={(e) => {
          void handleFileChange(e.target.files)
          e.target.value = ""
        }}
      />

      {/* Footer: privacy picker + save button */}
      <div className="flex items-center justify-between">
        <Popover>
          <PopoverTrigger
            className="inline-flex items-center gap-1.5 rounded-md border border-border bg-background px-2.5 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            aria-label={`Visibility: ${visibility}`}
          >
            {visibility === "private" ? (
              <Lock className="size-3.5" aria-hidden />
            ) : (
              <Globe className="size-3.5" aria-hidden />
            )}
            {visibility === "private" ? "Private" : "Public"}
          </PopoverTrigger>
          <PopoverContent align="start" className="min-w-[140px]">
            <button
              type="button"
              onClick={() => setVisibility("private")}
              className={cn(
                "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm outline-none transition-colors hover:bg-muted",
                visibility === "private" && "font-medium",
              )}
            >
              <Lock className="size-4 shrink-0" />
              Private
            </button>
            <button
              type="button"
              onClick={() => setVisibility("public")}
              className={cn(
                "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm outline-none transition-colors hover:bg-muted",
                visibility === "public" && "font-medium",
              )}
            >
              <Globe className="size-4 shrink-0" />
              Public
            </button>
          </PopoverContent>
        </Popover>

        <Button
          variant="default"
          size="icon"
          disabled={!name.trim() || saving}
          onClick={() => void handleSubmit()}
          aria-label="Save pebble"
          className="size-9 rounded-full"
        >
          <Check className="size-5" aria-hidden />
        </Button>
      </div>
      </motion.div>

      {/* Date picker dialog */}
      <Dialog open={dateOpen} onOpenChange={setDateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>When</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <InlineDatePicker value={tempDate} onChange={handleDateChange} />
            <div className="flex items-center justify-center gap-3">
              <TimeStepPicker value={tempDate} onChange={handleTimeChange} />
              <Button
                variant="outline"
                onClick={() => setTempDate(new Date())}
              >
                Now
              </Button>
            </div>
          </div>
          <DialogFooter>
            <DialogClose>Cancel</DialogClose>
            <Button
              onClick={() => {
                setHappenedAt(tempDate.toISOString())
                setDateOpen(false)
              }}
            >
              Done
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Glyph picker dialog */}
      <Dialog
        open={glyphOpen}
        onOpenChange={(open) => {
          setGlyphOpen(open)
          if (open) setLocalMarkId(markId)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Glyph</DialogTitle>
          </DialogHeader>

          {marks.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No glyphs yet. Carve one from the Glyphs page.
            </p>
          ) : (
            <ul
              role="radiogroup"
              aria-label="Glyphs"
              className="grid grid-cols-4 gap-2"
            >
              {marks.map((mark) => {
                const selected = localMarkId === mark.id
                return (
                  <li key={mark.id}>
                    <button
                      type="button"
                      role="radio"
                      aria-checked={selected}
                      aria-label={mark.name ?? `Glyph ${mark.id.slice(0, 4)}`}
                      onClick={() => setLocalMarkId((prev) => (prev === mark.id ? undefined : mark.id))}
                      className={cn(
                        "flex size-16 items-center justify-center rounded-lg border p-1 transition-all duration-100 outline-none focus-visible:ring-3 focus-visible:ring-ring/50 active:scale-95",
                        selected
                          ? "border-primary bg-primary/10 ring-2 ring-primary"
                          : "border-input hover:bg-muted",
                      )}
                    >
                      <GlyphPreview mark={mark} className="size-full" />
                    </button>
                  </li>
                )
              })}
            </ul>
          )}

          <DialogFooter>
            <DialogClose>Cancel</DialogClose>
            <Button
              onClick={() => {
                setMarkId(localMarkId)
                setGlyphOpen(false)
              }}
            >
              Save
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Souls picker sheet */}
      <Sheet open={soulsOpen} onOpenChange={setSoulsOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Souls</SheetTitle>
          </SheetHeader>
          <div className="flex items-center gap-2 border-b border-border pb-2 mb-3">
            <Search className="size-4 shrink-0 text-muted-foreground" aria-hidden />
            <input
              type="text"
              value={soulQuery}
              onChange={(e) => setSoulQuery(e.target.value)}
              placeholder="Search souls…"
              className="h-8 w-full min-w-0 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
              onKeyDown={(e) => {
                if (e.key === "Enter" && canAddNewSoul) {
                  e.preventDefault()
                  void handleAddSoul()
                }
              }}
            />
          </div>

          {/* Selected chips */}
          {soulIds.length > 0 && (
            <ul className="mb-3 flex flex-wrap gap-1.5" role="list" aria-label="Selected souls">
              {soulIds.map((id) => {
                const soul = souls.find((s) => s.id === id)
                if (!soul) return null
                return (
                  <li key={id}>
                    <button
                      type="button"
                      onClick={() => toggleSoul(id)}
                      className="inline-flex items-center gap-1 rounded-md bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary"
                      aria-label={`Remove ${soul.name}`}
                    >
                      {soul.name}
                      <X className="size-3" aria-hidden />
                    </button>
                  </li>
                )
              })}
            </ul>
          )}

          <div className="max-h-[300px] overflow-y-auto">
            {filteredSouls.map((soul) => {
              const selected = soulIds.includes(soul.id)
              return (
                <button
                  key={soul.id}
                  type="button"
                  onClick={() => toggleSoul(soul.id)}
                  className={cn(
                    "flex w-full items-center gap-2 rounded-lg px-2 py-2 text-sm outline-none transition-colors hover:bg-muted",
                    selected && "font-medium",
                  )}
                >
                  <span className="flex size-4 shrink-0 items-center justify-center">
                    {selected && <Check className="size-4" />}
                  </span>
                  {soul.name}
                </button>
              )
            })}
            {canAddNewSoul && (
              <button
                type="button"
                onClick={() => void handleAddSoul()}
                className="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-sm text-muted-foreground outline-none transition-colors hover:bg-muted hover:text-foreground"
              >
                <Plus className="size-4 shrink-0" />
                Add &quot;{soulQuery.trim()}&quot;
              </button>
            )}
            {filteredSouls.length === 0 && !canAddNewSoul && (
              <p className="px-2 py-4 text-center text-sm text-muted-foreground">
                No souls found
              </p>
            )}
          </div>

          <div className="mt-4 flex justify-end">
            <SheetClose aria-label="Done">
              Done
            </SheetClose>
          </div>
        </SheetContent>
      </Sheet>
    </section>
  )
}
