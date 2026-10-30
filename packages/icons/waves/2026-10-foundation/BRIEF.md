# Pebbles icon set — one hand, the whole set

You are one of ten hands. Draw the complete set below in a single consistent hand. One hand, one pen, one vocabulary. You are judged on coherence and taste at 24 pt in a menu row, not on a poster.

## DNA
Pebbles icons are small ink drawings, not geometry. Each is drawn in one sitting with a pen of constant width on a 24-unit square by a hand that knows the shape but never measures it: circles are stones, never compasses; straight lines lean a degree or two and swell slightly; corners are rounded by the pen or left open where two strokes almost meet. An icon is the fewest strokes that still name the thing, two to five, open-ended, with no fills, so the paper shows through. Symmetry is implied, never exact. The vocabulary is neo-primitive, things people scratched on bark and stone before alphabets: a tent, a fire, a path, a sun, a figure without a face, a pile of stones, a knotted cord. Nothing is cute or pictographic; the line is calm and a little weighty, like the stone outlines of the brand. An active icon is not a filled icon: it is the same drawing after something happened inside it, a lit stone, a fire in the tent, sand that has run through the glass.

## Canvas (strict)
- viewBox 0 0 24 24. Keep every stroke inside 1.5..22.5.
- stroke="currentColor", stroke-width 1.75, stroke-linecap round, stroke-linejoin round, fill none.
- The only allowed fill is `fill="currentColor"` on a small shape that is *lit* in an active variant (a flame, a lit stone, a dot). Never fill the whole icon.
- Paths only (M, L, C, Q, A). No text, no transforms, no masks, no filters, no stroke-dasharray except on `glyphs`.
- Hand-drawn means: slight wobble, uneven curves, a line that overshoots or stops short. It never means messy or scribbled. Draw badly rather than draw safely: a Lucide clone fails the round.

## The icons (meaning only; you choose the subject)
Brand anchors you must keep: `plus-circle` and `check-circle` are a stone-shaped ring (an irregular rounded blob, wider than tall, like a pebble seen from above), not a circle. `pebbles` is a stone. `glyphs` is a dashed stone-ring with a small scribbled mark inside.

1. checkmark — done, yes
2. check-circle — done, inside a stone ring
3. plus-circle — create a pebble, inside a stone ring
4. hourglass — waiting, something not yet ready
5. camera — take a photo now
6. home — where you land (active variant)
7. path — your months in order, the walk through time (active variant)
8. people — the souls in your life (active variant)
9. collections — your pebbles gathered into sets (active variant)
10. stack — a collection mode: a loose pile, no order
11. pack — a collection mode: a bound set that travels together
12. track — a collection mode: an ordered sequence, one after another
13. user — you, your own account (active variant)
14. settings — adjust things
15. glyphs — the little marks a user scribbles on a stone
16. pebbles — a stone, the unit of the app
17. karma — good you gave, that comes back
18. calendar — a date, a day
19. trash — remove
20. success — it worked, celebrate quietly
21. idea — a thought, a suggestion (not a lightbulb)
22. like — I liked this (active variant)
23. stepper — a vertical two-dot stepper: a rail with two nodes, used for ordered steps

Active variants: home, path, people, collections, user, like. Same drawing, something happened inside it. Think before drawing: what would make this tent, this path, this figure *alive*?

## Output (exact)
Write ONE file: `hands/<your-hand-id>/sprite.svg`, containing only:

```
<svg xmlns="http://www.w3.org/2000/svg">
  <symbol id="checkmark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">...</symbol>
  ...
  <symbol id="home-active" ...>...</symbol>
</svg>
```

Ids: the 23 names above exactly, plus `home-active`, `path-active`, `people-active`, `collections-active`, `user-active`, `like-active`. 29 symbols.

And ONE file `hands/<your-hand-id>/NOTES.md`, at most 12 lines: the name of your hand (two words), the subject you chose for each tab icon and its active twin, and one sentence on what the hand owns.

Verify before finishing: `grep -c '<symbol' sprite.svg` prints 29; every path's coordinates sit inside 1.5..22.5; the file parses (`python3 -I -c "import xml.dom.minidom,sys;xml.dom.minidom.parse(sys.argv[1])" sprite.svg`). Nothing else: no HTML, no preview page, no README.
