"use client"

import { useCallback, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import {
  Heart,
  Layers,
  Users,
  ArrowUpCircle,
  CalendarDays,
  Check,
  Plus,
} from "lucide-react"
import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { useDataProvider } from "@/lib/data/provider-context"
import { computeKarmaDelta } from "@/lib/data/karma"
import { EMOTIONS } from "@/lib/config/emotions"
import { DOMAINS } from "@/lib/config/domains"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@/components/ui/popover"
import {
  Combobox,
  ComboboxInput,
  ComboboxContent,
  ComboboxList,
  ComboboxItem,
  ComboboxEmpty,
} from "@/components/ui/combobox"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogClose,
} from "@/components/ui/dialog"
import { InlineDatePicker } from "@/components/record/InlineDatePicker"
import { TimeStepPicker } from "@/components/record/TimeStepPicker"
import { cn } from "@/lib/utils"

type Intensity = 1 | 2 | 3
type Valence = -1 | 0 | 1
type EditorMode = "quick" | "normal"

const INTENSITY_OPTIONS: { value: Intensity; label: string }[] = [
  { value: 1, label: "Small" },
  { value: 2, label: "Medium" },
  { value: 3, label: "Huge" },
]

const VALENCE_OPTIONS: { value: Valence; label: string }[] = [
  { value: 1, label: "highlight" },
  { value: 0, label: "neutral" },
  { value: -1, label: "lowlight" },
]

function isNow(dateStr: string): boolean {
  const diff = Math.abs(Date.now() - new Date(dateStr).getTime())
  return diff < 60_000
}

