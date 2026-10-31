"use client"

import { CalendarDate, Camera, KarmaDot, PebbleDottedGlyph, PebbleFlat, PebbleRound, PlusCircle, Slider, StonesAlignedBottom } from "@pbbls/icons"
import { M3Island } from "@/components/layout/M3Island"
import { Pentagon } from "@/components/stones/Pentagon"
import { SplitStone } from "@/components/stones/SplitStone"
import { Stone } from "@/components/stones/Stone"
import type { StoneFill } from "@/components/stones/stone-fill"

const PRIMARY_CONTAINER: StoneFill = { fill: "fill-m3-primary-container", bg: "bg-m3-primary-container" }
const SURFACE_CONTAINER: StoneFill = { fill: "fill-m3-surface-container", bg: "bg-m3-surface-container" }
const SURFACE_CONTAINER_HIGH: StoneFill = { fill: "fill-m3-surface-container-high", bg: "bg-m3-surface-container-high" }
const LIT: StoneFill = { fill: "fill-m3-primary", bg: "bg-m3-primary" }
const UNLIT: StoneFill = { fill: "fill-m3-surface-container-highest", bg: "bg-m3-surface-container-highest" }

const ACTIONS = [
  [Camera, "Camera"],
  [PebbleFlat, "Quick moment"],
  [PlusCircle, "New pebble"],
] as const

const COUNTERS = [
  [CalendarDate, "Days", "128"],
  [PebbleRound, "Pebbles", "1,240"],
  [KarmaDot, "Karma", "86"],
] as const

/**
 * The stones of #1384 (spec D7) in an M3 island, at the mock's seeds: the quick
 * actions row, the counters, a chip, the settings button, the first-five
 * stepper with two pieces lit, and the avatar pentagon. Resize the window to
 * watch the rocks redraw.
 */
export function StonesSpecimen() {
  return (
    <div id="stones-specimen" className="flex flex-col gap-3">
      <h3 className="font-heading text-xl">Stones</h3>
      <p className="max-w-2xl text-sm text-muted-foreground">
        One shared port of the app&apos;s stone geometry (<code>lib/stones/</code>): the same seed and size draw the
        same stone as iOS.
      </p>
      <M3Island className="rounded-xl p-6">
        <div className="mx-auto flex max-w-md flex-col gap-8">
          <SplitStone
            n={3}
            height={100}
            fill={PRIMARY_CONTAINER}
            cells={ACTIONS.map(([Icon, label], k) => (
              <div
                key={label}
                className={`pb-type-label flex h-full flex-col justify-between pt-[15px] pr-3 pb-[14px] text-m3-on-primary-container ${k === 0 ? "pl-4" : "pl-5"}`}
              >
                <Icon className="size-7" />
                <span>{label}</span>
              </div>
            ))}
          />
          <SplitStone
            n={3}
            height={88}
            seed="days|pebbles|karma"
            fill={SURFACE_CONTAINER}
            cells={COUNTERS.map(([Icon, label, value], k) => (
              <div key={label} className={`flex h-full flex-col justify-center py-3 pr-2.5 ${k === 0 ? "pl-4" : "pl-5"}`}>
                <span className="pb-type-counter text-m3-on-surface">{value}</span>
                <span className="pb-type-row-line mt-1.5 flex items-center gap-1.5 text-m3-on-surface-variant">
                  <Icon className="size-[18px] text-m3-primary" />
                  {label}
                </span>
              </div>
            ))}
          />
          <div className="flex items-center gap-6">
            <Stone seed="chip-0" corner={16} fill={SURFACE_CONTAINER} className="grid h-[46px] w-12 shrink-0 place-items-center">
              <StonesAlignedBottom className="size-[26px] text-m3-primary" />
            </Stone>
            <Stone seed="settings" corner={16} fill={SURFACE_CONTAINER_HIGH} className="grid h-11 w-[46px] shrink-0 place-items-center">
              <Slider className="size-[22px] text-m3-on-surface" />
            </Stone>
            <Pentagon>
              <PebbleDottedGlyph className="size-[58px]" />
            </Pentagon>
          </div>
          <SplitStone
            n={5}
            height={6}
            seed="first-five"
            options={{ corner: 3, gutter: 5, fillet: 2, lean: 0.9 }}
            fill={[LIT, LIT, UNLIT, UNLIT, UNLIT]}
          />
        </div>
      </M3Island>
    </div>
  )
}
