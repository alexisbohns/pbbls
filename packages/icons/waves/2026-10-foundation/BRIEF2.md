# Pebbles icon set — one hand, the whole set (round 2)

You are one of ten hands. Draw the complete set below in a single consistent hand: one pen, one vocabulary, yours. You are judged on coherence, taste and *originality* at 24 pt in a menu row. Round 1 failed because all ten hands drew the same tent, the same winding path, the same two faceless figures and the same bowl of stones. Do not draw those. Any icon that could have come from Lucide, SF Symbols or a stock set fails the round.

## DNA
Pebbles icons are small ink drawings, not geometry. Each is drawn in one sitting with a pen of constant width on a 24-unit square by a hand that knows the shape but never measures it: round things are stones, never compasses; straight lines lean a degree or two and swell slightly; corners are rounded by the pen or left open where two strokes almost meet. An icon is the fewest strokes that still name the thing, two to five, open-ended, with no fills, so the paper shows through. Symmetry is implied, never exact. The vocabulary is neo-primitive: marks people made before alphabets, on bark, bone, clay and stone. Which marks is your choice; invent, don't recall. Nothing is cute or pictographic; the line is calm and a little weighty. An active icon is not a filled icon: it is the same drawing after something happened inside it. What happens is yours to decide, and it should be the same kind of event across the set.

Before drawing, write three lines in your NOTES: the world your hand draws from (a material, a place, a people, a ritual, anything that is not "stones and tents"), the event that makes an icon active, and the one rule of your line. Then draw every icon from that world.

## Canvas (strict)
- viewBox 0 0 24 24. Keep every stroke inside 1.5..22.5.
- stroke="currentColor", stroke-width 1.75, stroke-linecap round, stroke-linejoin round, fill none.
- The only allowed fill is `fill="currentColor"` on a small shape that is *lit* in an active variant. Never fill the whole icon.
- Paths only (M, L, C, Q, A). No text, no transforms, no masks, no filters, no stroke-dasharray except on `glyphs`.
- Hand-drawn means: slight wobble, uneven curves, a line that overshoots or stops short. Never messy. Draw badly rather than draw safely.

## The icons (meaning only; the subject is yours)
One brand anchor: `plus-circle` and `check-circle` sit inside a stone-shaped ring (an irregular rounded blob, wider than tall), because that ring is the pebble in the product. Everything else is open, including `pebbles` and `glyphs`.

1. checkmark — done, yes
2. check-circle — done, inside the stone ring
3. plus-circle — create a pebble, inside the stone ring
4. hourglass — waiting, something not yet ready (need not be an hourglass)
5. camera — take a photo now
6. home — where you land (active variant)
7. path — your months in order, the walk through time (active variant)
8. people — the souls in your life (active variant)
9. collections — your pebbles gathered into sets (active variant)
10. stack — a collection mode: a loose pile, no order
11. pack — a collection mode: a bound set that travels together
12. track — a collection mode: an ordered sequence
13. user — you, your own account (active variant)
14. settings — adjust things
15. glyphs — the little marks a user scribbles on a stone
16. pebbles — a recorded moment, the unit of the app
17. karma — good you gave, that comes back
18. calendar — a date, a day
19. trash — remove
20. success — it worked, celebrate quietly
21. idea — a thought, a suggestion (not a lightbulb, not a sparkle, not a sprout)
22. like — I liked this (active variant; not a heart)
23. stepper — a vertical two-node stepper for ordered steps

Active variants: home, path, people, collections, user, like.

## Output (exact)
ONE file `hands/<your-hand-id>/sprite.svg`:
```
<svg xmlns="http://www.w3.org/2000/svg">
  <symbol id="checkmark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">...</symbol>
  ...
</svg>
```
Ids: the 23 names above exactly, plus `home-active`, `path-active`, `people-active`, `collections-active`, `user-active`, `like-active`. 29 symbols.

ONE file `hands/<your-hand-id>/NOTES.md`, at most 14 lines: the hand's name (two words), the three lines above, then the subject of each tab icon and its active twin.

Verify before finishing: `grep -c '<symbol' sprite.svg` prints 29; every coordinate sits inside 1.5..22.5; the file parses (`python3 -I -c "import xml.dom.minidom,sys;xml.dom.minidom.parse(sys.argv[1])" sprite.svg`). Nothing else.
