"use client"

import { useCallback, useState } from "react"
import { useRouter } from "next/navigation"
import {
  Heart,
  Layers,
  Users,
  ArrowUpCircle,
  CalendarDays,
} from "lucide-react"
import { usePebbles } from "@/lib/data/usePebbles"
import { useDataProvider } from "@/lib/data/provider-context"
import { computeKarmaDelta } from "@/lib/data/karma"
import { EMOTIONS } from "@/lib/config/emotions"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogClose,
} from "@/components/ui/dialog"
import { EmotionPicker } from "@/components/record/EmotionPicker"
import { DomainPicker } from "@/components/record/DomainPicker"
import { SoulPicker } from "@/components/record/SoulPicker"
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
  const { provider } = useDataProvider()

  const [name, setName] = useState("")
  const [happenedAt, setHappenedAt] = useState(() => new Date().toISOString())
  const [intensity, setIntensity] = useState<Intensity>(1)
  const [valence, setValence] = useState<Valence>(0)
  const [emotionId, setEmotionId] = useState("")
  const [domainIds, setDomainIds] = useState<string[]>([])
  const [soulIds, setSoulIds] = useState<string[]>([])
  const [mode, setMode] = useState<EditorMode>("quick")

  const [dateOpen, setDateOpen] = useState(false)
  const [emotionOpen, setEmotionOpen] = useState(false)
  const [domainOpen, setDomainOpen] = useState(false)
  const [soulOpen, setSoulOpen] = useState(false)

  // Temp state for pickers (commit on save)
  const [tempEmotionId, setTempEmotionId] = useState("")
  const [tempDomainIds, setTempDomainIds] = useState<string[]>([])
  const [tempSoulIds, setTempSoulIds] = useState<string[]>([])
  const [tempDate, setTempDate] = useState(() => new Date())

  const selectedEmotion = EMOTIONS.find((e) => e.id === emotionId)

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
      <div className="mb-2 flex flex-wrap items-center gap-2">
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

        {INTENSITY_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            type="button"
            onClick={() => setIntensity(opt.value)}
            className={cn(
              "rounded-md px-2 py-1 text-xs font-medium transition-colors",
              intensity === opt.value
                ? "bg-foreground/10 text-foreground"
                : "text-muted-foreground hover:bg-muted hover:text-foreground",
            )}
          >
            {opt.label}
          </button>
        ))}

        {VALENCE_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            type="button"
            onClick={() => setValence(opt.value)}
            className={cn(
              "rounded-md px-2 py-1 text-xs font-medium transition-colors",
              valence === opt.value
                ? "bg-foreground/10 text-foreground"
                : "text-muted-foreground hover:bg-muted hover:text-foreground",
            )}
          >
            {opt.label}
          </button>
        ))}
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
          {/* Emotion picker */}
          <button
            type="button"
            onClick={() => {
              setTempEmotionId(emotionId)
              setEmotionOpen(true)
            }}
            className={cn(
              "flex items-center gap-1 rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground",
              emotionId && "text-foreground",
            )}
            aria-label={selectedEmotion ? `Emotion: ${selectedEmotion.name}` : "Pick emotion"}
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
          </button>

          {/* Domain picker */}
          <button
            type="button"
            onClick={() => {
              setTempDomainIds(domainIds)
              setDomainOpen(true)
            }}
            className={cn(
              "rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground",
              domainIds.length > 0 && "text-foreground",
            )}
            aria-label={domainIds.length > 0 ? `${domainIds.length} domain(s) selected` : "Pick domains"}
          >
            <Layers className="size-4" aria-hidden />
          </button>

          {/* Soul picker */}
          <button
            type="button"
            onClick={() => {
              setTempSoulIds(soulIds)
              setSoulOpen(true)
            }}
            className={cn(
              "rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground",
              soulIds.length > 0 && "text-foreground",
            )}
            aria-label={soulIds.length > 0 ? `${soulIds.length} soul(s) selected` : "Pick souls"}
          >
            <Users className="size-4" aria-hidden />
          </button>
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

      {/* Emotion picker dialog */}
      <Dialog
        open={emotionOpen}
        onOpenChange={(open) => {
          setEmotionOpen(open)
          if (open) setTempEmotionId(emotionId)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Emotion</DialogTitle>
          </DialogHeader>
          <EmotionPicker value={tempEmotionId} onChange={setTempEmotionId} />
          <DialogFooter>
            <DialogClose>Cancel</DialogClose>
            <Button
              onClick={() => {
                setEmotionId(tempEmotionId)
                setEmotionOpen(false)
              }}
            >
              Done
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Domain picker dialog */}
      <Dialog
        open={domainOpen}
        onOpenChange={(open) => {
          setDomainOpen(open)
          if (open) setTempDomainIds(domainIds)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Domains</DialogTitle>
          </DialogHeader>
          <DomainPicker value={tempDomainIds} onChange={setTempDomainIds} />
          <DialogFooter>
            <DialogClose>Cancel</DialogClose>
            <Button
              onClick={() => {
                setDomainIds(tempDomainIds)
                setDomainOpen(false)
              }}
            >
              Done
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Soul picker dialog */}
      <Dialog
        open={soulOpen}
        onOpenChange={(open) => {
          setSoulOpen(open)
          if (open) setTempSoulIds(soulIds)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Souls</DialogTitle>
          </DialogHeader>
          <SoulPicker value={tempSoulIds} onChange={setTempSoulIds} />
          <DialogFooter>
            <DialogClose>Cancel</DialogClose>
            <Button
              onClick={() => {
                setSoulIds(tempSoulIds)
                setSoulOpen(false)
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
