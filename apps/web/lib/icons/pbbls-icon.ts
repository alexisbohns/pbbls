import type { ComponentType } from "react"
import type { PbblsIconProps } from "@pbbls/icons"

/**
 * The five sizes a Pebbles icon draws at (web icon migration spec D7), as
 * Tailwind classes in rem: 16, 20, 22, 28 and 48 px at the default root, the
 * iOS `PbblsIconSize` values. rem follows the reader's browser font size the
 * way Dynamic Type does on iOS; it does not follow the line, so `inline` is 16
 * beside `text-xs` and beside `text-base` alike.
 *
 * Size an icon with a class, never with the generated `size` prop: `size`
 * writes `width`/`height` attributes, which the shadcn slots'
 * `[&_svg:not([class*='size-'])]:size-*` rule overrides, while a `size-*`
 * class both sizes the icon and opts it out of the slot default.
 *
 * - `inline`: on a line of text (pills, counters, captions, labelled buttons,
 *   menu items, the Lab timeline), and in icon-only controls of 24–32 px.
 * - `row`: the lead of a list or settings row, a card header, a tile trigger,
 *   the bottom nav, a shortcut tile, icon-only controls of 36 px, and beside
 *   display text (`text-xl` and up).
 * - `toolbar`: icon-only controls of 40 px and more.
 * - `tile`: an icon in a disc or slot of 48 px and more that is not an empty state.
 * - `hero`: empty states and placeholders in a 96 px slot, the celebration,
 *   the Record photo hero.
 *
 * 16 is the floor. Colour only through `text-*`; never `fill-*`, `stroke-*` or
 * `strokeWidth` (the 1.75 stroke is the DNA, a filled state is a twin):
 * `<PebbleRound className={cn(PBBLS_ICON_SIZE.inline, "text-primary")} />`.
 */
export const PBBLS_ICON_SIZE = {
  inline: "size-4",
  row: "size-5",
  toolbar: "size-5.5",
  tile: "size-7",
  hero: "size-12",
} as const

export type PbblsIconSize = keyof typeof PBBLS_ICON_SIZE

/** A generated Pebbles icon, for maps and props whose every value is one (spec D10). */
export type PbblsIconComponent = ComponentType<PbblsIconProps>
