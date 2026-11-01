# Android screenshot references on the Arkaik map — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On every push to `main` that changes an Android screenshot reference or its mapping, record the mapped references as screenshots on the hosted Arkaik map through the arkaik `record-screenshots` reusable workflow.

**Architecture:** One mapping file (`apps/android/arkaik-shots.json`, node id → reference stem), one pure script (`scripts/arkaik/android-shots-manifest.mjs`, `--check` on PRs and `--sha … --out …` on `main`), one new workflow that uploads the manifest and calls arkaik, one `--check` step in the existing Android gate. Spec: `docs/superpowers/specs/2026-11-01-android-arkaik-screenshots-design.md`. Issue #1438.

**Tech stack:** Node 22 ESM scripts (no dependencies, like `scripts/journal/drop-pebble.mjs`), `node --test`, GitHub Actions. The arkaik side is already live: `alexisbohns/arkaik/.github/workflows/record-screenshots.yml@main` takes `manifest_artifact`, optional `manifest_path` (default `shots.json`), optional `sha`, and the secret `ARKAIK_SCREENSHOT_TOKEN`.

**Branch:** `feat/1438-android-arkaik-screenshots` from updated `main`. Single PR, labels `feat` + `android` + `quality` + `no-lab-note`, milestone to confirm with the maintainer.

**Reference layout (read-only facts):** references live under `apps/android/app/src/screenshotTestDebug/reference/app/pbbls/android/<File>Kt/`, named `<Function>[_<variant>]_<8 hex>_<index>.png`. A *stem* is `<File>Kt/<Function>[_<variant>]`.

---

## File map

| File | Responsibility |
|---|---|
| `apps/android/arkaik-shots.json` (create) | node id → stem, the seed |
| `scripts/arkaik/android-shots-manifest.mjs` (create) | resolve stems, write the manifest or check the mapping |
| `scripts/arkaik/android-shots-manifest.test.mjs` (create) | `node --test` over a fixture tree it builds in a temp dir |
| `.github/workflows/android-screenshots-record.yml` (create) | push to main → manifest artifact → arkaik reusable workflow |
| `.github/workflows/android.yml` (modify) | `--check` step after `Validate UI screenshots` |
| `apps/android/CLAUDE.md`, `.claude/skills/android-screenshot-gate/SKILL.md` (modify) | the one rule about keeping the mapping in step |
| `package.json` (modify) | `test:arkaik-shots` script |

---

### Task 1: The manifest script, test first

**Files:**
- Create: `scripts/arkaik/android-shots-manifest.test.mjs`
- Create: `scripts/arkaik/android-shots-manifest.mjs`
- Modify: `package.json` (root scripts)

- [ ] **Step 1: Write the failing test**

`scripts/arkaik/android-shots-manifest.test.mjs`:

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { buildManifest, checkMapping, resolveStem } from "./android-shots-manifest.mjs";

/** A fixture reference tree: one stem that resolves once, one twice, one never. */
function fixture() {
  const root = mkdtempSync(join(tmpdir(), "arkaik-shots-"));
  const dir = join(root, "SettingsScreenshotsKt");
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, "SettingsPageLight_4d5ad0cf_0.png"), "png");
  writeFileSync(join(dir, "SettingsPageLight_fs2_72706441_0.png"), "png");
  writeFileSync(join(dir, "SettingsPageDark_22b24d2c_0.png"), "png");
  writeFileSync(join(dir, "SettingsPageDark_99999999_0.png"), "png");
  return root;
}

test("resolveStem finds exactly one file and ignores variants of the same function", () => {
  const root = fixture();
  try {
    assert.equal(resolveStem(root, "SettingsScreenshotsKt/SettingsPageLight"), "SettingsScreenshotsKt/SettingsPageLight_4d5ad0cf_0.png");
    assert.equal(resolveStem(root, "SettingsScreenshotsKt/SettingsPageLight_fs2"), "SettingsScreenshotsKt/SettingsPageLight_fs2_72706441_0.png");
  } finally { rmSync(root, { recursive: true, force: true }); }
});

