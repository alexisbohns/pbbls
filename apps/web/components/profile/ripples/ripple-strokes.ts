// Ripple stroke geometry, hand-ported from the SVG <path d="…"> definitions in
// issue #442 (authored against a 44×44 viewBox), mirroring the iOS
// `RippleStrokes.swift`. Ordered outermost (id 6) → innermost (id 1) so inner
// rings paint on top. `opacity` reproduces the iOS RippleBadge layering.

export type RippleStrokeDef = {
  id: number
  d: string
  opacity: number
}

export const RIPPLE_STROKES: RippleStrokeDef[] = [
  {
    id: 6,
    d: "M15.4023 2.71506 C26.241 -0.507724 41.9999 7.38652 41.9999 21.8993",
    opacity: 0.33,
  },
  {
    id: 5,
    d: "M7.37405 37.1175 C-1.10595 29.2114 0.748869 9.24398 10.831 4.78223",
    opacity: 0.33,
  },
  {
    id: 4,
    d: "M41.458 26.9565 C39.2185 38.9628 23.9232 45.4638 11.4043 40.1005",
    opacity: 0.33,
  },
  {
    id: 3,
    d: "M36.6088 19.5146 C39.4844 34.5339 18.0179 42.6778 10 31.0941",
    opacity: 0.66,
  },
  {
    id: 2,
    d: "M34.1755 13.5272 C25.9708 1.34962 4.58761 9.44313 7.58572 25.087",
    opacity: 0.66,
  },
  {
    id: 1,
    d: "M25.4147 30.7822 C22.7365 31.9714 19.5086 31.8785 16.4741 29.5504 C6.9764 22.2636 18.3687 7.65428 27.8664 14.941 C32.662 18.6202 32.1318 24.1663 29.2088 27.818",
    opacity: 1,
  },
]
