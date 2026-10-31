import type { BlogPost } from "@/lib/blog/types"

/**
 * "Cut from one stone" (#1380): the Home quick actions on iOS, from three grey
 * tiles to one split stone, and the three rounds of exploration behind it.
 * The drawings are the exploration's own mocks, converted to SVG from the
 * boards and the matrix harness, and kept in the `lab-assets` bucket under
 * `blog/cut-from-one-stone/`; the two screenshots of the app stay in `public/`.
 */
export const cutFromOneStone: BlogPost = {
  slug: "cut-from-one-stone",
  documentTitle: "Cut from One Stone",
  title: "Cut from one stone",
  kicker: "Pebbles · behind the scenes",
  standfirst:
    "Three shortcuts on the Home tab took three rounds of drawing: ten boards, a matrix of a hundred and fifty combinations, and forty-five reworks. This is how we went from many variants to one choice.",
  byline: "From the maker of Pebbles",
  publishedOn: "2026-10-31",
  body: [
    {
      kind: "paragraph",
      text: "The Home tab of Pebbles on iPhone opens on a greeting, then three shortcuts: Camera, to keep a moment the instant you take a photo; Quick moment, a date and a name and nothing more; and New pebble, the full flow, where a moment gets its emotion, its people and its weight. Until this week they were three equal grey tiles. Now they are three pieces of one pebble, cut apart by two leaning lines.",
    },
    {
      kind: "figure",
      n: 1,
      file: "01-before-after.jpg",
      alt: "Four strips of three shortcuts. On the left, the old ones: three identical grey tiles with an icon over a label, in paper and in night. On the right, the new ones: three soft rose stones that fit together like the pieces of one broken pebble, in paper and in night.",
      width: 1644,
      height: 594,
      size: "full",
      caption: "Before and after, paper above and night below. Three grey tiles, and one stone split in three.",
    },
    {
      kind: "paragraph",
      text: "It is a small strip, but it is the first thing you can touch when you open the app, and the maintainer was not happy with it. This post retraces how it changed: what we were after, the three rounds of drawing, how each round was set up to produce many variants at once, and how one of them was chosen.",
    },
    { kind: "heading", text: "In short" },
    {
      kind: "paragraph",
      text: "The first round gave the same short brief to ten designers. They came back with thirty directions, but only about four ideas, and all ten recommended the same one. The second round stopped asking for ideas. It split the strip into three choices, the shape, the layout and the colour, and drew every combination: a hundred and fifty of them, live, with switches. The third round took the maintainer's favourites and gave five designers one lens each, to rework the stones until they were as calm as the plain tiles. The default that shipped is a row cut from one rock. Settings has a bento, and plain tiles for anyone who prefers them.",
    },
    { kind: "heading", text: "What was wrong with the tiles" },
    {
      kind: "paragraph",
      text: "The three shortcuts are not equal. Camera is one tap. Quick moment is two fields. New pebble is the whole flow, the thing the app exists for. Three identical grey boxes said the opposite: that they weigh the same, and that none of them matters much. Next to the greeting and the pages below, they read as furniture, not as something to press.",
    },
    {
      kind: "paragraph",
      text: "So the intention was simple to state and hard to draw. The shortcuts should catch the eye against the rest of the page, they should belong to the app's own hand, the drawn stones and the new hand-drawn icons, and they should still look calm on the hundredth morning. And the French labels, the longest of which is “Appareil photo”, must never be cut.",
    },
    { kind: "heading", text: "Round one: ten boards, one brief" },
    {
      kind: "paragraph",
      text: "The first round is the method we had used before for the monthly wrap and the achievements. One short brief, given as is to ten designers working in parallel, each a model with the same tools. Each had to propose three directions of its own, genuinely different from each other and from the tiles, using only the app's colour tokens and its new icons. Each direction was drawn in four phone crops, paper and night, English and French, and one pressed state, with the camera greyed while a photo saves and the strip at the largest text size. Before handing in, each designer rendered its own board, looked at it, and fixed what looked cheap. Each wrote a short note: the three directions, what each one gives up, and the one it would ship.",
    },
    {
      kind: "paragraph",
      text: "Ten boards came back the same evening, thirty directions in all. A critic pass read them before the maintainer did, and its first finding was a tally. All ten designers recommended the same layout: Camera and Quick moment as two small capsules side by side, and New pebble as one full-width bar under them. All ten had also drawn a ledger, three ruled rows with an italic line of explanation. Six drew stones. Three gave the camera a tall tile of its own.",
    },
    {
      kind: "figure",
      n: 2,
      file: "02-converged.svg",
      stored: "bucket",
      alt: "Four versions of the same layout: two outlined capsules for Camera and Quick moment above one wide bar for New pebble. In the first two, by different designers, the bar is filled with ink: black on paper, and a pale cream slab on night. In the last two, the bar is filled with the app's wine colour on paper and its pale pink on night.",
      width: 792,
      height: 424,
      size: "full",
      caption: "Ten designers, one answer: a pair and a lead bar. Filled with neutral ink, the bar turns into a cream slab at night; in the brand colour, it reads as Pebbles.",
    },
    {
      kind: "paragraph",
      text: "Ten hands, about four ideas. That can be read two ways. The pair and the lead bar is probably the obvious right answer for three unequal actions, and ten independent votes for it are worth something. But the round had not explored. The critic recommended the version whose bar used the brand colour rather than black ink, kept a quiet ruled alternative, and flagged the boldest direction of the thirty: no strip at all, just the greeting going on talking, with the three shortcuts as chips inside a sentence.",
    },
    {
      kind: "figure",
      n: 3,
      file: "03-others.svg",
      stored: "bucket",
      alt: "Four other directions from the first round, on paper: a ledger of three ruled rows, each with an italic hint such as “A date and a name”; three round stones growing from Camera to New pebble; a bento with a tall Camera tile beside two short rows; and a sentence, “Catch it with the Camera, jot a Quick moment, or start a New pebble”, with the three actions as chips.",
      width: 792,
      height: 492,
      size: "full",
      caption: "The other ideas of the round: the ledger, stones by weight, the viewfinder bento, and the sentence said aloud.",
    },
    {
      kind: "paragraph",
      text: "The critic ended on a product question rather than a style one. A lead bar makes New pebble the main action on Home. Is it? The maintainer's answer moved the work somewhere else. The lead bar was not it. What they liked was in the margins of the boards: stones of the same size, each a different hand-drawn shape; a colour that makes the shortcuts stand out; and the bento, a square beside two stacked rows. The ruled lists were boring.",
    },
    { kind: "heading", text: "Round two: a matrix instead of boards" },
    {
      kind: "paragraph",
      text: "Asking ten more designers for ideas would have produced the same four. So the second round asked for none. It took what the maintainer had liked and turned it into three independent choices: how a shortcut is drawn, how the three are laid out, and how they are coloured. Then it drew every combination.",
    },
    {
      kind: "paragraph",
      text: "Five displays: filled stones, carved stones with the icon cut out of a darker core, rings drawn with the icons' own pen, rounded squares and capsules. Five layouts: a row of three, a bento, a pair with a lead, a riverbed where the stones sit at slightly different heights, and a cairn, a small pile. Six colourings: monochrome, a soft wash of the brand colour, the brand colour itself, soft with New pebble solid, an outline only, and three colour families, one per shortcut. Five times five times six is a hundred and fifty strips, each drawn in paper and in night.",
    },
    {
      kind: "figure",
      n: 4,
      file: "04-displays.svg",
      stored: "bucket",
      alt: "Six rows of three shortcuts on paper: rounded grey squares close to the old tiles, then in soft rose: uneven filled stones, stones with a darker carved core, stones drawn as wine-coloured rings, rounded squares, and capsules.",
      width: 788,
      height: 489,
      size: "full",
      caption: "The five displays, as a row in the soft wash, beside a monochrome square close to the old tiles.",
    },
    {
      kind: "paragraph",
      text: "Two designers split the work by family. One wrote a single renderer for the three stone displays, the other one for the squares and the capsules. Neither drew a picture. Each wrote a small program that takes a combination and draws the strip, so the gallery could call it a hundred and fifty times, and again every time a switch moved. Both had to implement all five layouts with the same meaning, and every colour went through one shared table, so a soft square and a soft stone were the same soft.",
    },
    {
      kind: "figure",
      n: 5,
      file: "05-layouts.svg",
      stored: "bucket",
      alt: "The soft rose stones in five layouts: a row of three, a pair above one wide stone, a bento with a large Camera stone beside two smaller ones, a riverbed with the middle stone set lower, and a cairn with the three stones stacked and offset and their names beside them. A sixth panel shows the bento as solid black carved stones.",
      width: 788,
      height: 693,
      size: "full",
      caption: "The five layouts, with the stones in the soft wash, and one of the critic's picks: carved stones in a bento, in plain ink.",
    },
    {
      kind: "paragraph",
      text: "The page had switches on top of the three axes: the label below the icon, beside it or hidden; the icon at three sizes; an edge that could be none, a hairline, or a line drawn at the icons' own weight. Pressing one redrew all hundred and fifty strips at once. Reviewing became a matter of moving through the grid rather than reading boards, and the critic's shortlist was a row of buttons that narrowed the grid to one combination.",
    },
    {
      kind: "figure",
      n: 6,
      file: "06-tints.svg",
      stored: "bucket",
      alt: "The same bento of rounded squares in six colourings: grey with black icons; a soft rose wash with wine icons; solid wine with white icons; soft rose with New pebble in solid wine; wine outlines on the page; and three families, green for Camera, sand for Quick moment and rose for New pebble.",
      width: 788,
      height: 705,
      size: "full",
      caption: "One bento in the six colourings: monochrome, soft, solid, soft with a solid lead, outline, and three families.",
    },
    {
      kind: "paragraph",
      text: "Hand-drawn shapes raised a practical question: how do you review a random shape? Every irregularity in the stones, each corner, lean and wave, came from a random generator seeded with the name of the shortcut. The same seed always draws the same stone, in every layout and every colour, so a stone could be judged, kept and named. A last switch, the stone of the day, changed the seed by date, to see whether the shapes held up when they changed every morning.",
    },
    {
      kind: "figure",
      n: 7,
      file: "07-stone-of-the-day.svg",
      stored: "bucket",
      alt: "The row of three soft stones six times: the fixed stones, then the stones of day one, day two and day three, each set slightly different in their corners and lean, and the fixed and day-three stones again on night.",
      width: 788,
      height: 465,
      size: "full",
      caption: "The stone of the day. Same size, same place, a different hand every morning; the fixed stones are the ones that shipped.",
    },
    {
      kind: "paragraph",
      text: "The maintainer went through the grid and came back with two favourites, both rounded squares in the soft wash: the row of three and the bento. The square tiles were calm where the stones were not, and they wanted the stones brought up to that standard. They added one rule for the bento. Its square should not always go to Camera, but to whichever shortcut you used last.",
    },
    { kind: "heading", text: "Round three: five designers, five lenses" },
    {
      kind: "paragraph",
      text: "The brief for the third round was narrow: make the stones as harmonious as the squares, in three places, the row of stones, the bento of stones and the bento of rings. Harmonious meant one family of shapes with the same weight and rhythm, aligned edges and baselines, icons centred to the eye, labels on one line, and still unmistakably hand-drawn. Each designer made three variants of each pick, and the gallery drew every variant beside the squares it had to live up to, in paper and night, in English and French, with the bento's lead rotating through all three shortcuts.",
    },
    {
      kind: "paragraph",
      text: "The first round had taught us what happens when the same brief goes to many hands of the same model. So this time each of the five designers got a different lens. One worked on the shape language, one on the grid and the rhythm, one on the pen, one on the type and the labels, and one on light within the colour. Forty-five variants came back, and for once they did not look alike.",
    },
    {
      kind: "figure",
      n: 8,
      file: "08-rows.svg",
      stored: "bucket",
      alt: "Six rows of three soft shortcuts: the rounded squares, then five stone versions: one wide stone split in three by two leaning cuts; stones edged with a translucent pen that darkens where it overlaps; stones with French names hung from a shared icon line; stones filling the squares' cells; and smaller flat stones with the names below on the page.",
      width: 788,
      height: 492,
      size: "full",
      caption: "The row: the squares to match, then the shape, pen, type, grid and tone lenses. “One rock, three” is the one that shipped.",
    },
    {
      kind: "paragraph",
      text: "In the row, the shape lens found the idea that won. Instead of three stones made to look like siblings, it drew one wide stone and split it with two parallel leaning cuts, so the pieces fit back together. The pen lens edged each stone with a translucent line that runs past its start and darkens where it overlaps, as the icons do. The type lens hung the icons from one shared line, so a French name on two lines grows down and never pushes its icon up, the one place where it beat the squares.",
    },
    {
      kind: "figure",
      n: 9,
      file: "09-bentos.svg",
      stored: "bucket",
      alt: "Six soft bentos: the rounded squares with Camera always in the large square; the same stones three times with an ink line drawn only around the large stone, which goes to Camera, then Quick moment, then New pebble; one stone cut along straight gutters into a large piece and two smaller ones; and a rock cut by a leaning vertical gutter.",
      width: 788,
      height: 701,
      size: "full",
      caption: "The bento, where the square goes to the shortcut used last. “Inked last used” marks it with the pen; “One stone, split” became the shipped bento.",
    },
    {
      kind: "paragraph",
      text: "The bento had its own problem to solve: the large square changes hands. The pen lens drew three quiet washes and gave only the last-used one a hand-drawn ink line, so the lead is marked by the pen rather than by size or colour. The grid lens cut one stone along the gutters, so the bento reads as a single object whichever shortcut leads. The rings were the boldest, one with the name set into a gap in the line, and the hardest to keep calm at this size.",
    },
    {
      kind: "figure",
      n: 10,
      file: "10-rings.svg",
      stored: "bucket",
      alt: "Four bentos on night drawn as pink rings around a dark wash: rings drawn in one stroke that closes by overlap; rings with the name set in a gap in the line; a lead ring in pink and two paler rings; and one stone split into three ringed pieces.",
      width: 788,
      height: 453,
      size: "full",
      caption: "The rings, on night. Each is a single stroke at the icons' weight that closes by overlapping where it began.",
    },
    {
      kind: "paragraph",
      text: "The critic's verdict was honest about what had happened. Harmony had mostly been reached by moving the stones toward the squares: column-wide, one corner scale, no tilt. Many variants were now hard to tell from the favourite at a glance. The ones worth keeping stayed calm and still kept one hand-drawn idea each. The weak ones shrank the stones to medallions with the names on the page, and lost both the tap area and the point.",
    },
    { kind: "heading", text: "The choice" },
    {
      kind: "paragraph",
      text: "The maintainer chose the split rock. The default on Home is a row cut from one stone, the shape lens's “One rock, three”. Settings has a new Home section with a switch between the row and a bento, and the bento is the grid lens's “One stone, split”, with its outer corners softened to about half the radius of the mock. A second switch turns the pebble shapes off, for plain rounded tiles in both layouts. The bento's square goes to the shortcut you used last, and the choice stays on the device.",
    },
    {
      kind: "figure",
      n: 11,
      file: "11-four-styles.jpg",
      alt: "Four strips of shortcuts as the iPhone app draws them, on paper: the row cut from one stone; the stone bento with Quick moment in the large piece; a row of plain rounded tiles; and a plain bento with New pebble in the large tile.",
      width: 1644,
      height: 856,
      size: "full",
      caption: "The four styles in the app: the stone row, which is the default, the stone bento, and the same two as plain tiles.",
    },
    {
      kind: "paragraph",
      text: "The soft wash won over the solid colour. The shortcuts catch the eye because they are the only coloured shapes near the top of the page, not because they shout. And the lead bar, the answer all ten designers gave in the first round, is not in the app. Nothing in the strip says New pebble matters more than the other two. The bento's square follows what you do instead.",
    },
    { kind: "heading", text: "From the mock to the app" },
    {
      kind: "paragraph",
      text: "The stones on iPhone are not a redrawing of the mock. The small piece of code that drew them in the gallery, its seeded random generator included, was ported to Swift, and the port draws the same numbers bit for bit. A test checks it against values from the gallery, so the shapes in the app are the ones that were reviewed, and checks that each piece stays in its cell with its gutters open.",
    },
    {
      kind: "paragraph",
      text: "One thing did not carry over, and it was a mistake on our side. The gallery's colours had been read from the app's high-contrast palette, a little deeper than the one most people see. The app uses the standard one: the brand colour for the icons and the labels, over a wash of the same colour at 13 % on paper and 20 % at night. High contrast now darkens it on its own. And the icons keep growing with your text size, so the tiles grow with them instead of letting the labels overlap.",
    },
    { kind: "heading", text: "What the method taught us" },
    {
      kind: "paragraph",
      text: "Many variants are cheap now. Variety is not. The same brief given to ten hands of the same model came back as ten copies of the safe answer, with a few good ideas in the margins. That is useful as a vote on the obvious, and it is where the maintainer's taste showed up, but it is not an exploration.",
    },
    {
      kind: "paragraph",
      text: "Once the taste was known, axes did better than ideas. Drawing every combination of a few choices let the maintainer compare by moving a switch, not by reading boards, and the favourite was one nobody had recommended. When ideas were needed again, a different lens for each designer kept them from converging. And seeding the randomness made a hand-drawn shape something that can be reviewed, kept and ported, rather than a picture that has to be redrawn.",
    },
    {
      kind: "paragraph",
      text: "A critic pass between the designers and the maintainer did its job each time, sorting, naming what was weak, and asking the one question that mattered. But the choice was never the critic's. Each round ended with the maintainer picking something from the margins.",
    },
    { kind: "heading", text: "What's left" },
    {
      kind: "paragraph",
      text: "The new strip is on iPhone, and passed its check on a real phone the day it was merged. Android is next, as a mirror of the same four styles and the same switches. The stone of the day stayed in the gallery: for now, each shortcut keeps the stone it was given.",
    },
  ],
}
