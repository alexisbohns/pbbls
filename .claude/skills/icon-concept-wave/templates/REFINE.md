# Refinement round 1 (pilot squad s1-delivery)

You refine drawings the maintainer reviewed and marked "Refine", each with a note. `refine/items.json` lists them; your items are those whose `agent` is your agent (`opus` or `sonnet`), by `batch`. Each item has: `name` (the maintainer's name for the drawing), `concept` and its `meaning`, `idea`, the maintainer's `note` (what to change), and `original` (the three drawings as shipped: regular, solid, marked, as the inner SVG of each symbol).

## What to do for each item

1. Read the note. It is the instruction; do exactly what it asks and nothing more. Keep everything it does not mention (same subject, same composition, same line).
2. Apply the v2 rules of `BRIEF.md` (variants section and the line): the solid fills the regular's own closed paths and keeps their stroke; the marked fills one existing small closed shape, or adds a filled 3 to 6 unit dot whose centre is inside a closed shape; every straight line leans (nudge an exactly horizontal or vertical line by 0.2 to 0.5); a variant that makes no sense is `data-skip="true"`. The checker enforces all of it on all three variants, so an original that breaks a rule must be corrected even if the note does not mention it, with the smallest change.
3. Reading the notes: "give up the solid" or "give up the marked" means that variant becomes a skip. "bigger dot mark" or "bigger mark" means a filled dot of 4 to 5 units. "put the dot in the circle" means the mark is a filled dot inside that circle, or the circle itself filled. "dot inside the circle, in solid the circle completely filled" means the solid fills that circle. "too sharp" means rebuild the solid per the v2 rule. "too geometrical / squared / not hand drawn" means redraw the regular itself with an uneven, leaning, soft-cornered hand (same subject), then derive the variants from it. "no dot in the regular" means the regular carries no dot at all.

## Output

Symbols are named after the item: `<name>-regular`, `<name>-solid`, `<name>-marked` (the `name` field, not the concept). One file per batch: `refine/<agent>-<batch>.svg`, same format as the first round (`<svg xmlns="http://www.w3.org/2000/svg">` containing only `<symbol>` elements with the usual root attributes, paths only). Write each batch in one Write call. After each batch run, from the wave folder:

    python3 -I check-refine.py <agent>-<batch>

and fix every error. Then write `refine/NOTES-<agent>.md` (at most 8 lines: any note you could not follow and why, any original you had to correct for a rule). Do not read other hands' folders; the `original` fields are all you need.
