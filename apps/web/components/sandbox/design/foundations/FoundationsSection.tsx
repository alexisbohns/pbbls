import type { DesignTheme } from "@/lib/theme/design-theme"
import { DesignSection } from "../DesignSection"
import { BridgeTable } from "./BridgeTable"
import { ColorSwatch } from "./ColorSwatch"
import { M3Roles } from "./M3Roles"
import { ShapeScale } from "./ShapeScale"
import { TypeScale } from "./TypeScale"

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

type FoundationsSectionProps = { theme: DesignTheme; themeKey: string }

export function FoundationsSection({ theme, themeKey }: FoundationsSectionProps) {
  return (
    <DesignSection
      id="foundations"
      title="Foundations"
      description="Live values read from the page's computed styles, so what you see is what is applied. Pairs show their text contrast; anything under 4.5:1 is flagged."
    >
      <div className="flex flex-col gap-3">
        <h3 className="font-heading text-xl">App tokens</h3>
        <p className="max-w-2xl text-sm text-muted-foreground">
          What every component reads today. Under M3 they come from the bridge.
        </p>
        <div className="flex flex-wrap gap-3">
          {APP_TOKENS.map(([bg, fg]) => (
            <ColorSwatch
              key={bg}
              label={bg}
              background={`--${bg}`}
              foreground={fg ? `--${fg}` : undefined}
              themeKey={themeKey}
            />
          ))}
        </div>
      </div>
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
      <TypeScale />
      <ShapeScale />
    </DesignSection>
  )
}
