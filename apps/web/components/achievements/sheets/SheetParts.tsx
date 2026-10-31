"use client"

import { createContext, useContext, useId, type CSSProperties, type ReactNode } from "react"
import { EmotionRosace } from "@/components/achievements/figures/EmotionRosace"
import { FirstFiveMark } from "@/components/achievements/figures/FirstFiveMark"
import { FlatSeal } from "@/components/achievements/figures/FlatSeal"
import { Ghost } from "@/components/achievements/figures/Ghost"
import { HueGroup } from "@/components/achievements/figures/HueGroup"
import { SheetClose, SheetTitle } from "@/components/ui/sheet"
import { SkylineStage } from "@/components/achievements/figures/SkylineStage"
import { polar } from "@/lib/achievements/geometry"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import { DOMAIN_SEAL, SIX_FIRST_SEALS, pebbleSeal } from "@/lib/achievements/seals"
import type { RegisterRow, RegisterSection, RowMark, RowOpens, YearCell, YearRow } from "@/lib/achievements/sheet-rows"
import { seatAngle, TWENTY_FIVE_SEATS } from "@/lib/achievements/wheel"
import { discMonth } from "@/lib/achievements/year-disc"
import { stonePaletteVars } from "@/lib/config/stone-tones"
import { useAchievementsWords } from "@/lib/i18n/useAchievementsWords"
import type { AchievementsEmotions } from "@/lib/types"
import { cn } from "@/lib/utils"

// The sheets' parts (#1308, the boards' pick-3 register), iOS's `SheetParts`:
// a band (with the set's ready mark while it is offered), the figure large, a
// lead, then sections of register rows. A row that opens something is a
// button of at least 44 px, read as its assembled label.

/**
 * Whether the band is set in a sheet: there it is the dialog's own title, so
 * the sheet is named by it; elsewhere (a preview, a test) a plain heading.
 */
export const InSheetContext = createContext(false)

/** A sheet's frame: the band, the figure, an optional lead, then its sections, on the stock. */
export function SheetBody({ band, hint, lead, ready, figure, children }: { band: string; hint?: string | null; lead?: string | null; ready?: ReactNode; figure: ReactNode; children: ReactNode }) {
  const inSheet = useContext(InSheetContext)
  const words = useAchievementsWords()
  const bandClass = "font-spine-sc text-[11.5px] font-semibold tracking-[.22em] text-spine-ink"
  return (
    <div className="px-1 pb-8 font-spine text-spine-ink">
      <header className="flex min-h-11 flex-wrap items-baseline justify-between gap-x-2 gap-y-1 border-b border-spine-card-rule pt-3.5 pb-2">
        {inSheet ? <SheetTitle className={bandClass}>{band}</SheetTitle> : <h2 className={bandClass}>{band}</h2>}
        {ready ?? (hint ? <em className="text-sm font-[380] text-spine-ink-2">{hint}</em> : null)}
        {inSheet ? (
          // For a touch screen reader with no Escape and no backdrop to tap: hidden until focused.
          <SheetClose className="sr-only focus:not-sr-only focus:order-first">{words.sheet.text("close")}</SheetClose>
        ) : null}
      </header>
      <div className="flex justify-center pt-4">{figure}</div>
      {lead ? <p className="pt-3.5 text-[16.5px] leading-[1.35] font-[380] italic">{lead}</p> : null}
      {children}
    </div>
  )
}

/** A section's head (the boards' `.lh`): the small-caps title, its italic note, a rule. */
export function SectionHead({ title, note, tint }: { title: string; note?: string; tint?: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-2 border-b border-spine-card-rule pb-1.5">
      <h3 className="font-spine-sc text-[11.5px] font-semibold tracking-[.22em]">{tint ?? title}</h3>
      {note ? <em className="text-[13.5px] font-[380] text-spine-ink-2">{note}</em> : null}
    </div>
  )
}

/** A family's name in its name ink (the boards' `tx`), or ink for one this build cannot resolve. */
function FamilyTitle({ title, family, palettes }: { title: string; family: string | null; palettes: AchievementPalettes }) {
  const palette = palettes.family(family)
  if (!palette) return <>{title}</>
  return (
    <span
      className={family === "shame" ? "text-(--sh) dark:text-(--s)" : "text-(--p) dark:text-(--s)"}
      style={stonePaletteVars(palette) as CSSProperties}
    >
      {title}
    </span>
  )
}

type SectionProps = {
  section: RegisterSection
  palettes: AchievementPalettes
  emotions?: AchievementsEmotions | null
  compact?: boolean
  open: (opens: RowOpens) => void
}

