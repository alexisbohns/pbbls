---
name: blog-dossier
description: >
  Prepare a writing dossier (a "story kit") for a product-engineering blog post by
  gathering and shaping artifacts that already exist — GitHub issues, superpowers
  specs/plans, PRs (including their bilingual Lab Notes), git history, the Arkaik
  diff, and the decision log — into a structured dossier a human writes the final
  post from. Use this skill whenever the user wants to write, plan, or gather
  material for a blog post, engineering retrospective, milestone write-up, or "the
  story of" a feature or idea — even if they never say the word "blog". Trigger on
  phrases like "blog material", "prepare a post", "write up milestone M__", "retro
  on #__", "the story of <feature>", or "gather material for <thread>". This skill
  does NOT write the post and does NOT invent prose — it extracts, attributes, and
  orders existing material for the human storyteller.
---

# Blog Dossier — material prep for product-engineering posts

You assemble a **dossier**: a structured, fully-sourced kit that a human turns into a
blog post. You are the researcher and the archivist. You are **not** the writer.

The posts this serves are narrative retrospectives — "the story of an idea" from before
the issue to the release. The interesting content is never the final design; it is the
**delta**: the constraint nobody saw, the decision reality reversed, the design that
fought back. Your job is to surface that delta from the trail of artifacts, with exact
provenance, so the storyteller can verify and expand every claim.

## What you do and do not do

- **Do**: gather, quote (verbatim, with source refs), order on a timeline, tag, and
  flag gaps.
- **Do NOT**: write the post, paraphrase into "blog voice", editorialize, fill gaps with
  plausible-sounding narrative, or fabricate quotes. A missing piece is reported as a
  gap, never invented. Every quote and fact carries a source reference the human can
  open.

## The heart/lungs rule (read this before you tag anything)

Research, design, and engineering are not phases — they are organs that only make sense
together. A "research moment" usually contains an engineering opportunity; an
"engineering moment" usually carries a design intention. So:

- Tag material by discipline (`research`, `design`, `engineering`) where a tag genuinely
  applies — a moment can carry **several** tags.
- **Keep everything on the timeline.** Do NOT group the dossier into research / design /
  engineering buckets. Do NOT reorder events into a research→design→engineering
  sequence. The interleaving is the story; flattening it destroys the point.
- Tags are labels on threads, not bins. The storyteller weaves; you only color the yarn.

## When to use

Use when the user asks to prepare material for a write-up of a finished (or nearly
finished) body of work, scoped as one of: a **milestone**, an **issue** (+ its linked
PRs/specs/plans), or a **branch**. If scope is ambiguous, ask which of the three before
gathering — guessing wastes a lot of tool calls.

## Preflight

Run once before Step 1; fail loudly if anything is missing rather than producing a
half-empty dossier.

- `gh auth status` — must be logged in to the repo's host.
- Confirm you're in the right repo: `git rev-parse --show-toplevel` ends with the
  expected project root.
- Confirm artifact roots exist: `docs/superpowers/specs/`, `docs/superpowers/plans/`,
  `docs/arkaik/bundle.json`, `docs/decisions/log.md`. A missing root is a gap, not a
  blocker — record it.

## Step 1 — Resolve the thread

Determine the scope and collect the set of artifacts to harvest.

| Scope given | How to resolve |
|---|---|
| Milestone (e.g. "M24") | `gh issue list --milestone "<M>" --state all --json number,title,labels,milestone,closedAt` for the issues. For PRs, prefer following each issue's "Resolves #N" → linked PRs (read each issue's timeline / linked PRs panel) over `gh pr list --search 'milestone:"<M>"'` — PRs are not always milestoned even when their issue is. The thread = the union of those issues + PRs. |
| Issue (e.g. "#211") | `gh issue view <N> --json title,body,labels,milestone,createdAt,closedAt,comments`. Find linked PRs via the issue's timeline and any "Resolves #N" backrefs (`gh pr list --search "<N> in:body" --state all`). Find the spec/plan by slug+date matching (see "Filename matching" below). |
| Branch | `git log main..<branch>` for commits; `gh pr list --head <branch> --state all`; trace back to the issue via "Resolves #N" in PR bodies. **After a squash-merge, `main..<branch>` is empty** — if so, find the merge commit on `main` and use `gh pr view <N>` to recover the commit list and diff. |

Record the resolved set explicitly at the top of the dossier so the human can audit
coverage.

