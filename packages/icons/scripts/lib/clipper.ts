/**
 * The one import of clipper-lib (CommonJS) for the ES-module generator, so the
 * interop is decided once. Coordinates are integers: callers scale by SCALE.
 */
import ClipperLib from "clipper-lib"

export { ClipperLib }

/** Glyph units to Clipper integers: 0.001 of a unit is far below a 3× hero pixel. */
export const SCALE = 1000
