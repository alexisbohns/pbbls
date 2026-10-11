"use client"

import { LitStone } from "@/components/pebble/stone/LitStone"
import { StoneSeal } from "@/components/pebble/stone/StoneSeal"
import { StoneSprite } from "@/components/pebble/stone/StoneSprite"
import type { Polarity, Size } from "@/lib/config/pebble-geometry"
import { hashUUID } from "@/lib/engine/seed"
import { CATEGORY_PALETTES } from "@/lib/seed/sandbox-palettes"
import { SANDBOX_MARKS } from "@/lib/seed/sandbox-pebbles"
import { Specimen } from "./Specimen"

const SIZES: Size[] = ["small", "medium", "large"]
const POLARITIES: Polarity[] = ["highlight", "neutral", "lowlight"]
const MATERIAL: Record<Polarity, string> = { highlight: "gem", neutral: "pebble", lowlight: "lava" }
const CATEGORIES = Object.keys(CATEGORY_PALETTES)
const SHAPES = SIZES.flatMap((size) => POLARITIES.map((polarity) => ({ size, polarity })))

const glyphAt = (i: number) => SANDBOX_MARKS[i % SANDBOX_MARKS.length]

/**
 * The lit stone and its seal (#1052) on the Spine's paper: nine shapes by the
 * seven emotion categories, then the branches the product takes (no glyph, no
 * emotion yet). Paper and Night follow the page's mode toggle.
 */
export function StoneSpecimens() {
  return (
    <>
      <StoneSprite />
      <Specimen
        id="lit-stone"
        note="Nine shapes × seven categories, at phone size below 900 px and wide size above. Each stone is seeded by its cell, so the facets, specks and pits differ."
        className="spine-paper overflow-x-auto"
      >
        <table className="border-separate border-spacing-x-3 border-spacing-y-4 text-(--spine-ink-2)">
          <thead>
            <tr>
              <th />
              {CATEGORIES.map((category) => (
                <th key={category} scope="col" className="text-xs font-medium capitalize">
                  {category}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {SHAPES.map(({ size, polarity }, row) => (
              <tr key={`${size}-${polarity}`}>
                <th scope="row" className="pr-2 text-left text-xs font-medium whitespace-nowrap">
                  {size} · {MATERIAL[polarity]}
                </th>
                {CATEGORIES.map((category, col) => (
                  <td key={category} className="h-20 w-20 align-middle">
                    <LitStone
                      size={size}
                      polarity={polarity}
                      palette={CATEGORY_PALETTES[category]}
                      glyph={glyphAt(row + col)}
                      seed={hashUUID(`${size}-${polarity}-${category}`)}
                      className="mx-auto"
                    />
                  </td>
                ))}
              </tr>
            ))}
            <tr>
              <th scope="row" className="pr-2 text-left text-xs font-medium whitespace-nowrap">
                edge cases
              </th>
              <td className="h-20 w-20 align-middle">
                <LitStone
                  size="medium"
                  polarity="highlight"
                  palette={CATEGORY_PALETTES.joy}
                  glyph={null}
                  seed={1}
                  className="mx-auto"
                />
              </td>
              <td className="h-20 w-20 align-middle">
                <LitStone size="large" polarity="lowlight" palette={null} glyph={glyphAt(0)} seed={2} className="mx-auto" />
              </td>
              <td colSpan={5} className="text-xs">
                No glyph (a domain default missing from the mark map), then a moment with no emotion yet: a blank,
                uncarved river pebble.
              </td>
            </tr>
          </tbody>
        </table>
      </Specimen>

      <Specimen
        id="stone-seal"
        note="The flat print for the contents and the card index: relief, line, engraving. Printed in primary on Paper and secondary at Night."
        className="spine-paper"
      >
        {POLARITIES.map((polarity) => (
          <div key={polarity} className="flex flex-col items-center gap-3">
            <div className="flex items-end gap-4">
              {SIZES.map((size, i) => (
                <StoneSeal
                  key={size}
                  size={size}
                  polarity={polarity}
                  palette={CATEGORY_PALETTES.pride}
                  glyph={glyphAt(i)}
                  className={{ small: "w-5", medium: "w-6", large: "w-[27px]" }[size]}
                />
              ))}
              <StoneSeal
                size="medium"
                polarity={polarity}
                palette={CATEGORY_PALETTES.peace}
                glyph={glyphAt(3)}
                variant="print"
                className="w-14"
              />
            </div>
            <span className="text-xs text-(--spine-ink-2)">{MATERIAL[polarity]}</span>
          </div>
        ))}
      </Specimen>
    </>
  )
}
