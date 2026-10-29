# Pebbles icon set — round 3, one line, the chosen subjects

You are one of three hands. Round 1 and 2 are over; a blind game picked a subject per icon. Your job is to redraw the whole set in ONE line so the winners from ten different hands become one set. You are judged on coherence first, then on how well each subject survives the move into the line.

## The line (read these two sprites first)
- hands/h03/sprite.svg is the line: Riverbed Reed. Every round thing is the same uneven stone drawn in three or four strokes that overlap where the stroke began; straight lines lean; corners are left open; nothing is a compass circle. Keep its weight and its overshoot. Do not copy its subjects where another hand won.
- hands/h00/sprite.svg is the composite: the winning subject per icon, stitched from ten hands. Redraw each subject in the Riverbed line. Where the composite's drawing is already h03, improve it rather than copy it.

## DNA
Pebbles icons are small ink drawings, not geometry, drawn with a pen of constant width on a 24-unit square by a hand that knows the shape but never measures it. Fewest strokes that still name the thing, open-ended, no fills. Symmetry implied, never exact. Nothing cute or stock. An active icon is the same drawing after something happened inside it: a stone lights. That is the one active rule for this set.

## Canvas (strict)
viewBox 0 0 24 24; every stroke inside 1.5..22.5; stroke="currentColor" stroke-width 1.75, round caps and joins, fill none; the only fill is `fill="currentColor"` on the small lit shape in an active variant; paths only (M L C Q A); no transforms, text, masks, filters; stroke-dasharray only on `glyphs`.

## The subjects (per icon: the hand whose subject won; alternates the maintainer liked in all three contexts, for you to consider, not to merge)
- trash: subject from h17 (Ledge Wax)
- path: subject from h04 (Slate Scratch); liked alternates: h14 Knot Cord
- stepper: subject from h04 (Slate Scratch)
- calendar: subject from h16 (Ash Bracket); liked alternates: h14 Knot Cord, h20 Pearl Jaw
- like: subject from h03 (Riverbed Reed); liked alternates: h12 Cord Keeper, h13 Knot Keeper, h10 Lifted Pen
- people: subject from h03 (Riverbed Reed); liked alternates: h18 Rawhide Beater, h13 Knot Keeper, h19 Couched Wool, h20 Pearl Jaw
- checkmark: subject from h18 (Rawhide Beater)
- home: subject from h03 (Riverbed Reed); liked alternates: h14 Knot Cord, h16 Ash Bracket
- user: subject from h19 (Couched Wool); liked alternates: h11 Knot Keeper, h13 Knot Keeper
- pack: subject from h08 (Creek Reed); liked alternates: h09 Cairn Ink, h06 Cairn Reed, h03 Riverbed Reed
- collections: subject from h08 (Creek Reed); liked alternates: h14 Knot Cord, h13 Knot Keeper
- track: subject from h01 (River Slate); liked alternates: h07 Reed Scratch, h16 Ash Bracket, h13 Knot Keeper, h17 Ledge Wax
- plus-circle: subject from h18 (Rawhide Beater)
- success: subject from h05 (Ember Reed); liked alternates: h02 Bark Reed, h18 Rawhide Beater, h01 River Slate, h13 Knot Keeper, h12 Cord Keeper
- idea: subject from h04 (Slate Scratch); liked alternates: h08 Creek Reed, h20 Pearl Jaw, h03 Riverbed Reed, h17 Ledge Wax, h19 Couched Wool
- hourglass: subject from h03 (Riverbed Reed); liked alternates: h11 Knot Keeper, h14 Knot Cord, h19 Couched Wool
- karma: subject from h18 (Rawhide Beater); liked alternates: h20 Pearl Jaw, h19 Couched Wool, h05 Ember Reed
- stack: subject from h07 (Reed Scratch); liked alternates: h13 Knot Keeper, h18 Rawhide Beater
- camera: subject from h18 (Rawhide Beater); liked alternates: h15 Cord Keeper
- check-circle: subject from h04 (Slate Scratch)
- settings: subject from h08 (Creek Reed); liked alternates: h19 Couched Wool, h09 Cairn Ink, h20 Pearl Jaw, h15 Cord Keeper
- pebbles: subject from h04 (Slate Scratch)
- glyphs: subject from h03 (Riverbed Reed); liked alternates: h20 Pearl Jaw, h12 Cord Keeper, h17 Ledge Wax

Active twins for home, path, people, collections, user, like: the composite shows the winning twin; keep its event, redraw it in the line.

## Output (exact)
`hands/<your-hand-id>/sprite.svg` with 29 `<symbol id=…>` (the 23 names + home-active, path-active, people-active, collections-active, user-active, like-active), same attributes as the brief above; `hands/<your-hand-id>/NOTES.md` under 12 lines: your hand name (two words), what you changed from the composite and why, the one icon you are least sure of.
Verify: `grep -c '<symbol' sprite.svg` → 29; all coordinates in 1.5..22.5; parses with `python3 -I -c "import xml.dom.minidom,sys;xml.dom.minidom.parse(sys.argv[1])" sprite.svg`.