/** A section: its head, its rows, its "not yet" names, its tail or its quiet line. */
export function SheetSection({ section, palettes, emotions = null, compact = false, open }: SectionProps) {
  return (
    <section className="pt-7">
      <SectionHead
        title={section.title}
        note={section.note}
        tint={section.family !== undefined ? <FamilyTitle title={section.title} family={section.family} palettes={palettes} /> : undefined}
      />
      {section.rows.map((row) => (
        <RegisterRowView key={row.id} row={row} palettes={palettes} emotions={emotions} compact={compact} open={open} />
      ))}
      {section.notYet.length > 0 ? <GhostNames names={section.notYet} /> : null}
      {section.trailing ? (
        <p className="flex min-h-11 items-center border-b border-spine-rule pl-11 text-[14.5px] font-[380] text-spine-ink-2 italic">{section.trailing}</p>
      ) : null}
      {section.quiet ? <p className="pt-2.5 text-[14.5px] font-[380] text-spine-ink-2 italic">{section.quiet}</p> : null}
      {section.closing ? <p className="pt-2.5 text-[14.5px] font-[380] text-spine-ink-2 italic">{section.closing}</p> : null}
    </section>
  )
}

/** A register row (the boards' `.r`): 50 px (44 in the glyph stacks), a button when it opens a pebble or a sheet. */
export function RegisterRowView({ row, palettes, emotions, compact, open }: { row: RegisterRow; palettes: AchievementPalettes; emotions: AchievementsEmotions | null; compact: boolean; open: (opens: RowOpens) => void }) {
  const words = useAchievementsWords()
  const hintId = useId()
  const content = (
    <span aria-hidden className={cn("flex w-full items-center gap-2.5 border-b border-spine-rule py-1.5 text-left", compact ? "min-h-11" : "min-h-[50px]")}>
      {row.number ? <span className="w-[42px] shrink-0 truncate text-[12.5px] tracking-[.03em] text-spine-ink-2 tabular-nums">{row.number}</span> : null}
      {row.mark.kind !== "none" ? (
        <span className={cn("flex shrink-0 justify-center", row.number ? "w-[30px]" : "w-[34px]")}>
          <RowMarkView mark={row.mark} palettes={palettes} emotions={emotions} />
        </span>
      ) : null}
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <RowName row={row} compact={compact} />
        {row.sub ? <span className="text-[14.5px] font-[420] text-spine-ink-2">{row.sub}</span> : null}
        {row.next ? <span className="text-[14.5px] italic">{row.next}</span> : null}
      </span>
      <span className="flex shrink-0 items-center gap-1.5">
        {row.isNew ? <span className="size-[5px] rounded-full bg-spine-ink" /> : null}
        {row.date ? <span className="text-[12.5px] whitespace-nowrap text-spine-ink-2 tabular-nums">{row.date}</span> : null}
      </span>
      {row.opens?.kind === "sheet" ? (
        <svg viewBox="0 0 11 19" className="h-[15px] w-[9px] shrink-0 fill-none stroke-spine-ink-2 stroke-[1.6]">
          <path d="M2 1.5 9.5 9.5 2 17.5" />
        </svg>
      ) : null}
    </span>
  )
  if (row.opens) {
    const opens = row.opens
    return (
      <button
        type="button"
        onClick={() => open(opens)}
        aria-label={row.label}
        aria-describedby={hintId}
        className="block w-full cursor-pointer outline-offset-1 focus-visible:outline-2 focus-visible:outline-spine-ink"
      >
        {content}
        <span id={hintId} className="sr-only">
          {words.sheet.opensHint(opens.kind)}
        </span>
      </button>
    )
  }
  return (
    <div>
      {content}
      <p className="sr-only">{row.label}</p>
    </div>
  )
}

function RowName({ row, compact }: { row: RegisterRow; compact: boolean }) {
  if (row.style === "name") return <span className="font-spine-sc text-[13px] font-semibold tracking-[.1em]">{row.name}</span>
  const size = compact ? "text-[17px]" : "text-[21px]"
  if (row.style === "number") return <span className={cn("font-spine-office font-light tabular-nums", size)}>{row.name}</span>
  if (row.style === "next") return <span className={cn("font-spine-office font-light text-spine-ink-2 tabular-nums", size)}>{row.name}</span>
  return <span className={cn("font-spine-office font-extralight text-spine-ink-2 tabular-nums", size)}>{row.name}</span>
}

/** A row's mark in its 28 px box: the figures' own pieces, larger. */
function RowMarkView({ mark, palettes, emotions }: { mark: RowMark; palettes: AchievementPalettes; emotions: AchievementsEmotions | null }) {
  if (mark.kind === "rosace") return emotions ? <EmotionRosace emotions={emotions} palettes={palettes} size={28} labelled={false} centred={false} marked={false} /> : null
  if (mark.kind === "stage") return <SkylineStage stage={mark.index} fill={mark.fill} done={mark.done} />
  return (
    <svg aria-hidden focusable="false" viewBox="-14 -14 28 28" width={28} height={28} className="block overflow-visible">
      {mark.kind === "firstFive" ? <FirstFiveMark task={mark.task} x={0} y={0} scale={1.7} /> : null}
      {mark.kind === "sixFirst" ? (
        <FlatSeal seal={SIX_FIRST_SEALS[mark.stone]} x={0} y={0} width={SIX_FIRST_SEALS[mark.stone].width * 1.55} />
      ) : null}
      {mark.kind === "twentyFive" ? (
        <>
          <circle r={11} fill="none" stroke="var(--spine-ink-2)" strokeWidth={1} />
          {Array.from({ length: TWENTY_FIVE_SEATS }, (_, i) => {
            const [x, y] = polar(11, seatAngle(i, TWENTY_FIVE_SEATS))
            return <circle key={i} cx={x} cy={y} r={1.1} fill="var(--spine-ink)" />
          })}
        </>
      ) : null}
      {mark.kind === "pebble" ? (
        <FlatSeal seal={pebbleSeal(mark)} x={0} y={0} width={9 + mark.intensity * 2.4} palette={palettes.family(mark.family)} />
      ) : null}
      {mark.kind === "domain" ? <FlatSeal seal={DOMAIN_SEAL} x={0} y={0} width={17} /> : null}
      {mark.kind === "ghost" ? <Ghost x={0} y={0} r={3.6} /> : null}
    </svg>
  )
}

