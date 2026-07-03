// Pure mapping from (strokeId, level, activeToday) → tone, ported verbatim from
// the iOS `RippleStrokeColor.swift` truth table (issue #442):
//   - strokeId > level                   → default  (outside current level)
//   - strokeId <= level &&  activeToday   → active   (created a pebble today)
//   - strokeId <= level && !activeToday   → inactive (no pebble today)

export type RippleTone = "default" | "active" | "inactive"

export function rippleStrokeTone(
  strokeId: number,
  level: number,
  activeToday: boolean,
): RippleTone {
  if (strokeId > level) return "default"
  return activeToday ? "active" : "inactive"
}

// Theme-aware token classes (each stroke is drawn with stroke="currentColor").
// default → border, active → accent primary, inactive → secondary text.
export const RIPPLE_TONE_CLASS: Record<RippleTone, string> = {
  default: "text-border",
  active: "text-primary",
  inactive: "text-muted-foreground",
}
