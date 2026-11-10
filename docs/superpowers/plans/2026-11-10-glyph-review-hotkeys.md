# Glyph review by keyboard — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the maintainer judge a glyph with one key and move straight to the next: Space ok, P needs progress, S needs a solid, M add a mark, D drop the twin, I incorrect, F fix (a comment, Enter saves it and moves on).

**Architecture:** The verdict type grows from `ok | fix | drop-twin` to seven verdicts, kept in the same localStorage store (`useReviewVerdicts`, keys `-v2`; old values stay valid). The review dialog (`GlyphReviewDialog`) gets a sticky footer listing the verdicts with their keys, a key handler that sets the verdict and calls `onNext`, and a single-line fix comment that submits on Enter. The tile pill and the Show filter learn the new verdicts.

**Tech Stack:** Next.js 16 / React 19 client components, base-ui Dialog (`components/ui/dialog`), Tailwind 4, Vitest.

**Branch:** `chore/1607-icon-drawings` (PR #1612, the round 2 review tool lives here). Never touch the untracked `apps/web/AGENTS.md`. Commits end with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

**Checks:** `npm run test --workspace=@pbbls/web -- lib/design`, `cd apps/web && npx tsc --noEmit`, `npm run lint --workspace=@pbbls/web`.

---

### Task 1: Seven verdicts

**Files:**
- Modify: `apps/web/lib/design/useReviewVerdicts.ts`
- Test: `apps/web/lib/design/review-verdicts.test.ts`

- [ ] **Step 1: Failing test** — append to `review-verdicts.test.ts` (import `VERDICTS`, `VERDICT_KEYS`, `verdictLabel`, `isVerdict`, `needsWork` from `./useReviewVerdicts`):

```ts
describe("verdicts", () => {
  it("has one key per verdict, Space for ok", () => {
    expect(VERDICTS).toEqual(["ok", "progress", "solid", "mark", "drop-twin", "incorrect", "fix"])
    expect(VERDICT_KEYS).toEqual({ " ": "ok", p: "progress", s: "solid", m: "mark", d: "drop-twin", i: "incorrect", f: "fix" })
    expect(new Set(Object.values(VERDICT_KEYS)).size).toBe(VERDICTS.length)
  })

  it("keeps the old stored verdicts valid and refuses unknown ones", () => {
    for (const v of ["ok", "fix", "drop-twin", "progress", "solid", "mark", "incorrect"]) expect(isVerdict(v)).toBe(true)
    expect(isVerdict("half")).toBe(false)
    expect(parseRecord('{"a":"solid","b":"nope"}', isVerdict)).toEqual({ a: "solid" })
  })

  it("names each verdict for the tile and counts everything but ok as work", () => {
    expect(verdictLabel("progress")).toBe("needs progress")
    expect(verdictLabel("drop-twin")).toBe("drop twin")
    expect(needsWork("ok")).toBe(false)
    expect(needsWork(undefined)).toBe(false)
    expect(needsWork("incorrect")).toBe(true)
  })
})
```

- [ ] **Step 2: Run** `npm run test --workspace=@pbbls/web -- lib/design/review-verdicts.test.ts` — FAIL (exports missing).

- [ ] **Step 3: Implement** — in `useReviewVerdicts.ts` replace the `Verdict` type, `ORDER`, `nextVerdict` and `isVerdict` with:

```ts
/** The maintainer's verdict on a glyph (#1607 review), in the order the dialog's footer lists them. */
export const VERDICTS = ["ok", "progress", "solid", "mark", "drop-twin", "incorrect", "fix"] as const
export type Verdict = (typeof VERDICTS)[number]

/** One key per verdict in the review dialog; every key but F moves on to the next glyph. */
export const VERDICT_KEYS: Readonly<Record<string, Verdict>> = { " ": "ok", p: "progress", s: "solid", m: "mark", d: "drop-twin", i: "incorrect", f: "fix" }

const LABELS: Readonly<Record<Verdict, string>> = {
  ok: "ok",
  progress: "needs progress",
  solid: "needs a solid",
  mark: "add a mark",
  "drop-twin": "drop twin",
  incorrect: "incorrect",
  fix: "fix",
}

export function verdictLabel(v: Verdict): string {
  return LABELS[v]
}

export function isVerdict(v: unknown): v is Verdict {
  return typeof v === "string" && (VERDICTS as readonly string[]).includes(v)
}

/** Every verdict but ok asks for a change. */
export function needsWork(v: Verdict | undefined): boolean {
  return v !== undefined && v !== "ok"
}

/** The tile pill cycles: none → ok → … → fix → none. */
export function nextVerdict(current: Verdict | undefined): Verdict | undefined {
  if (current === undefined) return VERDICTS[0]
  return VERDICTS[VERDICTS.indexOf(current) + 1]
}
```

and in `copy`, export the notes of every glyph that needs work (not only `fix`): `Object.entries(notes).filter(([name]) => needsWork(verdicts[name]))`. Keep `round: 2` in the export.

- [ ] **Step 4: Run** the test file — PASS; then `cd apps/web && npx tsc --noEmit` and fix the call sites the type change breaks (they are rewritten in Tasks 2–3; for now keep them compiling with the minimum change).

- [ ] **Step 5: Commit** `feat(web): seven glyph review verdicts, one key each`.

---

### Task 2: The dialog, judged by keyboard

**Files:**
- Modify: `apps/web/components/sandbox/design/GlyphReviewDialog.tsx`

Behaviour (each line is a requirement):

1. **Sticky footer.** The dialog content scrolls (`max-h-[90dvh]`, flex column); the footer stays visible at the bottom (`sticky bottom-0`, its own background and top border, above the scrolled content). It holds, left to right: ← → buttons, then one button per verdict in `VERDICTS` order, each showing its key as a `<kbd>` (`Space`, `P`, `S`, `M`, `D`, `I`, `F`) and its label (`verdictLabel`), the current verdict pressed (`aria-pressed`, filled style).
2. **Keys, anywhere in the dialog except while typing in the comment:** Space, P, S, M, D, I set that verdict and call `onNext()` (the glyph moves on). Pressing the key of the glyph's current verdict keeps it and still moves on (no toggling off by key; the pill and the buttons can still clear). F sets `fix` and focuses the comment, without moving on. ← / → walk without judging. `preventDefault` on every handled key (Space must not scroll or press a focused button). Modifier keys (Meta/Ctrl/Alt) are left alone. Keys are matched case-insensitively (`e.key.toLowerCase()`, Space as `" "`).
3. **The comment.** Shown whenever the verdict is `fix` (and also when a note exists for any other verdict, read-only is not needed: editable is fine). A single-line field (`<input type="text">`, or a textarea that never inserts a newline), placeholder "What the fix needs — Enter saves and moves on". Typing saves as today (`onNote`). **Enter** submits: if the text is not blank, `onNext()`; if blank, stay (a fix needs a comment) and show a one-line hint under the field ("A fix needs a comment"). **Shift+Enter** does nothing special (no newline). **Esc** leaves the field (blur) without closing the dialog, as today; a second Esc closes it.
4. **Clicking a footer button** sets that verdict like its key does (moves on, except F which focuses the comment). The ← → buttons walk.
5. **Focus.** After moving on, focus returns to the dialog content (not the comment), so the next key works at once. The round 1 note (`firstReview`) and the flags keep their place above the forms.
6. **Hint line** under the title: "Space ok · P progress · S solid · M mark · D drop twin · I incorrect · F fix · ← → walk" in muted text, so the keys are discoverable.

- [ ] **Step 1:** Rewrite the dialog's key handling, footer and comment per the list above, using `VERDICTS`, `VERDICT_KEYS`, `verdictLabel` from `useReviewVerdicts`. Remove the old `VERDICTS`/`VERDICT_KEYS` constants and the toggle-off `choose`.
- [ ] **Step 2:** `npx tsc --noEmit`, lint, tests.
- [ ] **Step 3: Commit** `feat(web): judge a glyph with one key and move on`.

---

### Task 3: Tiles, filter, header

**Files:**
- Modify: `apps/web/components/sandbox/design/pages/IconsPage.tsx`
- Modify: `apps/web/lib/design/glyph-forms.ts` (`showsGlyph`), its test
- Modify: `apps/web/components/sandbox/design/DesignTools.tsx` (`SHOW_OPTIONS`)

- [ ] The tile pill shows `verdictLabel(verdict)` (or "review"), styled: ok = primary; drop-twin = muted, struck through; any other verdict = secondary; a `fix` without a note keeps " · no note", with a note " ✎".
- [ ] `showsGlyph(name, "fix", verdicts)` becomes "needs work": true for every verdict but ok (`needsWork`). Update its test (a `solid` verdict is listed, `ok` is not). In `SHOW_OPTIONS`, the label "Marked fix" becomes "Needs work".
- [ ] The review header counts "N of M judged · K need work · J fixes noted".
- [ ] `DesignTools`' count line ("… · N marked fix") counts glyphs that need work: "· N need work".
- [ ] Checks; commit `feat(web): show every review verdict on the tiles and list what needs work`.

---

### Task 4: Look at it (controller)

On http://localhost:3000/sandbox/design/icons: Show → Needs work, open a tile, press S (verdict set, next glyph opens), F (comment focused), type, Enter (next glyph), Esc in the comment (stays open), Space on a footer button focus (sets ok, does not click twice). Screenshot the dialog with the footer pinned while the forms scroll.