/** "Not yet" as dotted names (the boards' `.rest`), wrapping; read as one: "Not yet: …". */
export function GhostNames({ names }: { names: string[] }) {
  const words = useAchievementsWords()
  return (
    <div className="border-b border-spine-rule py-2.5">
      <p className="sr-only">{words.sheet.notYet(names)}</p>
      <ul aria-hidden className="flex flex-wrap gap-x-3.5 gap-y-1.5">
        {names.map((name, i) => (
          <li key={i} className="flex items-center gap-1.5 text-[15px] text-spine-ink-2">
            <svg viewBox="-5 -5 10 10" width={10} height={10} className="block overflow-visible">
              <Ghost x={0} y={0} r={3.6} />
            </svg>
            {name}
          </li>
        ))}
      </ul>
    </div>
  )
}

/** The year table's column heads (the boards' `.yth`). */
export function YearColumnsHead() {
  const words = useAchievementsWords()
  return (
    <div aria-hidden className="flex items-end justify-end gap-1.5 border-b border-spine-rule pt-2 pb-1">
      {(["moments", "pebbles", "weeks"] as const).map((key) => (
        <span key={key} className="w-[58px] truncate text-center font-spine-sc text-[10.5px] font-semibold tracking-[.14em] text-spine-ink-2">
          {words.sheet.text(`column.${key}`)}
        </span>
      ))}
    </div>
  )
}

/** A month of the year table (the boards' `.r.yt`): its counter, its name and three gauges, read as one line; not a button. */
export function YearRowView({ row, palettes }: { row: YearRow; palettes: AchievementPalettes }) {
  const m = discMonth(row.month)
  const palette = m.tone.muted ? null : palettes.family(m.tone.family)
  return (
    <div className="border-b border-spine-rule">
      <p className="sr-only">{row.label}</p>
      <div
        aria-hidden
        className={cn("flex min-h-12 items-center gap-1.5", palette && "spine-spot")}
        style={palette ? (stonePaletteVars(palette) as CSSProperties) : undefined}
      >
        <svg viewBox="-15 -14 30 28" width={30} height={28} className="block shrink-0 overflow-visible">
          <HueGroup palette={palette}>
            {m.counter.ring ? <circle r={14 * 0.78} fill="none" stroke="var(--spine-ink)" strokeWidth={0.8} /> : null}
            <FlatSeal
              seal={{ size: "medium", outline: "neutral", print: m.counter.print }}
              x={0}
              y={0}
              width={14}
              palette={m.counter.print === "solid" ? palette : null}
              ink={m.counter.print === "solid" ? "ink" : "ink2"}
              dashed={m.counter.dashed}
            />
          </HueGroup>
        </svg>
        <span className={cn("flex min-w-0 flex-1 items-center gap-1.5", m.tone.muted && "opacity-55")}>
          <span className="min-w-0 flex-1 truncate font-spine-sc text-[13px] font-semibold tracking-[.1em]">{row.name}</span>
          <Cell cell={row.moments} hued={false} />
          <Cell cell={row.pebbles} hued={palette !== null} />
          <Cell cell={row.weeks} hued={false} />
        </span>
      </div>
    </div>
  )
}

/** One gauge (the boards' `cell`): the served number, done-styled when the server says so, over a hairline and its fill. */
function Cell({ cell, hued }: { cell: YearCell; hued: boolean }) {
  const tone = cell.hued && hued ? "var(--spine-spot)" : "var(--spine-ink)"
  return (
    <span className="flex w-[58px] shrink-0 flex-col items-center gap-1">
      <span className="text-sm whitespace-nowrap tabular-nums">
        <span className={cn(cell.done ? "font-[560]" : "text-spine-ink-2")} style={cell.done ? { color: tone } : undefined}>
          {cell.value}
        </span>
        {cell.target ? <span className="text-spine-ink-2">{cell.target}</span> : null}
      </span>
      <span className="relative block h-1 w-[52px]">
        <span className="absolute inset-x-0 top-[1.5px] h-px bg-spine-ink-2 opacity-55" />
        {cell.fill !== null && cell.fill > 0 ? (
          <span className="absolute top-[0.75px] left-0 h-[2.5px] rounded-full" style={{ width: 52 * Math.min(cell.fill, 1), background: tone }} />
        ) : null}
      </span>
    </span>
  )
}
