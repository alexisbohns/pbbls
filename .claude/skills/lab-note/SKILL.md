---
name: lab-note
description: >
  Author the bilingual (EN/FR) Lab Note for a user-facing Pebbles PR as a strict
  YAML snippet the maintainer can copy straight into the admin. Use this skill
  whenever you are wrapping up a PR that ships something users will notice — the
  PR has the `feat` label, or it touches a user-visible Arkaik view node — and
  you need to fill the `## Lab Note (EN/FR)` section of the PR body. It defines the exact schema (the fields the `logs`
  table accepts plus the optional `suggested:` block for the Ariko vault), the
  allowed values, and the friendly, casual tone of voice (French uses "Tu").
  One block serves three destinations: on merge it auto-posts to the Ariko
  changelog vault and to the Arkaik journal, and a human pastes the same YAML
  into the Pebbles Lab admin at release time. The skill itself publishes nothing — it only authors the note.
---

# Lab Note Authoring

A **Lab Note** is the end-user-facing changelog entry for a user-facing change.
You draft it in the PR body; at release time the maintainer copies the YAML and
publishes it via the Lab admin (the `logs` table that drives the iOS Lab tab).

Your job here: produce **one YAML snippet** in the PR's `## Lab Note (EN/FR)`
section, following the schema and tone below. It is a **proposal only** (never
write to Supabase / the `logs` table from the dev loop).

## One block, three destinations

The single snippet you write serves **all three** delivery legs:

1. **Ariko inbox (automatic, on merge):** the repo's `.github/workflows/lab-note.yml`
   watches for merged PRs, reads this section, and posts the note to the Ariko
   changelog vault (idempotent upsert on `owner/repo#N`). No human action beyond
   merging. Ariko's gate is a heading that starts with `## Lab Note` holding one
   fenced `yaml` block — which our `## Lab Note (EN/FR)` heading already matches.
   Ariko requires `en.title` + `en.summary`; it reads the optional `suggested:`
   block and **ignores every other top-level key** (`species`, `platform`, etc.).
2. **The Arkaik journal (automatic, on merge):** the Arkaik GitHub App turns the
   same note into a `deliverable.shipped` entry on the hosted Pebbles map, which
   is what the Arkaik changelog renders. It reads `en` + `fr`, and the optional
   `nodes:` list — see below. It works out the platform from the folders the PR
   touched, so `platform:` here is **not** what fills the chip.
3. **Pebbles Lab admin (manual, at release):** the maintainer copies the same
   YAML and publishes it via the Lab admin, unchanged (see "Where it goes").

So you write one block with the full Pebbles key set **plus** the `suggested:`
block **plus** `nodes:` where it applies; each destination reads the keys it
cares about and ignores the rest.

## Both languages are mandatory

Every Lab Note **must** contain a complete `en:` block **and** a complete `fr:`
block, each with its own `title` and `summary`. A note with only English is
**incomplete** and must not be shipped. Do not treat French as optional or as a
"nice to have" step you can skip when short on time. If you emit the snippet, it
has all four fields (`en.title`, `en.summary`, `fr.title`, `fr.summary`).

## No em dashes — use parentheses

Do **not** use the em dash (`—`) anywhere in a Lab Note, in either language. For
an aside or parenthetical, use round brackets `( )` instead. For a hard break
between two clauses, use a period and start a new sentence. This applies to
`title` and `summary` in both `en:` and `fr:`.

- Wrong: `The whole detail view — background, title, tiles — now tints.`
- Right: `The whole detail view (background, title, tiles) now tints.`

## Always quote titles and summaries

