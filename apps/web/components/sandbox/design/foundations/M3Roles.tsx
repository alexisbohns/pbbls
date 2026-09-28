import { ColorSwatch } from "./ColorSwatch"

type Pair = readonly [background: string, foreground?: string]

const GROUPS: { title: string; pairs: Pair[] }[] = [
  { title: "Primary", pairs: [["primary", "on-primary"], ["primary-container", "on-primary-container"], ["inverse-primary"]] },
  { title: "Secondary", pairs: [["secondary", "on-secondary"], ["secondary-container", "on-secondary-container"]] },
  { title: "Tertiary", pairs: [["tertiary", "on-tertiary"], ["tertiary-container", "on-tertiary-container"]] },
  { title: "Error (orange)", pairs: [["error", "on-error"], ["error-container", "on-error-container"]] },
  { title: "Sand (custom colour)", pairs: [["sand", "on-sand"], ["sand-container", "on-sand-container"]] },
  {
    title: "Surface",
    pairs: [
      ["surface", "on-surface"],
      ["surface-variant", "on-surface-variant"],
      ["surface-dim", "on-surface"],
      ["surface-bright", "on-surface"],
      ["inverse-surface", "inverse-on-surface"],
    ],
  },
  {
    title: "Surface containers",
    pairs: [
      ["surface-container-lowest", "on-surface"],
      ["surface-container-low", "on-surface"],
      ["surface-container", "on-surface"],
      ["surface-container-high", "on-surface"],
      ["surface-container-highest", "on-surface"],
    ],
  },
  { title: "Outline", pairs: [["outline"], ["outline-variant"], ["scrim"]] },
  {
    title: "Fixed",
    pairs: [
      ["primary-fixed", "on-primary-fixed"],
      ["primary-fixed-dim", "on-primary-fixed-variant"],
      ["secondary-fixed", "on-secondary-fixed"],
      ["secondary-fixed-dim", "on-secondary-fixed-variant"],
      ["tertiary-fixed", "on-tertiary-fixed"],
      ["tertiary-fixed-dim", "on-tertiary-fixed-variant"],
    ],
  },
]

export function M3Roles({ themeKey }: { themeKey: string }) {
  return (
    <div className="flex flex-col gap-6">
      <h3 className="font-heading text-xl">M3 roles</h3>
      {GROUPS.map((group) => (
        <div key={group.title} className="flex flex-col gap-2">
          <h4 className="text-sm font-medium">{group.title}</h4>
          <div className="flex flex-wrap gap-3">
            {group.pairs.map(([bg, fg]) => (
              <ColorSwatch
                key={bg}
                label={bg}
                background={`--m3-${bg}`}
                foreground={fg ? `--m3-${fg}` : undefined}
                themeKey={themeKey}
              />
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
