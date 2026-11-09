"use client"

import { useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { describeFlag, flaggedPaths, formChoices, reviewFlags, type Glyph, type GlyphPath } from "@/lib/design/glyph-forms"
import { ICON_ROWS, type IconRow } from "@/lib/design/icon-catalog"
import type { Verdict } from "@/lib/design/useReviewVerdicts"
import { cn } from "@/lib/utils"
import { GlyphFormView } from "./GlyphFormView"

const ROWS: ReadonlyMap<string, IconRow> = new Map(ICON_ROWS.map((row) => [row.name, row]))
const VERDICTS: readonly Verdict[] = ["ok", "fix", "drop-twin"]
const VERDICT_KEYS: Readonly<Record<string, Verdict>> = { o: "ok", f: "fix", d: "drop-twin" }

function roleLabel(p: GlyphPath): string {
  const role = p.role === "step" ? `step ${p.step ?? "?"}` : (p.role ?? "line")
  return p.hidden ? `${role}, hidden` : role
}

/** Body in ink, steps and the mark in two chart hues; a line without a role stays muted. */
function roleColour(p: GlyphPath): string {
  if (p.role === "step") return "text-chart-1"
  if (p.role === "mark") return "text-chart-2"
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
            className={cn(roleColour(p), hover !== null && hover !== i && "opacity-25")}
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
              <span aria-hidden className={cn("size-2.5 rounded-full bg-current", roleColour(p))} />
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
  /** What a fix needs, in the maintainer's words; asked for only under a fix verdict. */
  note: string
  onNote: (text: string) => void
  onPrev: () => void
  onNext: () => void
  onClose: () => void
}

/**
 * One glyph laid out for review: what the role pass flagged, every form the
 * generator works out next to the twins drawn by hand today, and the roles
 * path by path. ← → walk the list; O, F, D set the verdict.
 */
export function GlyphReviewDialog({ glyph, position, verdict, onVerdict, note, onNote, onPrev, onNext, onClose }: GlyphReviewDialogProps) {
  // Choosing fix from the keyboard or a button moves straight to the note, which mounts with that verdict.
  const focusNote = useRef(false)

  function choose(v: Verdict) {
    const next = verdict === v ? undefined : v
    onVerdict(next)
    focusNote.current = next === "fix"
  }

  const row = glyph ? ROWS.get(glyph.name) : undefined
  const flags = glyph ? reviewFlags(glyph.name) : []

  function onKeyDown(e: React.KeyboardEvent) {
    const typing = e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement
    if (typing || e.metaKey || e.ctrlKey || e.altKey) return
    if (e.key === "ArrowLeft") onPrev()
    else if (e.key === "ArrowRight") onNext()
    else if (VERDICT_KEYS[e.key]) choose(VERDICT_KEYS[e.key])
    else return
    e.preventDefault()
  }

  return (
    <Dialog
      open={glyph !== null}
      onOpenChange={(open, details) => {
        // Escape in the note leaves the note, for ← → and the verdict keys; a second Escape closes.
        const active = document.activeElement
        if (!open && details.reason === "escape-key" && active instanceof HTMLTextAreaElement) {
          details.cancel()
          active.blur()
          return
        }
        if (!open) onClose()
      }}
    >
      <DialogContent onKeyDown={onKeyDown} className="max-h-[90dvh] max-w-[calc(100%-2rem)] overflow-y-auto p-5 sm:max-w-3xl">
        {glyph && (
          <>
            <DialogHeader>
              <span className="text-xs text-muted-foreground tabular-nums">{position}</span>
              <DialogTitle className="font-mono text-lg">{glyph.name}</DialogTitle>
              <DialogDescription>{glyph.meaning}</DialogDescription>
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

            {row && row.twins.length > 0 && (
              <section className="flex flex-col gap-2">
                <h3 className="text-sm font-medium">Twins, drawn by hand today</h3>
                <div className="grid grid-cols-[repeat(auto-fill,minmax(6.5rem,1fr))] gap-2">
                  {row.Icon && (
                    <Cell label="base">
                      <row.Icon className="size-16" />
                    </Cell>
                  )}
                  {row.twins.map((twin) =>
                    twin.Icon ? (
                      <Cell key={twin.variant} label={twin.variant}>
                        <twin.Icon className="size-16" />
                      </Cell>
                    ) : null,
                  )}
                </div>
              </section>
            )}

            <section className="flex flex-col gap-2">
              <h3 className="text-sm font-medium">Roles, path by path</h3>
              <RolesMap key={glyph.name} glyph={glyph} />
            </section>

            {verdict === "fix" && (
              <label className="flex flex-col gap-1.5 text-sm font-medium">
                What the fix needs
                <textarea
                  ref={(el) => {
                    if (el && focusNote.current) {
                      el.focus()
                      focusNote.current = false
                    }
                  }}
                  value={note}
                  onChange={(e) => onNote(e.target.value)}
                  rows={3}
                  placeholder="e.g. tag the moon as mark; redraw lit-3 so both figures fill; drop the added dot"
                  className="resize-y rounded-xl border border-input bg-card px-3 py-2 text-sm font-normal outline-none placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring"
                />
                <span className="text-xs font-normal text-muted-foreground">Saved as you type. Esc leaves the note; ← → then walk the list.</span>
              </label>
            )}

            <footer className="-mx-5 -mb-5 flex flex-wrap items-center justify-between gap-2 border-t border-border bg-muted/50 px-5 py-3">
              <div className="flex gap-1">
                <Button size="sm" variant="ghost" onClick={onPrev} aria-label="Previous glyph">
                  ←
                </Button>
                <Button size="sm" variant="ghost" onClick={onNext} aria-label="Next glyph">
                  →
                </Button>
              </div>
              <div role="group" aria-label="Verdict" className="flex gap-1">
                {VERDICTS.map((v) => (
                  <Button
                    key={v}
                    size="sm"
                    variant={verdict === v ? "default" : "outline"}
                    aria-pressed={verdict === v}
                    onClick={() => choose(v)}
                  >
                    {v}
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
