"use client"

import type { ReactNode } from "react"
import type { ArbitraryFamily, DesignAudit, FindingGroup } from "@/lib/design/audit"
import type { EmotionPalette } from "@/lib/data/useEmotionPalettes"
import { CATEGORY_PALETTES } from "@/lib/seed/sandbox-palettes"
import { useDesign } from "../DesignContext"
import { DesignSection } from "../DesignSection"
import { FindingList } from "../FindingList"
import { BridgeTable } from "../foundations/BridgeTable"
import { ColorSwatch } from "../foundations/ColorSwatch"
import { HomeYouSpecimen } from "../foundations/HomeYouSpecimen"
import { M3Roles } from "../foundations/M3Roles"
import { ShapeScale } from "../foundations/ShapeScale"
import { StonesSpecimen } from "../foundations/StonesSpecimen"
import { TypeScale } from "../foundations/TypeScale"

const APP_TOKENS: readonly (readonly [background: string, foreground?: string])[] = [
  ["background", "foreground"],
  ["surface", "foreground"],
  ["surface-alt", "foreground"],
  ["card", "card-foreground"],
  ["popover", "popover-foreground"],
  ["primary", "primary-foreground"],
  ["secondary", "secondary-foreground"],
  ["muted", "muted-foreground"],
  ["accent", "accent-foreground"],
  ["destructive", "destructive-foreground"],
  ["border"],
  ["input"],
  ["ring"],
]

const PALETTE_ROLES: readonly (keyof EmotionPalette)[] = [
  "primary_color",
  "secondary_color",
  "light_color",
  "surface_color",
  "shaded_color",
  "dark_color",
]

/** `#RRGGBBAA` from Studio → a CSS colour. */
function cssHex(value: string): string {
  return value.length === 9 && value.endsWith("FF") ? value.slice(0, 7) : value
}

function Chip({ color }: { color: string }) {
  return <span aria-hidden className="block size-5 shrink-0 rounded-md border border-border" style={{ background: color }} />
}

/** `bg-amber-300/10` → `var(--color-amber-300)`: Tailwind emits the variable for every class the source uses. */
function paletteVar(value: string): string {
  const name = value.replace(/^[a-z]+-/, "").replace(/\/\d+$/, "")
  return `var(--color-${name})`
}

function SubSection({ title, description, children }: { title: string; description?: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-3">
      <h3 className="font-heading text-xl">{title}</h3>
      {description && <p className="max-w-2xl text-sm text-muted-foreground">{description}</p>}
      {children}
    </div>
  )
}

type ColorsAudit = Pick<DesignAudit, "hex" | "colorFunctions" | "palette" | "arbitrary" | "scannedFiles">

function family(audit: ColorsAudit, f: ArbitraryFamily): FindingGroup[] {
  return audit.arbitrary.filter((g) => g.family === f)
}

/**
 * The foundations, live: tokens and roles read from the computed styles (so
 * what shows is what applies), the emotion palettes, type and shape — each
 * followed by what the source writes outside them.
 */
export function ColorsPage({ audit }: { audit: ColorsAudit }) {
  const { theme, themeKey } = useDesign()
  const literals = [...audit.hex, ...audit.colorFunctions]
  return (
    <>
      <DesignSection
        id="colors"
        title="Colors"
        description="Live values read from the page's computed styles under the theme above. Pairs show their text contrast; anything under 4.5:1 is flagged."
      >
        <SubSection title="App tokens" description="What every component reads today. Under M3 they come from the bridge.">
          <div className="flex flex-wrap gap-3">
            {APP_TOKENS.map(([bg, fg]) => (
              <ColorSwatch key={bg} label={bg} background={`--${bg}`} foreground={fg ? `--${fg}` : undefined} themeKey={themeKey} />
            ))}
          </div>
        </SubSection>
        {theme.theme === "m3" ? (
          <>
            <M3Roles themeKey={themeKey} />
            <BridgeTable />
          </>
        ) : (
          <p className="text-sm text-muted-foreground">
            Switch to M3 (press <kbd>T</kbd>) to see the role palette and the bridge.
          </p>
        )}
        <SubSection
          title="Emotion palettes"
          description="The seven emotion categories as the sandbox carries them (a snapshot of Supabase Studio, not the source of truth). The same in every theme."
        >
          <table className="w-full max-w-3xl text-xs">
            <thead>
              <tr className="text-left text-muted-foreground">
                <th className="py-1 font-medium">Category</th>
                {PALETTE_ROLES.map((role) => (
                  <th key={role} className="py-1 font-mono font-medium">
                    {role.replace("_color", "")}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {Object.entries(CATEGORY_PALETTES).map(([category, palette]) => (
                <tr key={category} className="border-t border-border">
                  <td className="py-1.5 capitalize">{category}</td>
                  {PALETTE_ROLES.map((role) => (
                    <td key={role} className="py-1.5" title={palette[role]}>
                      {palette[role] ? <Chip color={cssHex(palette[role])} /> : "—"}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </SubSection>
        <SubSection
          title="Literal colours in code"
          description={`Hex and colour functions written in components/ and app/ (${audit.scannedFiles} files, the design page excluded). None of them follows the theme, the world or dark mode unless the code switches by hand.`}
        >
          <FindingList groups={literals} empty="No literal colour in the source." lead={(g) => <Chip color={g.value} />} />
        </SubSection>
        <SubSection
          title="Default palette classes in code"
          description="Tailwind's own palette (amber-300, neutral-800, white): fixed colours outside the tokens."
        >
          <FindingList groups={audit.palette} empty="No default palette class in the source." lead={(g) => <Chip color={paletteVar(g.value)} />} />
        </SubSection>
      </DesignSection>

      <DesignSection id="type" title="Type" description="The M3 scale, the app's faces and the Home/You roles, then every type value written off the scale.">
        <TypeScale />
        <HomeYouSpecimen />
        <SubSection
          title="Off-scale type in code"
          description="Arbitrary sizes, weights, leading and tracking. Each is a size the scale does not have."
        >
          <FindingList groups={family(audit, "type")} empty="Every type value is on the scale." />
        </SubSection>
      </DesignSection>

      <DesignSection id="shape" title="Shape & stones" description="The M3 shape scale and what the bridge does to rounded-*, the stones, then every radius written off the scale.">
        <ShapeScale />
        <StonesSpecimen />
        <SubSection title="Off-scale radii in code">
          <FindingList groups={family(audit, "radius")} empty="Every radius is on the scale." />
        </SubSection>
        <SubSection title="Off-scale spacing and sizes in code" description="Arbitrary padding, margins, gaps, offsets, widths and heights.">
          <FindingList groups={[...family(audit, "spacing"), ...family(audit, "size")]} empty="Every spacing value is on the scale." />
        </SubSection>
      </DesignSection>
    </>
  )
}