test("checkMapping names every failure and passes a clean mapping", () => {
  const root = fixture();
  try {
    const bad = checkMapping(root, {
      "V-settings": "SettingsScreenshotsKt/SettingsPageLight",
      "V-dark": "SettingsScreenshotsKt/SettingsPageDark",
      "V-gone": "SettingsScreenshotsKt/Nope",
      "settings": "SettingsScreenshotsKt/SettingsPageLight",
    });
    assert.deepEqual(bad.map((f) => f.key).sort(), ["V-dark", "V-gone", "settings"]);
    assert.match(bad.find((f) => f.key === "V-dark").message, /2 files/);
    assert.match(bad.find((f) => f.key === "V-gone").message, /no file/);
    assert.match(bad.find((f) => f.key === "settings").message, /V-|AC-/);
    assert.deepEqual(checkMapping(root, { "V-settings": "SettingsScreenshotsKt/SettingsPageLight" }), []);
  } finally { rmSync(root, { recursive: true, force: true }); }
});

test("buildManifest writes the raw URL at the sha, the stem as detail, keys sorted", () => {
  const root = fixture();
  try {
    const out = buildManifest(root, { "V-settings": "SettingsScreenshotsKt/SettingsPageLight", "AC-a11y-large-font": "SettingsScreenshotsKt/SettingsPageLight_fs2" }, "abc123", "apps/android/app/src/screenshotTestDebug/reference/app/pbbls/android");
    assert.deepEqual(out, [
      { node_id: "AC-a11y-large-font", platform: "android", url: "https://raw.githubusercontent.com/alexisbohns/pbbls/abc123/apps/android/app/src/screenshotTestDebug/reference/app/pbbls/android/SettingsScreenshotsKt/SettingsPageLight_fs2_72706441_0.png", detail: "SettingsScreenshotsKt/SettingsPageLight_fs2" },
      { node_id: "V-settings", platform: "android", url: "https://raw.githubusercontent.com/alexisbohns/pbbls/abc123/apps/android/app/src/screenshotTestDebug/reference/app/pbbls/android/SettingsScreenshotsKt/SettingsPageLight_4d5ad0cf_0.png", detail: "SettingsScreenshotsKt/SettingsPageLight" },
    ]);
  } finally { rmSync(root, { recursive: true, force: true }); }
});

