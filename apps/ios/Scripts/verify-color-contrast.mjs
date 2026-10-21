#!/usr/bin/env node
// Asserts that googleCapsuleInk is a single-appearance colorset and that it clears
// WCAG AA against the pinned white capsule it inks. Runs on plain Node (no Xcode),
// so CI can gate the iOS half of the contrast fix without a macOS runner.
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const colorset = join(
  root,
  "Pebbles/Resources/Assets.xcassets/M3/googleCapsuleInk.colorset/Contents.json",
);

const channel = (v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4);
const luminance = ({ r, g, b }) =>
  0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
const ratio = (a, b) => {
  const [la, lb] = [luminance(a), luminance(b)];
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
};

const fail = (msg) => {
  console.error(`verify-color-contrast: ${msg}`);
  process.exit(1);
};

const parsed = JSON.parse(readFileSync(colorset, "utf8"));
const entries = parsed.colors ?? [];

if (entries.length !== 1) {
  fail(
    `googleCapsuleInk must hold exactly one universal entry, found ${entries.length}. ` +
      `Its ground is a pinned white capsule, so the ink must not follow the appearance.`,
  );
}
if (entries[0].appearances) {
  fail("googleCapsuleInk must not declare an appearances block (see GoogleCapsuleInk in ColorSchemes.kt).");
}

const { components } = entries[0].color;
const ink = {
  r: Number.parseInt(components.red, 16) / 255,
  g: Number.parseInt(components.green, 16) / 255,
  b: Number.parseInt(components.blue, 16) / 255,
};
const measured = ratio(ink, { r: 1, g: 1, b: 1 });

if (measured < 4.5) {
  fail(`googleCapsuleInk on white is ${measured.toFixed(3)}:1, below WCAG AA (4.5:1).`);
}
console.log(`verify-color-contrast: googleCapsuleInk on white = ${measured.toFixed(3)}:1 (AA ok)`);

// Tab bar pairs (#903 §8): the unselected label and the selected item on the
// bar ground, in every appearance the role colorsets declare.
const m3 = (role) =>
  JSON.parse(
    readFileSync(
      join(root, `Pebbles/Resources/Assets.xcassets/M3/${role}.colorset/Contents.json`),
      "utf8",
    ),
  ).colors;
const rgb = ({ color: { components: c } }) => ({
  r: Number.parseInt(c.red, 16) / 255,
  g: Number.parseInt(c.green, 16) / 255,
  b: Number.parseInt(c.blue, 16) / 255,
});
const look = (entry) => JSON.stringify(entry.appearances ?? []);

for (const [ink, ground] of [
  ["onSurfaceVariant", "surfaceContainer"],
  ["primary", "surfaceContainer"],
]) {
  const grounds = new Map(m3(ground).map((entry) => [look(entry), entry]));
  for (const entry of m3(ink)) {
    const bg = grounds.get(look(entry));
    if (!bg) fail(`${ink} declares appearance ${look(entry)} that ${ground} lacks.`);
    const measured = ratio(rgb(entry), rgb(bg));
    if (measured < 4.5) {
      fail(`${ink} on ${ground} ${look(entry)} is ${measured.toFixed(3)}:1, below WCAG AA (4.5:1).`);
    }
  }
  console.log(`verify-color-contrast: ${ink} on ${ground} is AA in every appearance`);
}
