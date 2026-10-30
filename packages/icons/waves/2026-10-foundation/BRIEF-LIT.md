# Pebbles icon set — the lit series

You are one of three hands. The foundation set is drawn; this wave adds a *lit progression* to the drawings that contain stones or stone-like rounds. Read `lit-sources.svg` (the 40 source drawings, final geometry, do not alter their strokes) and `lit-families.json` (the families).

## The rule
For every source drawing, produce up to three variants: `<name>-lit-1`, `<name>-lit-2`, `<name>-lit-3`, with one, two and three of its stones lit. A lit stone is the same stone with `fill="currentColor"` on a small shape inside it, exactly as the foundation set lights a stone in its active twins (see any `*-active` in hands/h99/sprite.svg): the outline stays, the light sits inside. Never fill the whole outline, never add new stones, never move a stroke. Light in a meaningful order: the nearest or lowest stone first, the far or top one last; on a path, in walking order; on a stack, bottom up; on a calendar, the day mark then the rings; on a plant, seed then leaves; on a user, the head last. If a drawing has fewer than three stones, produce only as many variants as it has stones (a single pebble gives `-lit-1` only) and say so in NOTES.

## Canvas (strict, same as the set)
viewBox 0 0 24 24; stroke="currentColor" stroke-width 1.75, round caps and joins, fill none on the root; the lit shape is a `<path fill="currentColor" …>` inside 1.5..22.5; paths only (M L C Q A); no transforms, text, masks, filters, no stroke-dasharray. Copy the source symbol's paths verbatim, then append the lit paths.

## Output (exact)
`lit/<your-hand-id>/sprite.svg`: `<svg xmlns=…>` holding one `<symbol id="<name>-lit-N" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">` per variant.
`lit/<your-hand-id>/NOTES.md`, under 20 lines: your hand name, the lighting order you chose per family, and the drawings with fewer than three stones.
Verify: every symbol id matches `^[a-z0-9-]+-lit-[123]$`; every `-lit-N` has a source in lit-sources.svg; the lit paths' coordinates sit inside 1.5..22.5; the file parses (`python3 -I -c "import xml.dom.minidom,sys;xml.dom.minidom.parse(sys.argv[1])" sprite.svg`); each variant's stroke paths are byte-identical to its source's.
