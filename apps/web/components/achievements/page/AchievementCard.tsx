import type { ReactNode } from "react"
import type { LadderBar } from "@/lib/achievements/ladders"
import { MarginLadder } from "@/components/achievements/figures/MarginLadder"

type AchievementCardProps = {
  /** The margin's vertical title, and the band's unless `heading` names it. */
  title: string
  heading?: string
  /** The band's italic note at its far end; a ready mark takes its place. */
  hint?: string | null
  /** The margin's ladder, one bar per step. */
  ladder?: LadderBar[]
  /** One long bar instead (Domains). */
  longBar?: LadderBar
  /** The card's spoken label, assembled from the payload (spec §5). */
  label: string
  /** Opens the card's sheet (Part 6); without it the card is not a button. */
  onOpen?: () => void
  /** The ready mark, a separate control in the band's corner. */
  ready?: ReactNode
  children: ReactNode
}

/**
 * The achievements card (#1308, the boards' pick-6 `gal`): the Spine's card
 * ground, a 34 px margin with the title set bottom to top over its ladder,
 * then a band (small-caps title, italic hint, a rule) over the figure. Its
 * visible content is drawn for the eye; a screen reader hears the label
 * assembled from the payload instead. A ready mark sits above, never inside,
 * the card's own button. iOS `AchievementCardShell`.
 */
export function AchievementCard({ title, heading, hint, ladder = [], longBar, label, onOpen, ready, children }: AchievementCardProps) {
  return (
    <article className="relative grid grid-cols-[34px_minmax(0,1fr)] rounded-[11px] bg-spine-stock text-spine-ink shadow-(--spine-card-shadow)">
      <div aria-hidden className="flex flex-col items-center justify-between gap-3.5 border-r border-spine-card-rule pt-4 pb-3.5">
        <span className="rotate-180 font-spine-sc text-[11px] font-semibold tracking-[.26em] whitespace-nowrap text-spine-ink-2 [writing-mode:vertical-rl]">
          {title}
        </span>
        {longBar ? <MarginLadder bars={[longBar]} tall={120} /> : <MarginLadder bars={ladder} />}
      </div>
      <div aria-hidden className="min-w-0 px-3.5 pb-4">
        <header className="flex min-h-11 flex-wrap items-baseline justify-between gap-x-2 gap-y-1 border-b border-spine-card-rule pt-3.5 pb-2">
          <span className="font-spine-sc text-[11.5px] font-semibold tracking-[.22em]">{heading ?? title}</span>
          {hint && !ready ? <em className="text-sm font-[380] text-spine-ink-2">{hint}</em> : null}
        </header>
        {children}
      </div>
      {onOpen ? (
        <button
          type="button"
          onClick={onOpen}
          aria-label={label}
          className="absolute inset-0 cursor-pointer rounded-[11px] outline-offset-2 focus-visible:outline-2 focus-visible:outline-spine-ink"
        />
      ) : (
        <p className="sr-only">{label}</p>
      )}
      {ready ? <div className="absolute top-1.5 right-1.5 z-10">{ready}</div> : null}
    </article>
  )
}
