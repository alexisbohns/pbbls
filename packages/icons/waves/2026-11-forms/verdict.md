# Verdict — 2026-11-forms

## Round 1 (2026-11-09)

The maintainer reviewed the 213 glyphs the role guess flagged, on the design page (`verdicts.json`): 82 ok, 117 fix (each with a note), 14 drop-twin.

The notes changed the model, not only the drawings (spec amended 2026-11-09, decision log the same day):

- the accent the guess called `focus` is the **mark**, and the form `half` is **marked**; it combines with every fill, so a solid can be marked too (the mark is cut out of it);
- numbered marks were mixing three things: real sequences became **steps**, pieces that fill in any combination became body **parts**, and a single lit accent is the mark; a glyph never has a single step (the lint refuses it);
- a dotted line is a **drawn form** of its solid-line glyph (`src/<name>.dotted.svg`), not a glyph of its own;
- the solid also carves a line standing on the body's edge (a door on the ground line), not a rim traced along the outline.

## Part 3b: what was drawn

| Batch | Glyphs |
|---|---:|
| The accent as a mark, most with a solid (`home`, `lock`, `heart-heart`, `people`…) | 20 |
| A solid instead of a meaningless step (`orange`, `purse`, `yarn`…) | 17 |
| Solids (everyday glyphs) | 25 + 31 |
| Parts: `dolmen`, `dolmen-2`, `moon-cloud`, `circles-joined`, `binoculars-row`, `candle-simple`, `users` | 7 |
| Steps: `collection`, `cocoon`, `ziggurat`, `pyramid-layers`, the three bottles, both umbrellas, `pine-cone`, `pine`, `signal-dots`, `stones-link`, `palette-3` | 14 |
| Dotted forms: `pebble-glyph`, `pebble-glyph-open` | 2 |
| Twins dropped (roles removed, `.active` deleted; no app used them) | 14 |
| Set-wide: every single step became the mark (154) or the body (12); a drawn dot inside a lit mark joined it (34) | 200 |

The set now: 950 glyphs, 360 with a solid, 560 with a mark, 7 with parts, 29 with steps, 2 with a dotted form, 83 regular only.

## Round 2 (pending)

`round2.json` lists the 213 glyphs whose source changed; the design page's Show → Round 2 lists them with fresh verdicts and each glyph's round 1 note. Rough spots known before the review:

- `people`, `users`: a figure's torso closes with a chord, so its shoulder fills as a wedge; `users` has no marked form any more (its old accent became the right figure's head).
- `home-hive` solid · marked: the door is narrow, a fleck of the cut mark remains.
- `hand-palm`, `hand-wave`: the solid is a mitt (finger gaps narrower than a carve).
- `poo-smell`, `cursor-pointer`: plain silhouettes, the drawing has no inner line to carve.
- `yarn`: two bands carved, the note said one.
- `exclamation-double`, `exclamation-triple`: no solid (bars and dots have no silhouette); marked lights the dots.
- `piglet` solid · marked: the ears are marks, so they turn white.
- `umbrella-above`: the drawing has no drops; six panels light one by one, step 7 fills the canopy (one spoke gap stays carved).
- `cromlech`, `pouch` (round 1 ok): unchanged drawings, their lit form is now `marked` and `solid` instead of `progress-1`.

## For part 7

- `pebble-glyph-dotted`, `pebble-glyph-dotted-open`: now the dotted forms of `pebble-glyph`, `pebble-glyph-open`; their rows go when the web call sites move.
- `palette-1`: to merge with `palette-3` into one `palette` (steps and a solid are on `palette-3`).