Wrap every `title` and `summary` in double quotes, in both languages, as every
example below does. A colon is the natural way to write a sentence ("Heads up:
it moved", "ton compte : ceux que...") and it is exactly what an unquoted YAML
value cannot hold: the parser reads `key: value` and the whole note fails to
parse, which fails the post-on-merge job. Quoting costs nothing and takes the
failure mode off the table, apostrophes included.

- Wrong: `summary: Heads up: the widget moved.`
- Right: `summary: "Heads up: the widget moved."`

Slug-ish values (`species`, `platform`, `status`, `nodes`, `molecule`, `type`,
`tags`) need no quotes.

## When to use

Only for **user-facing** PRs. Gate (same as the PR checklist):

- the PR has the `feat` label, **OR**
- it touches a user-visible Arkaik **view** node.

If the PR is not user-facing (chore, refactor, infra, docs-only), there is no Lab
Note — delete the section from the PR body.

## The YAML schema

Emit **idiomatic YAML** (nested objects). Every value below maps 1:1 to a `logs`
column; the allowed sets are enforced by the admin, so use them verbatim.

```yaml
species: feature          # announcement | feature
platform: ios             # all | webapp | ios | android | project | infra
status: in_progress       # backlog | planned | in_progress | shipped
release-date: 2026-07-17T20:00:00   # optional; maps to released_at
published: false          # boolean
en:
  title: "Swap glyphs with the community"
  summary: "Your Glyphs page now has Mine, Owned, and Commu tabs. Find a community glyph you love and swap it for karma."
fr:
  title: "Échange des glyphes avec la communauté"
  summary: "Ta page Glyphes propose désormais les onglets Miens, Acquis et Commu. Trouve un glyphe qui te plaît pour l'échanger contre du karma."
nodes: [V-glyphs-list, V-glyph-swap-drawer, F-swap-glyph]   # optional; Arkaik only
suggested:                  # optional; for the Ariko vault only
  molecule: pbbls
  atom: glyphs              # only when confidently known
  type: feature
  tags: [glyphs, community, karma]
```

| Field | Required | Allowed values / format | Notes |
|---|---|---|---|
| `species` | yes | `announcement`, `feature` | A shipped capability is a `feature`; a standalone message is an `announcement`. There is **no** `fix` — a user-facing fix is a `feature`. |
| `platform` | yes | `all`, `webapp`, `ios`, `android`, `project`, `infra` | Use **`webapp`**, not `web`. `all` when it lands everywhere. |
| `status` | yes | `backlog`, `planned`, `in_progress`, `shipped` | At PR time this is usually `in_progress` (built, not yet released). The maintainer flips it to `shipped` at release. |
| `release-date` | no | ISO datetime, e.g. `2026-07-17T20:00:00` | Maps to the `released_at` column. Leave it out at PR time; the maintainer sets it at release. |
| `published` | yes | `true` / `false` | **`false` at PR time** — a draft. The maintainer flips it to `true` when publishing. |
| `en.title` | yes | short string | Required. |
| `en.summary` | yes | 1–2 sentences | Required. |
| `fr.title` | yes | short string | The DB falls back to EN if absent, but the note is incomplete without it. Always write it. |
| `fr.summary` | yes | 1–2 sentences | The DB tolerates its absence, but the note is incomplete without it. Always write it. |
| `nodes` | no | list of Arkaik node ids | The graph nodes this change touched, most important first. Read **only** by the Arkaik journal; ignored by the Lab admin and the Ariko vault. See below. |
| `suggested.molecule` | no | `pbbls` | Always `pbbls` for this repo. Routing hint for the Ariko vault; ignored by the Lab admin. |
| `suggested.atom` | no | short string | The feature area (`glyphs`, `path`, `read-view`, …). Include **only when confidently known** — omit rather than guess. |
| `suggested.type` | no | short string | The kind of change, e.g. `feature`, `announcement`. |
| `suggested.tags` | no | list of short strings | A few free-form topic tags for the vault. |

**PR-time defaults:** `status: in_progress`, `published: false`, and omit
`release-date`. Those three are the maintainer's release-time switches — draft
them safe.

**The `suggested:` block** is optional and read **only by the Ariko vault** (the
Lab admin ignores it). Always set `molecule: pbbls`. Add `atom`, `type`, and
`tags` when you can fill them meaningfully; leave `atom` out rather than guess.

### `nodes:` — what the change touched on the map

The Arkaik changelog renders each entry with a **Touched** list of product-graph
nodes and a count. Arkaik works out the *acceptances* on its own, by reading an
`AC-…` id in the PR title or body — but **views, flows, API endpoints, data
models and decisions it cannot guess**. An entry with nothing under it is the
default, and this key is how you avoid it.

```yaml
nodes: [V-pebble-record, V-record-success, F-record-pebble-flow, AC-record-pebble-flow]
```

- **List the ids your change actually touched**, most important first. They lead
  the entry in your order; any acceptance the PR body mentions follows.
- **Only ids you know exist.** Read them off the **hosted** map with the
  `arkaik-mcp` tools (`list_nodes` / `get_node`) — never reconstruct one from a
  screen name, and never from `docs/arkaik/bundle.json`, which is a frozen
  pre-hosted snapshot (see CLAUDE.md § "The map is hosted"). An id that matches
  nothing is left off the entry and named back at you in the delivery response.
- If you moved nodes on the map in this PR, those are exactly the ids that
  belong here.
- **Omit the key** when the change touched no node. A malformed `nodes:` never
  costs you the note — unusable entries are dropped one at a time and the note
  still posts everywhere.

## Tone of voice

Write for **end users, not engineers**. This is the part the old workflow got
wrong, so it matters:

- **Lead with the benefit**, not the mechanism. "Find a glyph you love and swap
  it" beats "Added a swap endpoint to the Glyphs tab."
- **Short.** A title of a few words; a summary of one or two sentences.
- **Warm and a little playful**, never corporate. No "We are pleased to
  announce."
- **French uses the informal "Tu"** — casual and friendly, addressing the user
  directly ("Ta page", "Trouve", "Échange"). Never "Vous".
- French is a real adaptation, **not a literal translation** — keep it natural.
- No engineering jargon, ticket numbers, or internal names.

## Gold-standard examples

Match this register.

```yaml
# A feature on the web app, still in progress at PR time
species: feature
platform: webapp
status: in_progress
published: false
en:
  title: "Your path, now on a timeline"
  summary: "See every pebble you've placed in order, and jump back to any moment in your journey."
fr:
  title: "Ton chemin, maintenant en frise"
  summary: "Retrouve chaque galet que tu as posé dans l'ordre, et reviens à n'importe quel moment de ton parcours."
```

```yaml
# A feature on Android
species: feature
platform: android
status: in_progress
published: false
en:
  title: "Widgets land on Android"
  summary: "Drop a Pebbles widget on your home screen to see today's prompt without opening the app."
fr:
  title: "Les widgets débarquent sur Android"
  summary: "Ajoute un widget Pebbles sur ton écran d'accueil pour voir la question du jour sans ouvrir l'app."
```

## Where it goes

1. Put the finished snippet in the PR body's `## Lab Note (EN/FR)` section
   (inside a ```yaml fence). That's the whole deliverable for the dev loop.
2. **On merge (automatic):** `.github/workflows/lab-note.yml` reads this section
   and posts the note to the Ariko changelog vault. The upsert is keyed on
   `owner/repo#N`, so merging (or re-running the job) never duplicates the
   capture. A chore PR with no `## Lab Note` section makes the job log
   "skipped" — nothing is posted. No human action beyond merging.
3. **On merge (automatic):** the Arkaik GitHub App appends the same note to the
   hosted Pebbles map's journal as a `deliverable.shipped` entry, carrying the
   `nodes:` you listed and the platform it worked out from the folders the PR
   touched. Idempotent per PR. Problems — a malformed note, an id nothing
   answers to — are reported in the App's delivery response, not in this repo's
   Actions log.
4. **At release time (a human):** copy the YAML, open the Lab admin, and click
   **"New log"** on the Features or Announcements list. If the snippet is on the
   clipboard, the New-log form opens **prefilled** from it — review, set
   `status: shipped` / the release date / `published`, and Save.

The admin importer is tolerant: it also accepts the older list-of-dashes style
(`- species: feature`), so a snippet copied from any past PR still prefills.