test("buildManifest throws on an unresolvable stem rather than emitting a partial manifest", () => {
  const root = fixture();
  try {
    assert.throws(() => buildManifest(root, { "V-gone": "SettingsScreenshotsKt/Nope" }, "abc123", "x"), /V-gone/);
  } finally { rmSync(root, { recursive: true, force: true }); }
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `node --test scripts/arkaik/android-shots-manifest.test.mjs`
Expected: `ERR_MODULE_NOT_FOUND` for the script.

- [ ] **Step 3: Write the script**

`scripts/arkaik/android-shots-manifest.mjs`:

```js
#!/usr/bin/env node
// Turn the committed Android screenshot references into the manifest the arkaik
// screenshot door takes (spec docs/superpowers/specs/2026-11-01-android-arkaik-screenshots-design.md).
//
//   node scripts/arkaik/android-shots-manifest.mjs --check
//   node scripts/arkaik/android-shots-manifest.mjs --sha <sha> --out shots.json
//
// The mapping (apps/android/arkaik-shots.json) goes from an Arkaik node id to a
// reference STEM: `<File>Kt/<Function>[_<variant>]`, the file name without the
// `_<8 hex>_<index>.png` the plugin appends. A stem must resolve to exactly one
// file: a renamed preview or a new variant fails `--check` on the pull request.
// The URL is the raw GitHub file at the sha — immutable, no upload, no bucket.
// This script never posts: the arkaik `record-screenshots` workflow does.
import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { parseArgs } from "node:util";
import { fileURLToPath } from "node:url";

export const REFERENCE_DIR = "apps/android/app/src/screenshotTestDebug/reference/app/pbbls/android";
export const MAPPING_FILE = "apps/android/arkaik-shots.json";
const RAW_ORIGIN = "https://raw.githubusercontent.com/alexisbohns/pbbls";
const NODE_ID = /^(V|AC)-[A-Za-z0-9._-]+$/;
const SUFFIX = /_[0-9a-f]{8}_\d+\.png$/;

/** The files in `root` whose name is `<stem>_<8 hex>_<index>.png`, as `<dir>/<file>` paths. */
function matches(root, stem) {
  const slash = stem.lastIndexOf("/");
  if (slash === -1) return [];
  const dir = stem.slice(0, slash);
  const fn = stem.slice(slash + 1);
  const full = join(root, dir);
  if (!existsSync(full)) return [];
  return readdirSync(full)
    .filter((name) => name.startsWith(`${fn}_`) && SUFFIX.test(name) && name.slice(0, -name.match(SUFFIX)[0].length) === fn)
    .sort()
    .map((name) => `${dir}/${name}`);
}

/** The one reference path for a stem, or a thrown Error naming why there is not exactly one. */
export function resolveStem(root, stem) {
  const found = matches(root, stem);
  if (found.length === 1) return found[0];
  if (found.length === 0) throw new Error(`${stem}: no file matches (renamed or removed preview?)`);
  throw new Error(`${stem}: ${found.length} files match (${found.join(", ")}); a stem must name one render`);
}

/** Every mapping entry that is wrong, as `{ key, message }`; empty when the mapping is clean. */
export function checkMapping(root, mapping) {
  const failures = [];
  for (const [key, stem] of Object.entries(mapping)) {
    if (!NODE_ID.test(key)) failures.push({ key, message: `${key}: not a node id (expected V-… or AC-…)` });
    if (typeof stem !== "string" || stem === "") { failures.push({ key, message: `${key}: the stem must be a non-empty string` }); continue; }
    try { resolveStem(root, stem); } catch (e) { failures.push({ key, message: e.message }); }
  }
  return failures;
}

/** The door's entries, keys sorted so the output is byte-stable. Throws on the first unresolvable stem. */
export function buildManifest(root, mapping, sha, referenceDir) {
  return Object.keys(mapping).sort().map((node_id) => {
    let file;
    try { file = resolveStem(root, mapping[node_id]); } catch (e) { throw new Error(`${node_id} → ${e.message}`); }
    return { node_id, platform: "android", url: `${RAW_ORIGIN}/${sha}/${referenceDir}/${file}`, detail: mapping[node_id] };
  });
}

function main() {
  const { values } = parseArgs({ options: { check: { type: "boolean" }, sha: { type: "string" }, out: { type: "string" }, root: { type: "string", default: "." } } });
  const root = join(values.root, REFERENCE_DIR);
  const mapping = JSON.parse(readFileSync(join(values.root, MAPPING_FILE), "utf8"));
  if (values.check) {
    const failures = checkMapping(root, mapping);
    for (const f of failures) console.error(`arkaik-shots: ${f.message}`);
    if (failures.length > 0) process.exit(1);
    console.log(`arkaik-shots: ${Object.keys(mapping).length} mapping(s) resolve.`);
    return;
  }
  if (!values.sha || !/^[0-9a-f]{7,40}$/.test(values.sha)) { console.error("arkaik-shots: --sha <sha> is required (with --out <file>)"); process.exit(1); }
  if (!values.out) { console.error("arkaik-shots: --out <file> is required"); process.exit(1); }
  const manifest = buildManifest(root, mapping, values.sha, REFERENCE_DIR);
  writeFileSync(values.out, `${JSON.stringify(manifest, null, 2)}\n`);
  console.log(`arkaik-shots: wrote ${manifest.length} entr${manifest.length === 1 ? "y" : "ies"} to ${values.out}`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) main();
```

- [ ] **Step 4: Run to verify it passes**

Run: `node --test scripts/arkaik/android-shots-manifest.test.mjs`
Expected: 4 passing, 0 failing.

- [ ] **Step 5: Register the script and commit**

In the root `package.json` `scripts`, add `"test:arkaik-shots": "node --test scripts/arkaik/android-shots-manifest.test.mjs"` next to the other `test:*` entries (if there are none at the root, add it after `"lint"`).

```bash
git add scripts/arkaik package.json
git commit -m "feat(android): build the arkaik screenshot manifest from the committed references"
```

---

### Task 2: The seed mapping

**Files:**
- Create: `apps/android/arkaik-shots.json`

- [ ] **Step 1: Draft the mapping**

For each Android view node below, find the preview that renders the whole screen content in Light (or Paper), default size, English, and write its stem. Read the `*Screenshots.kt` file when the name is not obvious; a section preview (e.g. `SettingsAccountSectionsLight`) is never a view's shot. Start from these, confirmed against the reference directory listing, and add others only when the preview clearly depicts the whole view:

```json
{
  "V-achievements": "AchievementsPageScreenshotsKt/AchievementsPageLight",
  "V-announcement-detail": "LabScreenshotsKt/AnnouncementDetailLight",
  "V-collections-list": "CollectionsScreenshotsKt/CollectionsGalleryLight",
  "V-home": "HomeScreenshotsKt/HomeShellPaper",
  "V-lab": "LabScreenshotsKt/LabContentLight",
  "V-pebble-detail": "PebbleDetailScreenshotsKt/PebbleDetailCardLight",
  "V-profile": "ProfileScreenshotsKt/YouPaper",
  "V-settings": "SettingsScreenshotsKt/SettingsPageLight"
}
```

Candidates to check in their files before adding: `V-timeline` (PathScreenScreenshotsKt/PathScreenPopulatedLight, if the Path screen is the timeline node), `V-souls-list` (SoulsScreenshotsKt), `V-glyphs-list` and `V-glyph-carve` (CarveScreenshotsKt / GlyphChromeScreenshotsKt), `V-pebble-record` (CaptureFlowScreenshotsKt), `V-pebble-edit` (EditPebbleScreenshotsKt), `V-onboarding-*` (IntroScreenshotsKt / FunnelScreenshotsKt), `V-connections-list` (ConnectionsScreenshotsKt), `V-monthly-cairn*` (MonthPageScreenshotsKt), `V-welcome` / `V-login` (FunnelScreenshotsKt). Keep the file sorted by key.

- [ ] **Step 2: Check it**

Run: `node scripts/arkaik/android-shots-manifest.mjs --check`
Expected: `arkaik-shots: N mapping(s) resolve.` Fix any stem it names.

Run: `node scripts/arkaik/android-shots-manifest.mjs --sha $(git rev-parse HEAD) --out /tmp/shots.json && head -12 /tmp/shots.json`
Expected: entries with raw URLs; open one URL in a browser after the branch is pushed to see the PNG.

- [ ] **Step 3: Verify the node ids exist on the hosted map**

With the `arkaik-mcp` tools (`ARKAIK_TOKEN` exported), call `get_node` for each key; every one must exist and list `android` in `platforms`. Drop or fix any that does not. This is a one-time check, not CI.

- [ ] **Step 4: Commit**

```bash
git add apps/android/arkaik-shots.json
git commit -m "feat(android): seed the arkaik screenshot mapping with the full-screen previews"
```

---

### Task 3: The workflows

**Files:**
- Create: `.github/workflows/android-screenshots-record.yml`
- Modify: `.github/workflows/android.yml` (after the `Validate UI screenshots` step)

- [ ] **Step 1: The record workflow**

```yaml
# Record the mapped Android screenshot references on the hosted Arkaik map
# (docs/superpowers/specs/2026-11-01-android-arkaik-screenshots-design.md).
# The references are committed PNGs, so there is nothing to render: this builds
# the manifest for the pushed sha (raw GitHub URLs, immutable by sha) and hands
# it to arkaik's reusable workflow, which computes the order, refuses a sha not
# on main, and skips an unchanged URL. Needs the ARKAIK_SCREENSHOT_TOKEN secret
# (a screenshot:append token from https://arkaik.app/settings/tokens).
name: android-screenshots-record

on:
  push:
    branches: [main]
    paths:
      - "apps/android/app/src/screenshotTestDebug/reference/**"
      - "apps/android/arkaik-shots.json"
      - "scripts/arkaik/android-shots-manifest.mjs"
      - ".github/workflows/android-screenshots-record.yml"

permissions:
  contents: read

jobs:
  manifest:
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@v4
        with:
          persist-credentials: false
      - uses: actions/setup-node@v4
        with:
          node-version: 22
      - name: Build the manifest for this sha
        run: node scripts/arkaik/android-shots-manifest.mjs --sha "$GITHUB_SHA" --out shots.json
      - uses: actions/upload-artifact@v4
        with:
          name: shots
          path: shots.json
          retention-days: 7

  record:
    needs: manifest
    uses: alexisbohns/arkaik/.github/workflows/record-screenshots.yml@main
    with:
      manifest_artifact: shots
    secrets:
      ARKAIK_SCREENSHOT_TOKEN: ${{ secrets.ARKAIK_SCREENSHOT_TOKEN }}
```

- [ ] **Step 2: The gate step**

In `.github/workflows/android.yml`, after the `Validate UI screenshots` step (and its failure-only artifact upload), add:

```yaml
      - name: Check the arkaik screenshot mapping
        run: node scripts/arkaik/android-shots-manifest.mjs --check
```

The job already sets up Node for the coverage summary; if it does not at that point, add `actions/setup-node@v4` with `node-version: 22` before the step.

- [ ] **Step 3: Validate both files**

Run: `node -e "const y=require('js-yaml');for(const f of ['.github/workflows/android-screenshots-record.yml','.github/workflows/android.yml'])y.load(require('fs').readFileSync(f,'utf8'));console.log('yaml ok')"` (`js-yaml` is in the web workspace's node_modules; if not resolvable from the root, run it with `npx --yes js-yaml` or `python3 -I -c 'import yaml,sys; yaml.safe_load(open(sys.argv[1]))' <file>`).
Expected: `yaml ok`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/android-screenshots-record.yml .github/workflows/android.yml
git commit -m "feat(ci): record android screenshot references on the arkaik map on push to main"
```

---

### Task 4: The rule, the PR, the token

**Files:**
- Modify: `apps/android/CLAUDE.md` (the screenshot rules block)
- Modify: `.claude/skills/android-screenshot-gate/SKILL.md`

- [ ] **Step 1: One rule, twice**

In `apps/android/CLAUDE.md`, next to the never-bend screenshot rules, add:

```markdown
- **A mapped preview keeps its name, or the mapping moves with it.** `apps/android/arkaik-shots.json` maps Arkaik node ids to reference stems; `android.yml` fails when a stem no longer resolves to exactly one PNG. Renaming a preview, adding a variant to it, or deleting it means editing the mapping in the same PR. Only Light, default-size, English renders fill a view's slot; a variant goes in only when mapped to an acceptance id.
```

In `.claude/skills/android-screenshot-gate/SKILL.md`, add a short section "The Arkaik mapping" stating the same rule, the `--check` command, and that a re-baseline on `main` records itself through `android-screenshots-record.yml`.

- [ ] **Step 2: Gate and PR**

Run: `npm run test:arkaik-shots && node scripts/arkaik/android-shots-manifest.mjs --check && npm run lint --workspace=apps/android`
Expected: green (ktlint is unaffected; the check passes on the seed).

Push and open the PR with `Resolves #1438`, labels `feat`, `android`, `quality`, `no-lab-note`, and the milestone the maintainer confirms. Body lists the four files and the one manual step.

- [ ] **Step 3: The manual step (maintainer)**

Mint a `screenshot:append` token at `https://arkaik.app/settings/tokens` and add it as the pbbls repository secret `ARKAIK_SCREENSHOT_TOKEN`. After the merge, the first push to `main` touching the mapping records the seed; check the Android views on the hosted map show their shot with the caption "From CI".

- [ ] **Step 4: Build journal**

Drop one pebble with `node scripts/journal/drop-pebble.mjs` (`--valence highlight --size small --collection Android --collection "Quality & tooling"`).

---

## Self-review

- **Spec coverage.** D1 mapping file: Task 2. D2 one-file resolution: Task 1 (`resolveStem`, tested for 0, 1 and 2 matches). D3 Light default seed: Task 2 list. D4 raw URL: `buildManifest`. D5 script + `--check`: Task 1. D6 two workflow touches: Task 3. D7 token: Task 4 Step 3. D8 re-baseline self-recording: the `paths` filter in Task 3. Rules: Task 4 Step 1.
- **Placeholders.** None. The seed list is a draft the implementer confirms against the files; the candidate list names the file to read for each.
- **Type consistency.** `resolveStem(root, stem)`, `checkMapping(root, mapping)`, `buildManifest(root, mapping, sha, referenceDir)` are the signatures in the test and the script. The manifest entry shape `{ node_id, platform, url, detail }` is what `lib/services/graph/screenshots.ts` in arkaik accepts, with `commit` and `order` added by `arkaik shot` from the workflow's flags.
