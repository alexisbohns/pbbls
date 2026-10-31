/**
 * How a stone is painted (#1384, spec D7): the SVG fill class once the stone is
 * measured and drawn, and the matching background class for the box before
 * that, so the first paint (and the server render) already shows the colour.
 * Both are literal Tailwind classes written at the call site.
 */
export type StoneFill = { fill: string; bg: string }
