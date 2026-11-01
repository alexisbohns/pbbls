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
