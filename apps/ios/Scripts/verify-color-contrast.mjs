#!/usr/bin/env node
// Asserts that SystemOnLight is a single-appearance colorset and that it clears
// WCAG AA against the pinned white capsule it inks. Runs on plain Node (no Xcode),
// so CI can gate the iOS half of the contrast fix without a macOS runner.
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const colorset = join(
  root,
  "Pebbles/Resources/Assets.xcassets/SystemOnLight.colorset/Contents.json",
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
    `SystemOnLight must hold exactly one universal entry, found ${entries.length}. ` +
      `Its ground is a pinned white capsule, so the ink must not follow the appearance.`,
  );
}
if (entries[0].appearances) {
  fail("SystemOnLight must not declare an appearances block (see SystemPalette.onLight).");
}

const { components } = entries[0].color;
const ink = {
  r: Number.parseInt(components.red, 16) / 255,
  g: Number.parseInt(components.green, 16) / 255,
  b: Number.parseInt(components.blue, 16) / 255,
};
const measured = ratio(ink, { r: 1, g: 1, b: 1 });

if (measured < 4.5) {
  fail(`SystemOnLight on white is ${measured.toFixed(3)}:1, below WCAG AA (4.5:1).`);
}
console.log(`verify-color-contrast: SystemOnLight on white = ${measured.toFixed(3)}:1 (AA ok)`);
