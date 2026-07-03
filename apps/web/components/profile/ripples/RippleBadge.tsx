import { RIPPLE_STROKES } from "./ripple-strokes"
import { rippleStrokeTone, RIPPLE_TONE_CLASS } from "./ripple-tone"

type RippleBadgeProps = {
  level: number
  activeToday: boolean
}

/**
 * 44×44 concentric-ripple badge with the level digit centered. Web port of the
 * iOS `RippleBadge`: strokes are tinted by tone (default/active/inactive) via
 * theme token classes; the digit is always the foreground color.
 */
export function RippleBadge({ level, activeToday }: RippleBadgeProps) {
  const clamped = Math.min(Math.max(level, 0), 6)

  return (
    <div className="relative size-11 shrink-0" aria-hidden>
      <svg viewBox="0 0 44 44" fill="none" className="size-full">
        {RIPPLE_STROKES.map((stroke) => (
          <path
            key={stroke.id}
            d={stroke.d}
            stroke="currentColor"
            strokeWidth={2}
            strokeLinecap="round"
            opacity={stroke.opacity}
            className={RIPPLE_TONE_CLASS[rippleStrokeTone(stroke.id, clamped, activeToday)]}
          />
        ))}
      </svg>
      <span className="absolute inset-0 flex items-center justify-center text-xs font-semibold text-foreground">
        {clamped}
      </span>
    </div>
  )
}
