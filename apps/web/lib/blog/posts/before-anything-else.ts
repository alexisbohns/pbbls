import type { BlogPost } from "@/lib/blog/types"

/**
 * "Before anything else" (#1511): the onboarding exploration, from a list of
 * questions to a seven-step flow. The screens are crops of the rounds' own
 * mocks, rendered in headless Chrome and kept as JPEGs in `public/`; the three
 * drawings (the progress row, the six tiles, the shelf) are shape-only SVG
 * kept in the `lab-assets` bucket under `blog/before-anything-else/`.
 */
export const beforeAnythingElse: BlogPost = {
  slug: "before-anything-else",
  documentTitle: "Before Anything Else",
  title: "Before anything else",
  kicker: "Pebbles · behind the scenes",
  standfirst:
    "What should Pebbles ask you before your first pebble? We started from eighteen questions and kept seven steps. On the way: one rule to sort them, three hands drawing a hundred and sixty-two screens, and two rounds to make the flow look like the app.",
  byline: "From the maker of Pebbles",
  publishedOn: "2026-11-04",
  body: [
    {
      kind: "paragraph",
      text: "Today, a new account in Pebbles goes through a short welcome and a consent screen, then lands on an empty Path. It works, but the app knows nothing about you when you arrive, and it says the same things to everyone. This post retraces how we designed what comes before your first pebble, and why most of the questions we wanted to ask did not make it.",
    },
    {
      kind: "figure",
      n: 1,
      file: "01-final-first-three.jpg",
      alt: "Three phone screens on cream paper. The first is a letter: “Before anything else, a promise”, a line in italics saying there will never be a feed here, two short paragraphs and a signature, with a wine button “I'll hold you to it”. The second, “Your consent”, has four rows with a switch each, three on and one off, and a greyed Continue button. The third, “What brings you here?”, shows six tiles in two columns, the first two lit in pink.",
      width: 1844,
      height: 1290,
      size: "full",
      caption: "The first three steps of the new flow: a promise, the consent, and why you came. The flow is decided; it is not in the apps yet.",
    },
    { kind: "heading", text: "In short" },
    {
      kind: "paragraph",
      text: "We listed every question Pebbles could ask a newcomer, then kept only the ones whose answer changes the app for that person. Three designers drew the eighteen candidates, three takes each, in loose ink. The maintainer picked seven steps from them. A second round redrew those seven in the app's own colours and type, two variants per screen. A third refined each choice three ways. The result is a promise, a consent, a reason, a name and a glyph, three people, a pace, and then the regular composer for your first pebble.",
    },
    { kind: "heading", text: "Why ask anything at all" },
    {
      kind: "paragraph",
      text: "It started with Duolingo. Before the first lesson, it asks you a few questions: which language, why you are learning it, how much you already know, how many minutes a day. Every answer changes the course you get. The maintainer wondered whether Pebbles could do the same, and wrote down what it might ask. Have you kept a journal before? How do you feel about Instagram? Do you want a glyph? Would you like a note from the person who makes the app? The list grew to eighteen.",
    },
    {
      kind: "paragraph",
      text: "Duolingo's questions work because each one has a job. A list of eighteen did not, yet. Some were questions, some were promises, some were legal acts, some were features looking for a door. We needed a way to tell them apart before anyone drew a screen.",
    },
    { kind: "heading", text: "One test" },
    {
      kind: "paragraph",
      text: "The test we kept is simple: a question is asked only if its answer changes the product for this person. If nothing downstream reads the answer, the question is a form, and the person filling it in can feel it.",
    },
    {
      kind: "paragraph",
      text: [
        "To say what “changes the product” means, we borrowed a list: Bain's elements of value, the pyramid of things people pay for, from saving time to self-transcendence. We used it to name what Pebbles gives. Five came out: ",
        { em: "therapeutic value" },
        ", in noticing how you feel; ",
        { em: "belonging" },
        ", in keeping the people who matter; ",
        { em: "nostalgia" },
        ", in remembering the good days; ",
        { em: "hope" },
        ", in getting through a hard season; and ",
        { em: "heirloom" },
        ", in a record you keep for later. A question earned its place if its answer told the app which of these to lead with.",
      ],
    },
    {
      kind: "paragraph",
      text: "Some questions passed easily. Why you came picks the value. Your first people give Pebbles someone to put in your first pebble. The pace you want sets the look-back the app promises. Others failed outright. How you feel about Instagram, and how you heard about Pebbles, are research questions: interesting to us, useless to you, and nothing in the app would read them. We drew them anyway, once, to be sure.",
    },
    { kind: "heading", text: "Round one: three hands, loose ink" },
    {
      kind: "paragraph",
      text: "Three designers, each a model working alone, got the same brief: the eighteen candidates in no particular order, and three different takes on each, drawn in the house line, ink on paper, with at most one lit stone per screen. Fifty-four screens per hand, a hundred and sixty-two in all. No colours, no system, no app chrome: we wanted ideas, not polish.",
    },
    {
      kind: "paragraph",
      text: "The hands agreed far more than we expected. For most steps, all three found nearly the same three takes: a plain list, a set of stones to light, and a sentence to finish. Once three people independently draw the same three screens, the question stops being which idea, and becomes which shape suits this step.",
    },
    {
      kind: "figure",
      n: 2,
      file: "02-three-takes.jpg",
      alt: "Three ink-on-paper phone screens for “What brings you here?”. The first is a list of five reasons with a small drawn icon each and one lit amber stone. The second scatters five hand-drawn stones with the reasons written inside, one filled in amber. The third reads “I'm here to keep the people who matter”, the end underlined, over five pill-shaped choices.",
      width: 1856,
      height: 1294,
      size: "full",
      caption: "One hand's three takes on why you came: a list, stones to light, a sentence to finish. The other two hands drew the same three.",
    },
    { kind: "subheading", text: "The questions that turned into pitches" },
    {
      kind: "paragraph",
      text: "The first surprise was in the research questions. Asked to draw “How do you feel about Instagram?”, every hand slid, take after take, towards a statement about Pebbles. “Where should your memories live?” “My best moments were never for an audience.” “Pebbles is the opposite of a feed.” The question about journals did the same: its best line was “Most journals stop in week three. Pebbles asks for ten seconds, not a page.”",
    },
    {
      kind: "figure",
      n: 3,
      file: "03-pitches.jpg",
      alt: "Five ink-on-paper phone screens about Instagram. A scale from “It holds my memories” to “I keep them elsewhere” over a quoted line. A crowd of small figures beside one amber stone, asking where your memories belong. The same idea with “Out there, for many eyes” and “In here, for yours”. A list of three stances above a note saying Pebbles is the opposite of a feed. A quote in large italics, “My best moments were never for an audience”, with three answer buttons.",
      width: 2976,
      height: 1278,
      size: "full",
      caption: "Five of the nine Instagram screens. Each one asks a question and answers it for you.",
    },
    {
      kind: "paragraph",
      text: "Those lines are good. They are also pitches, not questions: the answer is already written, and nothing would change if you picked another one. That confirmed the test. The research questions left the flow. The best of their lines belong on the welcome screens, where saying what Pebbles is is the whole point.",
    },
    { kind: "subheading", text: "Who you would call" },
    {
      kind: "paragraph",
      text: "The second surprise was a question replacing a form. “Your first people” asked for one to three names, and most takes drew three empty lines. One take asked instead: you get great news, who do you tell first? You do not have to think about who counts as a person in your life. A name simply comes.",
    },
    {
      kind: "figure",
      n: 4,
      file: "04-good-news.jpg",
      alt: "An ink-on-paper phone screen. In italics at the top: “You get great news. Who do you tell first?”. Below, the name Maya on a line, then two drawn figures linked by a dotted line through a small blue stone, and the note “Maya is your first soul”. A dark button reads “Add Maya”.",
      width: 688,
      height: 1294,
      size: "narrow",
      caption: "The good-news question. It survived every round, as the line under “Three places”.",
    },
    {
      kind: "paragraph",
      text: "From the hundred and sixty-two screens, the maintainer picked seven steps and their order: a promise, the consent, why you came, a name and a glyph, your first people, a pace, and your first pebble. A few good screens were set aside for later, waiting on something else. The rest were dropped, each with a reason written down.",
    },
    { kind: "heading", text: "Round two: in the app's own clothes" },
    {
      kind: "paragraph",
      text: "Loose ink is good for ideas and bad for judging whether a screen belongs in the app. So one designer redrew the seven steps in the app's own scheme: its paper, its wine colour, its two faces, its real icons, its switches and buttons, next to renders of the screens that ship today. Two variants per step, the keyboard up wherever you type, and the small screens each step opens: drawing your own glyph, picking a glyph for a person, adding a name. One progress row, one Back, one button style throughout.",
    },
    {
      kind: "paragraph",
      text: "The consent was the one step with a real fork. One shape gathers three clauses over a single tap. The other gives each act its own switch: storing how you feel, the terms, the privacy policy, and confirming your age. The first is shorter. The second says exactly what you agree to, and lets you see the one you have not turned on yet.",
    },
    {
      kind: "figure",
      n: 5,
      file: "05-consent-shapes.jpg",
      alt: "Two phone screens in the app's cream and wine. On the left, “Before your first pebble”: three ticked clauses in one card and a wine button, “I give my consent”. On the right, “Your consent”: four rows with a switch each, three on and the age row off, a line “Turn on all four to carry on”, and a greyed Continue.",
      width: 1424,
      height: 1464,
      size: "mid",
      caption: "The two consent shapes of round two: one act, or one switch per act. The maintainer picked the switches.",
    },
    {
      kind: "paragraph",
      text: "The maintainer chose screen by screen, and rejected two outright. The hand-off to the first pebble, in both variants, added a screen that only said what was coming next. The sheet for adding a person broke away from the shelf it was supposed to fill. And the pace, drawn as four radio rows, read like a settings page: the maintainer asked for it to look like one composed object, not a list of options.",
    },
    { kind: "heading", text: "Round three: three ways inside each choice" },
    {
      kind: "paragraph",
      text: "The third round did not reopen any decision. For each screen, the designer drew three refinements of the chosen variant: the typography and signature of the promise, how a missing consent reads, how the tiles light, how the name and the glyph grid share the screen with the keyboard up. A few fixes went everywhere: the progress row became the app's drawn stones, people are always named rather than given a pronoun, and the people step offers “Later” rather than “Skip”, because you may want to think about it.",
    },
    {
      kind: "paragraph",
      text: "The pace got the most attention. Three shapes for the same four choices: a vertical group of large, connected surfaces with one lit; four quarters of a square, each showing a week as it might look; and a preview card above a row of four.",
    },
    {
      kind: "figure",
      n: 6,
      file: "06-paces.jpg",
      alt: "Three versions of “What pace suits you?”. First: four stacked, rounded surfaces, the second one filled in wine with “A few times a week”. Second: four squares in a grid, each with a row of seven small stones, the lit one in pink. Third: a large card reading “A few times a week” over a week of seven stones, three lit, then a row of four smaller choices.",
      width: 1844,
      height: 1290,
      size: "full",
      caption: "Three shapes for the four paces. The connected group on the left won.",
    },
    { kind: "heading", text: "The flow" },
    {
      kind: "figure",
      n: 7,
      file: "07-final-flow.jpg",
      alt: "Six phone screens in a row: the promise letter, the consent switches, the six reason tiles, “This is you” with the name Lena over a grid of glyphs, “Three places” with two named stones on a shelf and a dashed empty one, and “What pace suits you?” with four connected choices, the second lit. A row of seven small stones at the top of each screen advances by one.",
      width: 3528,
      height: 1274,
      size: "full",
      caption: "The final flow, step by step. After the pace, the regular composer opens for your first pebble.",
    },
    {
      kind: "paragraph",
      text: "It opens on a promise, before any legal text: there will never be a feed here, nothing you keep becomes an ad or trains a machine, and if you leave, your pebbles leave with you. The maintainer can reword it without shipping a new version of the app, so the promise can be kept honest as things change.",
    },
    {
      kind: "paragraph",
      text: "Then a row of seven stones at the top of each screen, the current one lit and a little larger, the ones behind you filled in.",
    },
    {
      kind: "figure",
      n: 8,
      file: "08-progress-stones.svg",
      stored: "bucket",
      alt: "Seven rows of seven small stones, on paper and on night. In each row, the stones before the current one are filled in ink, the current one is larger and lit in the brand colour, and the rest are open outlines. The lit stone moves one place to the right on every row.",
      width: 744,
      height: 380,
      size: "mid",
      caption: "The progress row, one line per step.",
    },
    {
      kind: "paragraph",
      text: "The consent comes next, one switch per act. Like the promise, it cannot be skipped. Then why you came, as six equal tiles you can light in any number, because more than one reason can be true on the same day. Five reasons came from the first round; a sixth, keeping a record for later, joined at the end.",
    },
    {
      kind: "figure",
      n: 9,
      file: "09-six-tiles.svg",
      stored: "bucket",
      alt: "Six rounded tiles in two columns, on paper and on night. The top two are filled in the soft brand colour, each with a lit stone in its corner. The other four are plain, with an open stone in the corner. Each tile has a small mark at the top and two bars where its words sit.",
      width: 744,
      height: 380,
      size: "mid",
      caption: "Six reasons, six equal tiles, as many lit as are true.",
    },
    {
      kind: "paragraph",
      text: "Then you: a name, set large over a line, and a small grid of starter glyphs to choose your mark. Drawing your own is one tap away, but not required. Then your people: three places on a drawn shelf, with the good-news question underneath. Each name becomes a stone, each stone gets a glyph you can change, and first names are enough. Nobody is ever contacted.",
    },
    {
      kind: "figure",
      n: 10,
      file: "10-shelf.svg",
      stored: "bucket",
      alt: "A hand-drawn shelf with three stones on it, on paper and on night. The first two are drawn in ink, each with a small glyph inside in the brand colour, a sprout and a spiral, and a name bar under it. The third is a dashed outline with a plus.",
      width: 744,
      height: 240,
      size: "mid",
      caption: "Three places on a shelf: two people with their glyphs, one still open.",
    },
    {
      kind: "paragraph",
      text: "Then the pace, as one connected group: every day, a few times a week, only when it matters, or wrapping the end of the week. “No streaks, no guilt” sits under it. And then nothing special at all. The flow ends and the regular composer opens, already knowing your people, so your first pebble is a real one, made the way every next one will be.",
    },
    { kind: "heading", text: "What we left out" },
    {
      kind: "paragraph",
      text: "Instagram and how you heard about us failed the test; their best lines are kept for the welcome screens. Whether you kept a journal before went the same way. A colour world is a setting, not a question: day, night or follow your phone. The widget does not exist yet. Importing your memories is a feature for later, not a step. Free or paid has nothing to choose at signup, since recording is free. A few screens wait on something else: a reminder preview for when reminders ship, a question about how easily you name feelings if the emotion picker ever gains levels, the friend who told you about Pebbles after your first wrap, and the maker's note for the About page.",
    },
    { kind: "heading", text: "What's next" },
    {
      kind: "paragraph",
      text: "This is a design, not a release. The specification is written, and the apps do not have the flow yet. Building it comes next, on iPhone, Android and the web, with the promise's text kept where the maintainer can edit it, and the answers stored where every surface can read them.",
    },
  ],
}