### Filename matching for specs and plans

Spec and plan filenames are **not** uniformly suffixed. Examples seen in the repo:
`2026-04-11-monorepo-audit-design.md`, `2026-04-11-monorepo-audit-report.md`,
`2026-04-11-database-schema.md`. Resolve by **slug substring**, not strict suffix:

```bash
ls docs/superpowers/specs/ | grep -i "<slug-fragment>"
ls docs/superpowers/plans/ | grep -i "<slug-fragment>"
```

If multiple candidates match, include them all; if none match, record a gap.

## Step 2 — Gather the sources

Pull from each source and extract only what the dossier sections below need. Quote
verbatim; record `source` for every extracted item (issue #, PR #, `file:line`, or
commit sha).

| Source | Command(s) | Extract |
|---|---|---|
| Issue (the original idea) | `gh issue view <N> --json title,body,labels,milestone,createdAt,closedAt,comments` | Initial idea / intention / context; original framing in the body; key turning points in comments (with timestamps + author). |
| Spec | read `docs/superpowers/specs/<match>.md` | Approaches considered, the one chosen, **Out of scope**, any "alternatives"/"rejected" notes. |
| Plan | read `docs/superpowers/plans/<match>.md` | The **spec-drift** section, the **Lessons learned** annotation, self-review notes. |
| PR(s) | `gh pr view <N> --json title,body,mergedAt,files,labels,milestone` | Section by section: **Changes**, **Notes**, **Lab Note (EN/FR)**, plus any legacy sections older PRs may carry (Summary, Implementation Notes, Notes for Review, Test Plan, Follow-Ups). Capture merge timestamp and key files. |
| Lab Note | the PR body's `## Lab Note (EN/FR)` block — a YAML snippet with `en:`/`fr:` each holding a `title` + `summary` (see the `lab-note` skill) | Bilingual end-user title + summary (EN + FR). This is already user-facing copy — treat it as primary blog raw material, not as engineering notes. If the section is absent or deleted, that's also a signal (the PR wasn't user-facing). |
| Git history | `git log --format='%h %ad %s' --date=short <range>` | Conventional commits as timeline beats. Flag reversal/discovery smells: `revert`, `fix:`, `fixup`, "actually", "turns out", "instead". |
| Arkaik diff | `git diff <base>..<head> -- docs/arkaik/bundle.json` | Nodes/edges added, removed, renamed, or status-changed → the architecture delta. |
| Decision log | `grep -nE "#<N>|PR.?#?<N>|<slug>" docs/decisions/log.md` and read surrounding entries | Significant decisions (and any superseded/deprecated lines) tied to this thread. |

If a source is absent (no spec, no decision-log entry, no Lab Note, etc.), note it as a
gap and move on. Never fabricate.

## Step 3 — Build the dossier

Produce exactly these sections. Each item carries `source`. Order chronologically except
where a section is intrinsically a ledger.

1. **Header** — thread scope + resolved artifact set; date range; a one-line through-line
   (the *fil d'Ariane*) stated factually, not dramatically.
2. **Timeline** — the spine. Dated beats from issue/comments/commits/PR/release. Each
   beat: `date · what happened · source`. This is where pivots become visible.
3. **Intention** — the original idea in its first voice, lightly trimmed (verbatim where
   possible), with source. Do not modernize it with hindsight.
4. **Decision ledger** — each *significant* decision as: `considered → chose → why →
   survived contact with reality?`. The last field is the harvested drift: cite where
   it held or broke (PR note, commit, decision-log supersede line). Minor/implementation
   choices stay out.
5. **Obstacles** — what reality bit and how it was overcome. Source each from
   spec-drift, PR **Notes**, or reversal-smell commits.
6. **Engineer notes (pull-quotes)** — raw quotable lines with provenance, like interview
   highlights: a sharp commit message, a spec rationale, a review remark. Tag each by
   discipline per the heart/lungs rule. Quote verbatim; never polish.
7. **Lab Notes harvested** — the bilingual EN/FR end-user blurbs already drafted on the
   relevant PRs, listed verbatim with their PR ref. This is closest to "publishable
   voice" — the human storyteller often opens or closes the post from here.
8. **Open questions & opportunities** — from PR follow-ups and spec **Out of scope** that
   hint at future work. Source each.
9. **Architecture delta** — plain-language summary of the Arkaik diff (e.g. "added 3
   views + 1 model, rerouted the souls flow"), with the node/edge ids.

Sections 4, 5, 6 may carry discipline tags. None of the nine sections is grouped by
discipline.

## Output

Write both files to `docs/blog/<scope-slug>/` (e.g. `docs/blog/m24-introduce-the-lab/`).
Create the directory if missing. The human may later move or gitignore this directory —
default to writing it in-tree so it's reviewable in the same diff.

- `dossier.json` — the source of truth (schema below).
- `dossier.md` — a rendered view of the same content, in the section order above, that
  the human writes from.

Generate both in one pass from the same gathered data; they must not diverge. After
writing, print a short summary listing: scope, resolved artifact counts, gap count, and
the two file paths.

### `dossier.json` schema

```jsonc
{
  "scope": { "type": "milestone|issue|branch", "ref": "M24", "title": "...",
             "date_range": ["2026-04-01", "2026-04-22"],
             "through_line": "one factual line" },
  "resolved": { "issues": [294], "prs": [293, 296], "specs": ["..."], "plans": ["..."] },
  "timeline": [ { "date": "2026-04-11", "event": "...", "source": "#294" } ],
  "intention": { "text": "verbatim or lightly trimmed", "source": "#294 body" },
  "decisions": [ { "considered": ["a","b"], "chose": "b", "why": "...",
                   "survived": "held|reversed: <how>", "tags": ["engineering"],
                   "source": "PR#296 Notes" } ],
  "obstacles": [ { "what": "...", "overcome_by": "...",
                   "tags": ["engineering","design"], "source": "plan spec-drift" } ],
  "pull_quotes": [ { "quote": "verbatim", "tags": ["research"],
                     "source": "abc123 commit" } ],
  "lab_notes": [ { "pr": 293,
                   "en": { "title": "...", "summary": "..." },
                   "fr": { "titre": "...", "resume": "..." } } ],
  "open_questions": [ { "text": "...", "source": "PR#293 Notes" } ],
  "architecture_delta": { "summary": "...", "nodes_added": [], "nodes_removed": [],
                          "status_changes": [], "edges": [] },
  "gaps": [ "no decision-log entry found for this thread" ]
}
```

### `dossier.md` template

```markdown
# Dossier — <title>  (<scope> <ref>, <date range>)
**Through-line:** <one factual line>
**Built from:** issues <…> · PRs <…> · specs <…> · plans <…>
**Gaps:** <anything missing>

## Timeline
- <date> — <event>  ·  _<source>_

## Intention
> <verbatim/trimmed>  ·  _<source>_

## Decision ledger
- **<chose>** — considered <…>; because <why>. **Reality:** <held / reversed: how>.  `[tags]` _<source>_

## Obstacles
- <what> → overcome by <how>.  `[tags]` _<source>_

## Engineer notes
- "<verbatim quote>"  `[tags]` _<source>_

## Lab Notes harvested
- **PR#<N> — EN:** <title> — <summary>
  **FR :** <titre> — <résumé>

## Open questions & opportunities
- <text>  _<source>_

## Architecture delta
<summary, with node/edge ids>
```

## Hard rules

- **Verbatim quotes only**, each with a source ref. No paraphrase inside quote marks.
- **No fabrication.** Never invent a quote, a decision, or a connecting narrative.
  Gaps are reported in `gaps`, not filled.
- **No prose authoring.** Bullets and fragments, not paragraphs. The human writes the
  post.
- **No discipline grouping or phase ordering.** Timeline order, multi-tag freely.
- **Significance bar for the ledger:** include a decision only if reversing or
  rediscovering it would cost real time. Implementation trivia stays in the (immutable)
  spec.
- **Lab Notes are quoted as drafts.** They are proposals on the PR, not necessarily what
  shipped on the Lab — flag them as `draft` unless the user confirms otherwise.

## Example (shape only)

A decision-ledger entry harvested from a real drift:

> **Lossy per-row decode** — considered: fail-fast strict decode vs. skip-bad-row
> wrapper; chose the wrapper. Because one malformed `v_logs_with_counts` row was taking
> the whole changelog down. **Reality:** held; later reused for all four Log feeds.
> `[engineering]` _PR#293 Notes; commit a1b2c3_

A pull-quote, tagged but left on the timeline:

> "RLS does the user filtering; no client-side `.eq("user_id", ...)`."
> `[engineering, design]` _PR#211 Notes_
