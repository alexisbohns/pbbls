// The press, from the final mock (#1052, spec §Ripple and glint). Every
// animation is finite; nothing runs unless a stone is pressed.

/** How far the waves travel, in stone widths, so every ripple covers about the same ground. */
export const RIPPLE_REACH = { small: 7.2, medium: 5.6, large: 4.4 } as const

export const WAVE = {
  duration: 1650,
  durationStep: 170,
  delay: 120,
  delayStep: 230,
  easing: "cubic-bezier(.12,.55,.3,1)",
} as const

type Motion = { keyframes: Keyframe[]; options: KeyframeAnimationOptions }

/** The stone dips under the finger and springs back. */
export const DIP: Motion = {
  keyframes: [
    { transform: "scale(1)" },
    { transform: "scale(.9)", offset: 0.26 },
    { transform: "scale(1.03)", offset: 0.6 },
    { transform: "scale(1)" },
  ],
  options: { duration: 520, easing: "cubic-bezier(.3,.7,.2,1)" },
}

/** The raked rings swell once. */
export const RINGS: Motion = {
  keyframes: [{ transform: "scale(1)" }, { transform: "scale(1.08)", offset: 0.35 }, { transform: "scale(1)" }],
  options: { duration: 1150, delay: 110, easing: "cubic-bezier(.2,.7,.2,1)" },
}

/** A sheen band crosses the stone from the light. */
export const SWEEP: KeyframeAnimationOptions = { duration: 900, delay: 140, easing: "cubic-bezier(.37,0,.63,1)" }

/** A four-point spark lands where the light is. */
export const SPARK: Motion = {
  keyframes: [
    { transform: "scale(0) rotate(0deg)", opacity: 0 },
    { transform: "scale(1) rotate(40deg)", opacity: 1, offset: 0.35 },
    { transform: "scale(0) rotate(90deg)", opacity: 0 },
  ],
  options: { duration: 760, delay: 300, easing: "cubic-bezier(.3,.6,.3,1)" },
}

/** With reduced motion: the rings darken and a still sheen holds this long. */
export const STILL_TOUCH_MS = 900

/** A pressed stone opens its pebble once the dip has landed (spec D8). */
export const OPEN_DELAY_MS = 360

/** The haptic tick a press gives. */
export const PRESS_VIBRATION_MS = 6
