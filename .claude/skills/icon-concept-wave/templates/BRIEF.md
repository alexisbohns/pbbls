# Pebbles icons: the concepts wave (2026-11)

126 concepts, in 8 squads. You draw ONE squad (named in your prompt). For every concept in it you draw **four ideas**, and every idea comes in **three variants**: 12 drawings per concept.

## The four ideas (four different readings, never one drawing jittered)

- **classical**: the icon people expect for this concept; the one most icon sets would draw. Instantly read.
- **bold**: less common, but it makes sense. A different metaphor, viewpoint or crop that a reader still gets.
- **pebbles**: inspired by the mineral and glyph universe of Pebbles, an app where life moments are kept as pebbles: smooth river stones, cairns, strata, veins, pouches of stones, water, a stone carved with a small glyph. The concept told through that world.
- **weird**: outstanding and unexpected; expressive or abstract. Allowed to need a second look. Still must belong to the concept.

## The three variants of each idea (one drawing, three states)

All three keep **every path of the regular exactly as drawn**. A variant changes fills, nothing else (except the mark, below).

- **regular**: strokes only. No fill anywhere.
- **solid**: fill the regular's own **closed** paths, as they are (same `d`, add `fill="currentColor"`), and **keep their stroke** (never `stroke="none"`, never `fill-rule`, never an inset, a shrunk copy or a carved knock-out). The 1.75 stroke around the fill keeps the corners round and the silhouette the size of the regular: a solid that looks sharper or smaller than its regular is wrong.
  - **What to fill:** the main shape that carries the meaning (the body, the container, the stone, the head), always. Also any repeated element (every stone of a row, every node): fill them all. Up to 4 filled paths, or up to 8 when they are repeats of one similar element.
  - **Details inside a filled shape** (a tick, a hole, a text line, a keyhole) stay as separate unfilled strokes drawn inside it: the preview and the icon pipeline knock them out of the fill (a gap of the stroke width), so they stay visible. Never skip a detail because the fill would hide it.
  - **Connectors** (an edge between two nodes, a stem, a beam, a handle) are strokes that reach outside the filled shape: they merge into it. That is how a node graph or a branch works in solid, and it is fine. Keep filled shapes either clearly apart (2 or more units between their edges) or deliberately merged: two filled shapes 0.3 to 1.7 apart fuse into an accidental blob (the checker warns).
- **marked**: the regular plus ONE mark, in one of three ways:
  - (a) fill one existing small closed shape of the regular (a stone, a circle, a head: same `d`, add `fill="currentColor"`);
  - (b) add one filled dot (`fill="currentColor"`) whose centre lies **inside a closed shape** of the regular (a circle, a stone, a box). Its width is 2 to 6 units and under three quarters of its shape (best: between a third and a half), so it reads as a dot in a shape, never as the shape;
  - (c) a **tail dot**: a filled dot 2.5 to 5 units wide placed just after the end of an open line (centre within 3 units of the line's end), the way the shipped checkmark-dot puts its dot after the tick.
  A dot never flies around anywhere else, and is never the pen's tiny dot (smaller than the stroke). The mark must say something: a status, a count, a point of focus.
  Like the solid, a mark **keeps its stroke** (never `stroke="none"`): the package draws a lit mark filled and stroked, so the dot you see is its path plus half a stroke all round. Draw the path that much smaller than the dot you want.
- **skip**: when a solid or a marked makes no sense for this idea (a line-only drawing, a concept with no body to fill), do not force one: write the symbol with the usual root attributes, `data-skip="true"` and no paths, e.g. `<symbol id="flow-bold-solid" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" data-skip="true"></symbol>`. Use it rarely and only when forcing it would be nonsense.

## The line (the DNA)

Pebbles icons are small ink drawings, never geometry: drawn with a pen of constant width (1.75) on a 24-unit square, by a hand that knows the shape but never measures it. **Hand-drawn irregularity is the identity**: no ruler, no compass, no grid. A box is four uneven strokes with soft overlapping corners; a circle is a stone that closes by overlap; **every straight line leans a little** (never exactly horizontal or vertical: the checker rejects it); proportions drift; corners are left open or rounded. Fewest strokes that still name the thing; open-ended; symmetry implied, never exact. Nothing cute, nothing stock, no letters, no text, no logos, no faces.

## Canvas (strict, machine-checked)

viewBox 0 0 24 24; every coordinate (end and control points, an arc's end point) inside 1.5..22.5; paths only, absolute `M L C Q A Z`; no `H V S T`, no relative commands, no transforms, no dashes, no text. Two decimals at most.

## Output (exact)

Your hand id (`h41` or `h43`) and squad are given in your prompt. The squad's concepts are listed in `concepts.json` (field `squad`, field `batch`): draw them batch by batch. One file per batch: `hands/<hand>/<squad>-<batch>.svg` (for example `hands/h41/s3-surface-02.svg`), containing only:

```
<svg xmlns="http://www.w3.org/2000/svg">
  <symbol id="delivery-classical-regular" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="..."/></symbol>
  <symbol id="delivery-classical-solid" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path fill="currentColor" d="(the regular's own closed path, unchanged)"/>(its other paths, unchanged)</symbol>
  <symbol id="delivery-classical-marked" ...>...</symbol>
  ... then -bold-, -pebbles-, -weird-
</svg>
```

Symbol id = `<concept>-<idea>-<variant>`, exactly (concept slug as in `concepts.json`; idea in classical|bold|pebbles|weird; variant in regular|solid|marked). 12 symbols per concept (a skipped solid or marked still counts as its symbol); at most 8 concepts per batch file.

After **each** batch file run, from the wave folder (`packages/icons/waves/2026-11-concepts`):

    python3 -I check.py <hand> <squad>-<batch>

and fix every error in the drawing before starting the next batch. Write each batch file once, in one Write call; do not print drawings anywhere else, do not make previews. When every batch passes, write `hands/<hand>/NOTES-<squad>.md` (at most 10 lines: the concepts you found hardest and why). Nothing else.

## Stay inside the brief

Work only from this brief, `concepts.json` and `EXAMPLES.md` (nine shipped drawings: read it first and extend that hand; do not copy them). Do not read `src/`, `icons.json`, `DNA.md`, any other wave or any other hand's folder; do not look at other hands' files. Do not edit anything but your own files.
