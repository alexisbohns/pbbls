import { COLOR_WORLDS } from "@/lib/config/color-worlds"
import type { ColorWorld } from "@/lib/types"

/**
 * State of the /sandbox/design switcher (#986). It lives in the URL so a reload
 * or a shared link lands on the same theme, and so a two-iframe compare view
 * can drive it later. Pure: the hook in components/sandbox/design applies it.
 */

export type DesignThemeName = "current" | "m3"
export type DesignMode = "light" | "dark"
export type DesignContrast = "standard" | "medium" | "high"

export interface DesignTheme {
  theme: DesignThemeName
  /** Used when theme is "current"; kept in the URL while on M3 so T flips back to it. */
  world: ColorWorld
  mode: DesignMode
  /** Used when theme is "m3". */
  contrast: DesignContrast
}

const DEFAULT_WORLD: ColorWorld = "blush-quartz"

export const DEFAULT_DESIGN_THEME: DesignTheme = {
  theme: "m3",
  world: DEFAULT_WORLD,
  mode: "light",
  contrast: "standard",
}

const THEMES: readonly DesignThemeName[] = ["current", "m3"]
const MODES: readonly DesignMode[] = ["light", "dark"]
const CONTRASTS: readonly DesignContrast[] = ["standard", "medium", "high"]
const WORLDS: readonly ColorWorld[] = COLOR_WORLDS.map((w) => w.id)

/** Every <html> class the design page may add, and therefore may remove. */
const MANAGED_CLASSES: readonly string[] = [
  "m3",
  "dark",
  "m3-contrast-medium",
  "m3-contrast-high",
  ...WORLDS.filter((w) => w !== DEFAULT_WORLD),
]

function pick<T extends string>(value: string | null, allowed: readonly T[], fallback: T): T {
  return allowed.find((a) => a === value) ?? fallback
}

export function parseDesignTheme(params: Pick<URLSearchParams, "get">): DesignTheme {
  return {
    theme: pick(params.get("theme"), THEMES, DEFAULT_DESIGN_THEME.theme),
    world: pick(params.get("world"), WORLDS, DEFAULT_DESIGN_THEME.world),
    mode: pick(params.get("mode"), MODES, DEFAULT_DESIGN_THEME.mode),
    contrast: pick(params.get("contrast"), CONTRASTS, DEFAULT_DESIGN_THEME.contrast),
  }
}

export function serializeDesignTheme(theme: DesignTheme): string {
  return new URLSearchParams({
    theme: theme.theme,
    world: theme.world,
    mode: theme.mode,
    contrast: theme.contrast,
  }).toString()
}

/** The <html> classes that render `theme`. The default world has no class (globals.css :root). */
export function designClassesFor(theme: DesignTheme): string[] {
  const dark = theme.mode === "dark" ? ["dark"] : []
  if (theme.theme === "m3") {
    return ["m3", ...dark, ...(theme.contrast === "standard" ? [] : [`m3-contrast-${theme.contrast}`])]
  }
  return [...(theme.world === DEFAULT_WORLD ? [] : [theme.world]), ...dark]
}

/** The classes the app itself would put on <html> for the viewer's saved preferences. */
export function appClassesFor(mode: DesignMode, world: ColorWorld): string[] {
  return designClassesFor({ ...DEFAULT_DESIGN_THEME, theme: "current", mode, world })
}

/**
 * What to remove/add to turn `current` into `target`, touching only the
 * classes this page manages. Empty when nothing differs, which is what keeps
 * the MutationObserver in useDesignTheme from looping.
 */
export function classDiff(
  current: readonly string[],
  target: readonly string[],
): { remove: string[]; add: string[] } {
  return {
    remove: current.filter((c) => MANAGED_CLASSES.includes(c) && !target.includes(c)),
    add: target.filter((c) => !current.includes(c)),
  }
}