export function QuickPebbleEditor() {
  const router = useRouter()
  const { addPebble } = usePebbles()
  const { souls, addSoul } = useSouls()
  const { provider } = useDataProvider()

  const [name, setName] = useState("")
  const [happenedAt, setHappenedAt] = useState(() => new Date().toISOString())
  const [intensity, setIntensity] = useState<Intensity>(1)
  const [valence, setValence] = useState<Valence>(0)
  const [emotionId, setEmotionId] = useState("")
  const [domainIds, setDomainIds] = useState<string[]>([])
  const [soulIds, setSoulIds] = useState<string[]>([])
  const [mode, setMode] = useState<EditorMode>("quick")

  // Date picker dialog state (kept as Dialog — too large for popover)
  const [dateOpen, setDateOpen] = useState(false)
  const [tempDate, setTempDate] = useState(() => new Date())

  // Soul combobox search state
  const [soulQuery, setSoulQuery] = useState("")

  const selectedEmotion = EMOTIONS.find((e) => e.id === emotionId)

  const intensityLabel = INTENSITY_OPTIONS.find((o) => o.value === intensity)?.label ?? "Small"
  const valenceLabel = VALENCE_OPTIONS.find((o) => o.value === valence)?.label ?? "neutral"

  // Filter souls by search query
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
    setHappenedAt(new Date().toISOString())
    setIntensity(1)
    setValence(0)
    setEmotionId("")
    setDomainIds([])
    setSoulIds([])
  }, [])

  const handleSubmit = useCallback(async () => {
    if (!name.trim()) return

    const finalHappenedAt = isNow(happenedAt) ? new Date().toISOString() : happenedAt

    if (mode === "normal") {
      const params = new URLSearchParams()
      params.set("prefill", "1")
      params.set("name", name.trim())
      params.set("happened_at", finalHappenedAt)
      params.set("intensity", String(intensity))
      params.set("positiveness", String(valence))
      if (emotionId) params.set("emotion_id", emotionId)
      if (domainIds.length > 0) params.set("domain_ids", domainIds.join(","))
      if (soulIds.length > 0) params.set("soul_ids", soulIds.join(","))
      router.push(`/record?${params.toString()}`)
      return
    }

    const input = {
      name: name.trim(),
      happened_at: finalHappenedAt,
      intensity,
      positiveness: valence as -2 | -1 | 0 | 1 | 2,
      emotion_id: emotionId || "serenity",
      soul_ids: soulIds,
      domain_ids: domainIds,
      instants: [],
      cards: [],
    }

    const karmaDelta = computeKarmaDelta(input)
    await addPebble(input)
    await provider.incrementPebblesCount()
    await provider.incrementKarma(karmaDelta, "quick pebble")
    await provider.refreshBounce()

    resetForm()
  }, [name, happenedAt, intensity, valence, emotionId, domainIds, soulIds, mode, addPebble, provider, resetForm, router])

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

  const dateLabel = isNow(happenedAt) ? "Now" : new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(happenedAt))

  return (
    <section
      className="rounded-xl border border-border/50 bg-card p-3"
      aria-label="Quick pebble editor"
    >
      {/* Header: date, intensity, valence */}
      <div className="mb-2 flex flex-wrap items-center gap-1">
        {/* Date button (opens Dialog) */}
        <button
          type="button"
          onClick={() => {
            setTempDate(new Date(happenedAt))
            setDateOpen(true)
          }}
          className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        >
          <CalendarDays className="size-3.5" aria-hidden />
          {dateLabel}
        </button>

        {/* Intensity popover */}
        <Popover>
          <PopoverTrigger
            className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          >
            {intensityLabel}
          </PopoverTrigger>
          <PopoverContent align="start">
            {INTENSITY_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                type="button"
                onClick={() => setIntensity(opt.value)}
                className={cn(
                  "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm outline-none transition-colors hover:bg-muted",
                  intensity === opt.value && "font-medium",
                )}
              >
                <span className="size-4 shrink-0 flex items-center justify-center">
                  {intensity === opt.value && <Check className="size-4" />}
                </span>
                {opt.label}
              </button>
            ))}
          </PopoverContent>
        </Popover>

        {/* Valence popover */}
        <Popover>
          <PopoverTrigger
            className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          >
            {valenceLabel}
          </PopoverTrigger>
          <PopoverContent align="start">
            {VALENCE_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                type="button"
                onClick={() => setValence(opt.value)}
                className={cn(
                  "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm outline-none transition-colors hover:bg-muted",
                  valence === opt.value && "font-medium",
                )}
              >
                <span className="size-4 shrink-0 flex items-center justify-center">
                  {valence === opt.value && <Check className="size-4" />}
                </span>
                {opt.label}
              </button>
            ))}
          </PopoverContent>
        </Popover>
      </div>

      {/* Body: name input */}
      <Input
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="What happened?"
        className="border-none bg-transparent px-0 shadow-none focus-visible:ring-0 focus-visible:border-transparent"
        onKeyDown={(e) => {
          if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault()
            void handleSubmit()
          }
        }}
      />

      {/* Footer: pickers + submit */}
      <div className="mt-2 flex items-center justify-between">
        <div className="flex items-center gap-1">
          {/* Emotion combobox */}
          <Combobox<string>
            value={emotionId || null}
            onValueChange={(value) => {
              if (value !== null) setEmotionId(value)
            }}
          >
            <PopoverTrigger
              render={
                <button
                  type="button"
                  className={cn(
                    "flex items-center gap-1 rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground",
                    emotionId && "text-foreground",
                  )}
                  aria-label={selectedEmotion ? `Emotion: ${selectedEmotion.name}` : "Pick emotion"}
                />
              }
            >
              <Heart className="size-4" aria-hidden />
              {selectedEmotion && (
                <span
                  className="rounded-full px-1.5 py-0.5 text-xs font-medium"
                  style={{ backgroundColor: `${selectedEmotion.color}20`, color: selectedEmotion.color }}
                >
                  {selectedEmotion.name}
                </span>
              )}
            </PopoverTrigger>
            <ComboboxContent align="start">
              <ComboboxInput placeholder="Search emotions…" />
              <ComboboxList>
                {EMOTIONS.map((emotion) => (
                  <ComboboxItem key={emotion.id} value={emotion.id}>
                    <span
                      className="size-3 rounded-full shrink-0"
                      style={{ backgroundColor: emotion.color }}
                      aria-hidden
                    />
                    {emotion.name}
                  </ComboboxItem>
                ))}
              </ComboboxList>
              <ComboboxEmpty>No emotions found</ComboboxEmpty>
            </ComboboxContent>
          </Combobox>

          {/* Domain popover */}
          <Popover>
            <PopoverTrigger
              className={cn(
                "rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground",
                domainIds.length > 0 && "text-foreground",
              )}
              aria-label={domainIds.length > 0 ? `${domainIds.length} domain(s) selected` : "Pick domains"}
            >
              <Layers className="size-4" aria-hidden />
            </PopoverTrigger>
            <PopoverContent align="start" className="min-w-[180px]">
              {DOMAINS.map((domain) => {
                const selected = domainIds.includes(domain.id)
                return (
                  <button
                    key={domain.id}
                    type="button"
                    onClick={() => toggleDomain(domain.id)}
                    className={cn(
                      "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm outline-none transition-colors hover:bg-muted",
                      selected && "font-medium",
                    )}
                    aria-pressed={selected}
                  >
                    <span className="size-4 shrink-0 flex items-center justify-center">
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

          {/* Souls combobox */}
          <Popover>
            <PopoverTrigger
              className={cn(
                "rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground",
                soulIds.length > 0 && "text-foreground",
              )}
              aria-label={soulIds.length > 0 ? `${soulIds.length} soul(s) selected` : "Pick souls"}
            >
              <Users className="size-4" aria-hidden />
            </PopoverTrigger>
            <PopoverContent align="start" className="min-w-[200px] p-2">
              <div className="flex items-center gap-2 border-b border-border pb-2 mb-1">
                <input
                  type="text"
                  value={soulQuery}
                  onChange={(e) => setSoulQuery(e.target.value)}
                  placeholder="Search souls…"
                  className="h-7 w-full min-w-0 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && canAddNewSoul) {
                      e.preventDefault()
                      void handleAddSoul()
                    }
                  }}
                />
              </div>
              <div className="max-h-[200px] overflow-y-auto">
                {filteredSouls.map((soul) => {
                  const selected = soulIds.includes(soul.id)
                  return (
                    <button
                      key={soul.id}
                      type="button"
                      onClick={() => toggleSoul(soul.id)}
                      className={cn(
                        "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm outline-none transition-colors hover:bg-muted",
                        selected && "font-medium",
                      )}
                    >
                      <span className="size-4 shrink-0 flex items-center justify-center">
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
                    className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-muted-foreground outline-none transition-colors hover:bg-muted hover:text-foreground"
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
            </PopoverContent>
          </Popover>
        </div>

        <div className="flex items-center gap-1">
          {/* Mode toggle */}
          <button
            type="button"
            onClick={() => setMode((m) => (m === "quick" ? "normal" : "quick"))}
            className="rounded-md px-2 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          >
            {mode === "quick" ? "Quick" : "Normal"}
          </button>

          {/* Submit */}
          <Button
            variant="default"
            size="icon"
            disabled={!name.trim()}
            onClick={() => void handleSubmit()}
            aria-label="Create pebble"
            className="size-8 rounded-full"
          >
            <ArrowUpCircle className="size-5" aria-hidden />
          </Button>
        </div>
      </div>

      {/* Date picker dialog (kept as Dialog — calendar is too large for popover) */}
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
    </section>
  )
}
