# The Pebbles icon DNA

The style paragraph every hand draws from, the line the foundation wave settled on, and the canvas rules the lint enforces (`npm run lint --workspace=@pbbls/icons`). The rules are machine-checked so a new hand cannot drift from the line by accident; the paragraph is what the hand reads first.

## DNA

Pebbles icons are small ink drawings, not geometry, drawn with a pen of constant width on a 24-unit square by a hand that knows the shape but never measures it. Fewest strokes that still name the thing, open-ended, no fills. Symmetry implied, never exact. Nothing cute or stock. An active icon is the same drawing after something happened inside it: a stone lights. That is the one active rule for this set.

## The line: Riverbed Reed

Every round thing is the same uneven stone drawn in three or four strokes that overlap where the stroke began; straight lines lean; corners are left open; nothing is a compass circle. Keep its weight and its overshoot. The round-3 hands made it the one vocabulary of the set: path stones, stepper nodes, people's heads, the collection bowl's stones, track, stack, the settings knobs, lenses, pebbles, the check and plus rings and the glyph stone are literally the same stone, closing by overlap, never with `Z`. The pen's dot (checkmark, plus-circle, karma, camera, idea) is a single touch at the line's own weight.

## The one active rule

A twin (`<name>.active`) is the base drawing with one small stone lit inside it, `fill="currentColor"`: the stone at the threshold of home, the far stone of the path, the stone between the two people, the top stone of the collection, the head of the figure, the stone inside the heart. Never a different drawing, never a filled outline. The lit series (`<name>.lit-1..3`) lights the drawing's own stones one at a time, solid, in walking, stacking or growing order; a stone that carries a mark (a glyph, a shine) is carved: one compound path, `fill-rule="evenodd"`, the mark knocked out at the stroke's width so the surface shows through.

## Canvas rules (what the lint checks)

| Rule | Error when | Why |
|---|---|---|
| Root | `<svg>` lacks exactly `xmlns`, `viewBox="0 0 24 24"`, `fill="none"`, `stroke="currentColor"`, `stroke-width="1.75"`, `stroke-linecap="round"`, `stroke-linejoin="round"`, or carries anything else | the emitters derive every surface's root from these seven; a `width`, `style` or `class` would mean one surface differs from the next |
| Elements | any child other than `<path d>`; any `transform`, `style`, `class`, `id`, `opacity`, text, mask, filter | VectorDrawable and CoreSVG render paths, not the rest; a transform would hide coordinates from the range check |
| Commands | a command other than absolute `M L C Q A Z` | relative commands hide coordinates from the range check; `H`, `V`, `S`, `T` are not in every reader's grammar |
| Range | any coordinate (end and control points; an arc's end point) outside `1.5..22.5` | the 1.75 stroke needs 0.875 of margin on each side of the 24 square, or the cap is clipped on one surface and not another |
| Fill | `fill="currentColor"` on a path in a base file; any other fill value; more than three filled paths in a twin (a filled area above 64 square units warns, it does not fail); `fill-rule` on an unfilled path, or any value but `evenodd`; `stroke="none"` on an unfilled path | a base is never filled (the paper shows through); only a lit stone is solid, and only a carved stone is even-odd; `stroke="none"` is how a solid or carved stone keeps its edge and its knockout at the drawn width |
| Dash | any `stroke-dasharray` | VectorDrawable has no dash support and CoreSVG's is partial; a dotted ring is drawn as short separate strokes so every surface gets the same geometry |
| Canonical | `format` would change the file | the emitters read one grammar with a tiny parser; `svgo` is a formatter here (editor cruft, root order, two decimals), never a path optimiser |
| Manifest | a row without `src/<name>.svg`; an `.svg` without a row; a listed variant without `src/<name>.<variant>.svg` or a twin file not listed in `variants`; a twin without its base; a variant outside `active`, `lit-1`, `lit-2`, `lit-3`; a duplicate or non-kebab name; a `wave` with no `waves/<wave>/` folder; an unknown `status` | `icons.json` is the order and the inventory every surface emits from; a drawing without a row, or a row without a drawing, is a silent gap on one surface |

Every error names the file and, for geometry, the offending number: a fix is a nudge to the drawing, never a relaxed rule.
