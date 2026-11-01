# Android screenshot references on the Arkaik map — design

**Date:** 2026-11-01
**Issue:** #1438. Siblings later: web (no suite yet), iOS (Xcode Cloud, not Actions).
**Milestone:** to confirm at PR time.
**Arkaik side:** arkaik #526 (events, ops, reducer) and #527 (door, `screenshot:append` scope, `arkaik shot`, `record-screenshots` reusable workflow), both merged 2026-11-01. Their spec: `docs/superpowers/specs/2026-11-01-screenshot-events-design.md` in the arkaik repo.
**PR kind:** `quality` + `android` tooling, not user-facing: `no-lab-note`. No Arkaik node changes; the screenshots land on existing view and acceptance nodes through the door.

## Summary

Every Android screen already has a committed reference PNG under
`apps/android/app/src/screenshotTestDebug/reference/`, rendered by the CI runner
and re-baselined through `android-screenshots.yml`. Those files are the
screenshots: byte-exact for the commit, reviewed in the PR that changed them,
and reachable at a stable URL by sha because the repository is public. So the
pipeline needs no render step and no bucket. It needs a way to say which
reference depicts which node, a script that turns that into the manifest the
arkaik door takes, and a workflow that posts it on every push to `main`.

## Settled decisions

1. **A mapping file, not an annotation.** `apps/android/arkaik-shots.json` maps
   an Arkaik node id to a *reference stem*: the reference directory plus the
   preview function name, without the hash and index the plugin appends.

   ```json
   {
     "V-settings": "SettingsScreenshotsKt/SettingsPageLight",
     "V-pebble-detail": "PebbleDetailScreenshotsKt/PebbleDetailCardLight"
   }
   ```

   Keys are view (`V-`) or acceptance (`AC-`) ids. Values are stems. The
   platform is always `android`. Rejected: a Kotlin annotation (needs build
   tooling to read it from the screenshot source set), and renaming 225
   preview functions to carry ids.

2. **A stem resolves to exactly one file.** The script globs
   `<stem>_<8 hex>_<index>.png` and refuses zero or several matches, so a
   renamed preview, a removed one, or a new variant breaks the check on the
   pull request, never the record on `main`. Variants are distinct stems
   (`SettingsPageLight_fs2`), so they can be mapped on purpose to an
   acceptance and are never swept in by accident.

3. **Light, default size, English fills a view's slot.** A node has one current
   shot per platform. Dark, large-font, French and wide renders are cases, and
   a case is recorded only when the mapping file points one at an acceptance
   id. The first seed maps view nodes whose preview renders the whole screen
   content (not a section), about ten to fifteen, drafted by the implementer
   and reviewed in the PR. Naming is not uniform (`…Light`/`…Dark` in most
   files, `…Paper`/`…Night` in Home and Profile), which is one more reason the
   mapping is explicit.

4. **Bytes stay on GitHub.** The recorded URL is
   `https://raw.githubusercontent.com/alexisbohns/pbbls/<sha>/<path>`. It is
   immutable by construction and needs no upload, secret or bucket. If the
   repository ever becomes private, the `lab-assets` public bucket is the
   fallback (one upload step, an admin credential in CI); that is a follow-up,
   not a hedge built now.

5. **The manifest script is the one piece of logic.**
   `scripts/arkaik/android-shots-manifest.mjs`:
   - `--sha <sha> --out shots.json` writes `[{ node_id, platform: "android", url, detail }]`, `detail` being the stem (what the door shows as evidence);
   - `--check` validates the mapping with no sha: every key looks like a node id, every stem resolves to one file; exit 1 with each failure named.
   It is tested with `node --test` against a fixture reference tree. It never
   calls the network: posting is the arkaik workflow's job.

6. **Two workflow touches.** `android.yml` gains one step after the screenshot
   validation: `node scripts/arkaik/android-shots-manifest.mjs --check`, so a
   pull request that renames a preview without updating the mapping goes red.
   A new `.github/workflows/android-screenshots-record.yml` runs on `push` to
   `main` when the reference directory, the mapping or the script changed:
   job `manifest` writes `shots.json` for `github.sha` and uploads it as
   artifact `shots`; job `record` calls
   `alexisbohns/arkaik/.github/workflows/record-screenshots.yml@main` with
   `manifest_artifact: shots` and the secret `ARKAIK_SCREENSHOT_TOKEN`. The
   arkaik side computes the order, refuses a sha not on `main`, and skips an
   unchanged URL, so a push that touched the mapping but not the renders
   records only what moved. Rejected: calling `arkaik shot` from
   `android.yml` directly (it would run on pull requests, which the door
   refuses, and it would couple the gate to a network call).

7. **One manual step.** Mint a `screenshot:append` token at
   `https://arkaik.app/settings/tokens` and add it as the repository secret
   `ARKAIK_SCREENSHOT_TOKEN`. Until it exists the `record` job fails with a
   401 that names it; `android.yml` is unaffected.

8. **Re-baselines record themselves.** A re-baseline lands as a commit on
   `main` touching the reference directory, which triggers the record
   workflow; the new URL differs by sha and content, so it applies. Nothing
   to add.

## What does not change

No Kotlin, no Gradle, no Arkaik map nodes, no bucket, no Lab Note. The
`android-screenshot-gate` skill and `apps/android/CLAUDE.md` gain one rule
each: a preview that fills a mapped slot keeps its name, or the mapping moves
with it in the same PR.

## Out of scope

Web and iOS producers; Dark and variant renders beyond explicit acceptance
mappings; a bucket; recording from pull requests; section previews (they
depict components, not nodes).

## Testing

- Script: a fixture tree with one stem matching once, one matching twice, one
  missing; `--check` names each failure; `--sha` output is byte-stable and
  carries the raw URL and the stem as `detail`.
- Workflow: parse both YAML files; the `record` job's inputs match the arkaik
  workflow's declared inputs (`manifest_artifact`, `manifest_path` default
  `shots.json`).
- Seed: `--check` passes on the committed mapping; each key exists on the
  hosted map (verified with `arkaik-mcp get_node` during the PR, not in CI).
