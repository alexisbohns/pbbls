"use client"

import type { CSSProperties } from "react"
import { useLocale, useTranslations } from "next-intl"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import { facetTone, facets, rosaceGeometry } from "@/lib/achievements/rosace"
import { familyRuns } from "@/lib/achievements/runs"
import { stonePaletteVars, type StonePalette } from "@/lib/config/stone-tones"
import type { AchievementsEmotions } from "@/lib/types"
import { useFamilyName } from "@/lib/i18n/useFamilyName"
import { cn } from "@/lib/utils"
import { HueGroup } from "./HueGroup"

type EmotionRosaceProps = {
  emotions: AchievementsEmotions
  palettes: AchievementPalettes
  /** The board's size: the rosace takes the width it is given, up to this, square. */
  size?: number
  /** The families' names round it (the card and the sheet), not on a row's small mark. */
  labelled?: boolean
  /** The count and "of 38" at its heart. */
  centred?: boolean
  /** The family ticks and the new dots. */
  marked?: boolean
}

/** A lit facet's two tones, set on it so `dark:` picks Night's: one render serves both themes. */
function tones(palette: StonePalette, place: number, soft: boolean): CSSProperties {
  const tone = facetTone(place, soft)
  return { ...stonePaletteVars(palette), "--tone-paper": tone.paper, "--tone-night": tone.night } as CSSProperties
}

/**
 * The emotions rosace (#1308, the boards' `gem`): a kite per served seat, a met
 * seat filled at its place in its family's ramp, the star between two met
 * seats of one family softened toward the stock, a new seat's dot outside it.
 * Every "not yet" is outlined in secondary ink; a family this build cannot
 * resolve lights, and is named, in ink. The count and "of 38" are the
 * payload's. Its text scales with it, never past the board's size.
 * Decorative: its card's label carries the count. iOS `EmotionRosace`.
 */
export function EmotionRosace({ emotions, palettes, size = 270, labelled = true, centred = true, marked = true }: EmotionRosaceProps) {
  const t = useTranslations("achievements.rosace")
  const name = useFamilyName()
  const locale = useLocale()
  const seats = emotions.seats
  const g = rosaceGeometry(size, seats.length, labelled)
  const runs = familyRuns(seats)
  const lit = "fill-(--tone-paper) dark:fill-(--tone-night)"
  return (
    <svg aria-hidden focusable="false" viewBox={`0 0 ${size} ${size}`} className="block h-auto w-full overflow-visible" style={{ maxWidth: size }}>
      <path d={g.silhouette} fill="var(--spine-stock)" stroke="var(--spine-ink-2)" strokeWidth={1} strokeLinejoin="round" />
      <path d={g.outline} fill="none" stroke="var(--spine-ink-2)" strokeWidth={1} strokeLinejoin="round" />
      {facets(seats).map((f) => {
        if (!f.seat.met) return null
        const palette = palettes.family(f.family)
        return (
          <g key={f.k}>
            <path
              d={g.kite(f.k)}
              className={palette ? lit : "fill-spine-ink"}
              style={palette ? tones(palette, f.place, false) : undefined}
              stroke="var(--spine-stock)"
              strokeWidth={0.9}
            />
            {f.bridged && (
              <path
                d={g.star(f.k)}
                className={palette ? lit : "fill-spine-ink"}
                style={palette ? tones(palette, f.nextPlace, true) : undefined}
                stroke="var(--spine-stock)"
                strokeWidth={0.9}
              />
            )}
          </g>
        )
      })}
      {marked &&
        runs.map((run) => (
          <line key={`tick-${run.start}`} {...g.tick(run.start)} stroke="var(--spine-ink-2)" strokeWidth={1} strokeLinecap="round" />
        ))}
      {marked &&
        seats.map((seat, k) => {
          if (!seat.isNew) return null
          const [x, y] = g.fresh(k)
          return <circle key={`new-${k}`} cx={x} cy={y} r={2.6} fill="var(--spine-ink)" />
        })}
      {labelled &&
        runs.map((run) => {
          // A run the server sent without a family has no name to show.
          if (run.family === null) return null
          const palette = palettes.family(run.family)
          const [x, y] = g.label(run.start, run.length)
          return (
            <HueGroup key={`name-${run.start}`} palette={palette}>
              <text
                x={x}
                y={y}
                textAnchor="middle"
                dominantBaseline="central"
                className={cn(
                  "font-spine-sc text-[11px] font-semibold tracking-[.14em] uppercase",
                  // The boards' `tx`: Night's secondary; Paper's primary, shame's shaded (its primary is too pale).
                  !palette ? "fill-spine-ink" : run.family === "shame" ? "fill-(--sh) dark:fill-(--s)" : "fill-(--p) dark:fill-(--s)",
                )}
              >
                {name(run.family)}
              </text>
            </HueGroup>
          )
        })}
      {centred && (
        <>
          <text x={g.centre} y={g.centre - 3} textAnchor="middle" className="fill-spine-ink font-spine-office text-[28px] font-light">
            {new Intl.NumberFormat(locale).format(emotions.count)}
          </text>
          <text
            x={g.centre}
            y={g.centre + 15}
            textAnchor="middle"
            className="fill-spine-ink-2 font-spine-sc text-[11px] font-semibold tracking-[.2em] uppercase"
          >
            {t("of", { of: new Intl.NumberFormat(locale).format(emotions.of) })}
          </text>
        </>
      )}
    </svg>
  )
}
