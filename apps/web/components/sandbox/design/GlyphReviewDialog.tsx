"use client"

import { useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { describeFlag, flaggedPaths, firstReview, formChoices, reviewFlags, type Glyph, type GlyphPath } from "@/lib/design/glyph-forms"
import { VERDICTS, VERDICT_KEYS, verdictLabel, type Verdict } from "@/lib/design/useReviewVerdicts"
import { cn } from "@/lib/utils"
import { GlyphFormView } from "./GlyphFormView"

/** The key that sets a verdict, as the footer prints it. */
function keyCap(v: Verdict): string {
  const key = Object.keys(VERDICT_KEYS).find((k) => VERDICT_KEYS[k] === v) ?? ""
  return key === " " ? "Space" : key.toUpperCase()
}

/** Parts take the three chart hues after the step and mark ones, in source order. */
const PART_COLOURS = ["text-chart-3", "text-chart-4", "text-chart-5"] as const

function roleLabel(p: GlyphPath): string {
  const role = p.role === "step" ? `step ${p.step ?? "?"}` : p.role === "body" && p.part ? `body · ${p.part}` : (p.role ?? "line")
  return p.hidden ? `${role}, hidden` : role
}

/** Steps and the mark in two chart hues, each part in its own, a whole body in ink; a line without a role stays muted. */
function roleColour(p: GlyphPath, parts: readonly string[]): string {
  if (p.role === "step") return "text-chart-1"
  if (p.role === "mark") return "text-chart-2"
  if (p.role === "body" && p.part !== undefined) return PART_COLOURS[Math.max(0, parts.indexOf(p.part)) % PART_COLOURS.length] ?? "text-foreground"
  if (p.role === "body") return "text-foreground"
  return "text-muted-foreground"
}

function Cell({ label, note, children }: { label: string; note?: string; children: React.ReactNode }) {
  return (
    <figure className="flex flex-col items-center gap-2 rounded-2xl bg-card px-2 pt-4 pb-2">
      {children}
      <figcaption className="text-center text-xs text-muted-foreground">
        <code>{label}</code>
        {note && <span className="block text-[10px] text-primary">{note}</span>}
      </figcaption>
    </figure>
  )
}

/**
 * Every path stroked in its role's colour, hidden ones dashed; hovering a
 * legend entry (or a flagged path, from the start) thickens it.
 */
function RolesMap({ glyph }: { glyph: Glyph }) {
  const flagged = flaggedPaths(glyph.name)
  const [hover, setHover] = useState<number | null>(null)
  const strong = (i: number) => (hover === null ? flagged.has(i) : hover === i)
  return (
    <div className="flex flex-wrap items-start gap-4">
      <svg viewBox="0 0 24 24" fill="none" strokeLinecap="round" strokeLinejoin="round" aria-hidden className="size-40 shrink-0 rounded-2xl bg-card p-2">
        {glyph.paths.map((p, i) => (
          <path
            key={i}
            d={p.d}
            stroke="currentColor"
            strokeWidth={strong(i) ? 2.6 : 1.75}
            strokeDasharray={p.hidden ? "1.2 1.6" : undefined}
            className={cn(roleColour(p, glyph.parts), hover !== null && hover !== i && "opacity-25")}
          />
        ))}
      </svg>
      <ol className="flex min-w-48 flex-1 flex-col gap-0.5 text-xs">
        {glyph.paths.map((p, i) => (
          <li key={i}>
            <button
              type="button"
              onMouseEnter={() => setHover(i)}
              onMouseLeave={() => setHover(null)}
              onFocus={() => setHover(i)}
              onBlur={() => setHover(null)}
              className="flex w-full items-center gap-2 rounded-md px-2 py-1 text-left outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring"
            >
              <span aria-hidden className={cn("size-2.5 rounded-full bg-current", roleColour(p, glyph.parts))} />
              <span className="tabular-nums text-muted-foreground">{i + 1}</span>
              <span>{roleLabel(p)}</span>
              {p.fill !== undefined && <span className="text-muted-foreground">carved</span>}
              {flagged.has(i) && <span className="ml-auto font-medium text-primary">flagged</span>}
            </button>
          </li>
        ))}
      </ol>
    </div>
  )
}

type GlyphReviewDialogProps = {
  glyph: Glyph | null
  position: string
  verdict?: Verdict
  onVerdict: (verdict: Verdict | undefined) => void
  /** What a fix needs, in the maintainer's words; asked for under a fix verdict. */
  note: string
  onNote: (text: string) => void
  onPrev: () => void
  onNext: () => void
  onClose: () => void
}

/**
 * One glyph laid out for review: what the role pass flagged, every form the
 * generator works out (the forms grid lists every fill with and without the
 * mark, every part combination and the dotted drawing), drawn as the components
 * draw them, and the roles path by path. One key judges the glyph and moves
 * on (Space ok, P, S, M, D, I; F asks for a comment, Enter saves it and moves
 * on); ← → walk the list.
 */
export function GlyphReviewDialog({ glyph, position, verdict, onVerdict, note, onNote, onPrev, onNext, onClose }: GlyphReviewDialogProps) {
  const contentRef = useRef<HTMLDivElement>(null)
  // Choosing fix focuses the comment, which mounts with that verdict.
  const focusNote = useRef(false)
  const noteField = useRef<HTMLInputElement | null>(null)
  // The glyph whose blank comment was refused on Enter.
  const [hintFor, setHintFor] = useState<string | null>(null)

  /** After moving on, the dialog itself holds focus so the next key lands. */
  function focusContent() {
    contentRef.current?.focus()
  }

  function choose(v: Verdict) {
    onVerdict(v)
    if (v === "fix") {
      if (noteField.current) noteField.current.focus()
      else focusNote.current = true
      return
    }
    onNext()
    focusContent()
  }

  function walk(by: "prev" | "next") {
    if (by === "prev") onPrev()
    else onNext()
    focusContent()
  }

  const flags = glyph ? reviewFlags(glyph.name) : []
  const first = glyph ? firstReview(glyph.name) : null

  function onKeyDown(e: React.KeyboardEvent) {
    const typing = e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement
    if (typing || e.metaKey || e.ctrlKey || e.altKey) return
    const key = e.key.toLowerCase()
    const picked = VERDICT_KEYS[key]
    if (key === "arrowleft") walk("prev")
    else if (key === "arrowright") walk("next")
    else if (picked) {
      // A held key repeats; judge once.
      if (!e.repeat) choose(picked)
    } else return
    e.preventDefault()
  }

  // Firefox clicks a focused button when Space is released; the keydown already judged the glyph.
  function onKeyUp(e: React.KeyboardEvent) {
    if (e.key === " " && !(e.target instanceof HTMLInputElement) && !e.metaKey && !e.ctrlKey && !e.altKey) e.preventDefault()
  }

  function onNoteKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key !== "Enter" || e.shiftKey || e.nativeEvent.isComposing) return
    e.preventDefault()
    if (note.trim() === "") {
      if (glyph) setHintFor(glyph.name)
      return
    }
    onNext()
    focusContent()
  }

  const showNote = verdict === "fix" || note !== ""
  const hint = glyph !== null && hintFor === glyph.name && note.trim() === ""

  return (
    <Dialog
      open={glyph !== null}
      onOpenChange={(open, details) => {
        // Escape in the comment leaves the comment, for ← → and the verdict keys; a second Escape closes.
        const active = document.activeElement
        if (!open && details.reason === "escape-key" && active instanceof HTMLInputElement) {
          details.cancel()
          active.blur()
          focusContent()
          return
        }
        if (!open) onClose()
      }}
    >
      <DialogContent
        ref={contentRef}
        tabIndex={-1}
        onKeyDown={onKeyDown}
        onKeyUp={onKeyUp}
        className="flex max-h-[90dvh] max-w-[calc(100%-2rem)] flex-col gap-0 overflow-hidden p-0 sm:max-w-3xl"
      >
        {glyph && (
          <>
            <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto p-5">
              <DialogHeader>
                <span className="text-xs text-muted-foreground tabular-nums">{position}</span>
                <DialogTitle className="font-mono text-lg">{glyph.name}</DialogTitle>
                <DialogDescription>{glyph.meaning}</DialogDescription>
                <p className="text-xs text-muted-foreground">
                  Space ok · P progress · S solid · M mark · D drop twin · I incorrect · F fix · ← → walk
                </p>
              </DialogHeader>

              {flags.length > 0 && (
                <ul className="flex flex-col gap-1 rounded-2xl bg-primary/10 px-4 py-3 text-sm">
                  {flags.map((flag, i) => (
                    <li key={i}>
                      <span className="font-medium">{flag.kind}</span> · {describeFlag(flag)}
                    </li>
                  ))}
                </ul>
              )}

              {first?.verdict && (
                <p className="rounded-2xl bg-muted px-4 py-3 text-sm">
                  <span className="font-medium">Round 1 · {first.verdict}</span>
                  {first.note && <> · {first.note}</>}
                </p>
              )}

              <section className="flex flex-col gap-2">
                <h3 className="text-sm font-medium">Forms, worked out</h3>
                <div className="grid grid-cols-[repeat(auto-fill,minmax(6.5rem,1fr))] gap-2">
                  {formChoices(glyph).map((c) => (
                    <Cell key={c.key} label={c.label} note={c.override ? "override" : undefined}>
                      <GlyphFormView glyph={glyph} form={c.form} className="size-16" />
                    </Cell>
                  ))}
                </div>
              </section>

              <section className="flex flex-col gap-2">
                <h3 className="text-sm font-medium">Roles, path by path</h3>
                <RolesMap key={glyph.name} glyph={glyph} />
              </section>

              {showNote && (
                <label className="flex flex-col gap-1.5 text-sm font-medium">
                  What the fix needs
                  <input
                    type="text"
                    ref={(el) => {
                      noteField.current = el
                      if (el && focusNote.current) {
                        el.focus()
                        focusNote.current = false
                      }
                    }}
                    value={note}
                    onChange={(e) => {
                      setHintFor(null)
                      onNote(e.target.value)
                    }}
                    onKeyDown={onNoteKeyDown}
                    placeholder="What the fix needs — Enter saves and moves on"
                    aria-describedby="glyph-note-help"
                    className="rounded-xl border border-input bg-card px-3 py-2 text-sm font-normal outline-none placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring"
                  />
                  <span id="glyph-note-help" className={cn("text-xs font-normal", hint ? "text-destructive" : "text-muted-foreground")}>
                    {hint ? "A fix needs a comment" : "Saved as you type. Esc leaves the comment; ← → then walk the list."}
                  </span>
                </label>
              )}
            </div>

            <footer className="sticky bottom-0 flex shrink-0 flex-wrap items-center gap-2 border-t border-border bg-background px-5 py-3">
              <div className="flex gap-1">
                <Button size="sm" variant="ghost" onClick={() => walk("prev")} aria-label="Previous glyph">
                  ←
                </Button>
                <Button size="sm" variant="ghost" onClick={() => walk("next")} aria-label="Next glyph">
                  →
                </Button>
              </div>
              <div role="group" aria-label="Verdict" className="flex flex-wrap gap-1">
                {VERDICTS.map((v) => (
                  <Button
                    key={v}
                    size="sm"
                    variant={verdict === v ? "default" : "outline"}
                    aria-pressed={verdict === v}
                    aria-label={`${keyCap(v)}: ${verdictLabel(v)}`}
                    onClick={() => choose(v)}
                  >
                    <kbd aria-hidden className="rounded border border-current/30 px-1 font-mono text-[10px] leading-4 opacity-80">
                      {keyCap(v)}
                    </kbd>
                    {verdictLabel(v)}
                  </Button>
                ))}
              </div>
            </footer>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
