---
name: lab-note
description: >
  Author the bilingual (EN/FR) Lab Note for a user-facing Pebbles PR as a strict
  YAML snippet the maintainer can copy straight into the admin. Use this skill
  whenever you are wrapping up a PR that ships something users will notice — the
  PR has the `feat` label, or it touches a user-visible Arkaik view node
  (`docs/arkaik/bundle.json`) — and you need to fill the `## Lab Note (EN/FR)`
  section of the PR body. It defines the exact schema (the fields the `logs`
  table accepts), the allowed values, and the friendly, casual tone of voice
  (French uses "Tu"). It does NOT publish anything — the note is a proposal a
  human pastes into the Lab admin at release time.
---

# Lab Note Authoring

A **Lab Note** is the end-user-facing changelog entry for a user-facing change.
You draft it in the PR body; at release time the maintainer copies the YAML and
publishes it via the Lab admin (the `logs` table that drives the iOS Lab tab).

Your job here: produce **one YAML snippet** in the PR's `## Lab Note (EN/FR)`
section, following the schema and tone below. It is a **proposal only** — never
write to Supabase / the `logs` table from the dev loop.

## When to use

Only for **user-facing** PRs. Gate (same as the PR checklist):

- the PR has the `feat` label, **OR**
- it touches a user-visible Arkaik **view** node (`docs/arkaik/bundle.json`).

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
  title: Swap glyphs with the community
  summary: Your Glyphs page now has Mine, Owned, and Commu tabs. Find a community glyph you love and swap it for karma.
fr:
  title: Échange des glyphes avec la communauté
  summary: Ta page Glyphes propose désormais les onglets Miens, Acquis et Commu. Trouve un glyphe qui te plaît pour l'échanger contre du karma.
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
| `fr.title` | recommended | short string | Optional in the DB (falls back to EN), but always write it. |
| `fr.summary` | recommended | 1–2 sentences | Optional in the DB, but always write it. |

**PR-time defaults:** `status: in_progress`, `published: false`, and omit
`release-date`. Those three are the maintainer's release-time switches — draft
them safe.

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
  title: Your path, now on a timeline
  summary: See every pebble you've placed in order, and jump back to any moment in your journey.
fr:
  title: Ton chemin, maintenant en frise
  summary: Retrouve chaque galet que tu as posé dans l'ordre, et reviens à n'importe quel moment de ton parcours.
```

```yaml
# A feature on Android
species: feature
platform: android
status: in_progress
published: false
en:
  title: Widgets land on Android
  summary: Drop a Pebbles widget on your home screen to see today's prompt without opening the app.
fr:
  title: Les widgets débarquent sur Android
  summary: Ajoute un widget Pebbles sur ton écran d'accueil pour voir la question du jour sans ouvrir l'app.
```

## Where it goes

1. Put the finished snippet in the PR body's `## Lab Note (EN/FR)` section
   (inside a ```yaml fence). That's the whole deliverable for the dev loop.
2. **At release time (a human):** copy the YAML, open the Lab admin, and click
   **"New log"** on the Features or Announcements list. If the snippet is on the
   clipboard, the New-log form opens **prefilled** from it — review, set
   `status: shipped` / the release date / `published`, and Save.

The admin importer is tolerant: it also accepts the older list-of-dashes style
(`- species: feature`), so a snippet copied from any past PR still prefills.
