# The Pebbles icon DNA

The style paragraph every hand draws from, the line the foundation wave settled on, and the canvas rules the lint enforces (`npm run lint --workspace=@pbbls/icons`). The rules are machine-checked so a new hand cannot drift from the line by accident; the paragraph is what the hand reads first.

## DNA

Pebbles icons are small ink drawings, not geometry, drawn with a pen of constant width on a 24-unit square by a hand that knows the shape but never measures it. Fewest strokes that still name the thing, open-ended, no fills. Symmetry implied, never exact. Nothing cute or stock. An active icon is the same drawing after something happened inside it: a stone lights. That is the one active rule for this set.

## The line: Riverbed Reed

Every round thing is the same uneven stone drawn in three or four strokes that overlap where the stroke began; straight lines lean; corners are left open; nothing is a compass circle. Keep its weight and its overshoot. The round-3 hands made it the one vocabulary of the set: path stones, stepper nodes, people's heads, the collection bowl's stones, track, stack, the settings knobs, lenses, pebbles, the check and plus rings and the glyph stone are literally the same stone, closing by overlap, never with `Z`. The pen's dot (checkmark, plus-circle, karma, camera, idea) is a single touch at the line's own weight.

## The one active rule

A source is never filled. Forms that fill (solid, parts, progress, the filled mark) are worked out by the generator from the roles (Roles and forms); an override (`<name>.<form>.svg`) is the only drawn file that carries a fill, and it keeps the DNA.

## Canvas rules (what the lint checks)

| Rule | Error when | Why |
|---|---|---|
| Root | `<svg>` lacks exactly `xmlns`, `viewBox="0 0 24 24"`, `fill="none"`, `stroke="currentColor"`, `stroke-width="1.75"`, `stroke-linecap="round"`, `stroke-linejoin="round"`, or carries anything else | the emitters derive every surface's root from these seven; a `width`, `style` or `class` would mean one surface differs from the next |
| Elements | any child other than `<path d>`; any `transform`, `style`, `class`, `id`, `opacity`, text, mask, filter | VectorDrawable and CoreSVG render paths, not the rest; a transform would hide coordinates from the range check |
| Commands | a command other than absolute `M L C Q A Z` | relative commands hide coordinates from the range check; `H`, `V`, `S`, `T` are not in every reader's grammar |
| Range | any coordinate (end and control points; an arc's end point) outside `1.5..22.5` | the 1.75 stroke needs 0.875 of margin on each side of the 24 square, or the cap is clipped on one surface and not another |
| Fill | `fill="currentColor"` outside an override; any other fill value; `fill-rule` on an unfilled path, or any value but `evenodd`; `stroke="none"` on an unfilled path | a base is never filled (the paper shows through); only a carved stone is even-odd; `stroke="none"` is how a solid or carved stone keeps its edge and its knockout at the drawn width |
| Dash | any `stroke-dasharray` | VectorDrawable has no dash support and CoreSVG's is partial; a dotted ring is drawn as short separate strokes so every surface gets the same geometry |
| Canonical | `format` would change the file | the emitters read one grammar with a tiny parser; `svgo` is a formatter here (editor cruft, root order, two decimals), never a path optimiser |
| Manifest | a row without `src/<name>.svg`; an `.svg` without a row; a source other than `<name>.svg`, `<name>.dotted.svg` or `<name>.<form>.svg`; a duplicate or non-kebab name; a `wave` with no `waves/<wave>/` folder; an unknown `status` | `icons.json` is the order and the inventory every surface emits from; a drawing without a row, or a row without a drawing, is a silent gap on one surface |

Every error names the file and, for geometry, the offending number: a fix is a nudge to the drawing, never a relaxed rule.

## Roles and forms (2026-11, #1607)

A path may carry `data-role="body"` (the main shape, filled the way SVG fills a path: an open path closes with a straight segment), optionally split into 2–4 named pieces with `data-part="<word>"` (every body path then names one); `data-role="mark"` (the one accent that carries the meaning: a door dot, a notch); or `data-role="step" data-step="1…n"` (a real sequence, in fill order). Steps are for sequences only: pieces that fill in any combination are parts, a single accent is the mark.

A form is a fill plus, when the glyph has a mark, `marked`. The fills are regular, solid (the body filled, every line within half a stroke of it carved out at the stroke width), parts (any combination of parts short of all of them, carved the same way), progress k of n (steps 1…k filled) and dotted (only where `src/<name>.dotted.svg` is drawn: a plain line drawing, no role, no fill). Marked fills the mark on regular and progress, and cuts it out of a solid or parts fill (or fills it beside the fill when it lies outside). A mark or step path may carry `data-rest="hidden"`: it is not drawn at rest and is drawn filled whenever its role fills (a lit dot the base does not show). A source is never filled; `src/<name>.<form>.svg`, `<form>` a form key such as `marked`, `solid-marked`, `parts-moon` or `progress-2`, overrides a worked-out form that looks wrong. A name never ends in a form word (regular, marked, solid, parts, of).
