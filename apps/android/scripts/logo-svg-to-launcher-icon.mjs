#!/usr/bin/env node
/**
 * Generate the adaptive launcher-icon layers from the iOS brand mark (#846).
 *
 * Source of truth is `apps/ios/Pebbles/Resources/pbbls-logo-loader.svg` — the
 * same stroked line art the iOS app icon renders and the loader draws on. This
 * script re-expresses it as two Android VectorDrawables (foreground + the
 * monochrome layer themed icons tint) so the two surfaces can never drift:
 * re-run it instead of hand-editing the XML.
 *
 *   node apps/android/scripts/logo-svg-to-launcher-icon.mjs
 *
 * Geometry: the mark's ink box (path bounds widened by half a stroke) is scaled
 * to `ART_DP` inside the 108dp adaptive canvas and centred, so it stays within
 * the 66dp circle every launcher mask is guaranteed to show. The group
 * transform carries the scale, which means stroke widths scale with it and the
 * path data stays byte-identical to the SVG's.
 */
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const SRC = resolve(here, "../../ios/Pebbles/Resources/pbbls-logo-loader.svg");
const OUT = resolve(here, "../app/src/main/res/drawable");

/** Adaptive-icon canvas, and the mask-safe circle the mark has to fit inside. */
const CANVAS_DP = 108;
const ART_DP = 66;
/** iOS app-icon colours (`AppIcon.appiconset/pbbls.png`), sampled exactly. */
const INK = "#F8F0F0";
/** Themed icons are re-tinted by the system; only this layer's alpha matters. */
const MONO_INK = "#000000";

const svg = readFileSync(SRC, "utf8");

/** Attribute reader that does not let `d="…"` match inside `id="…"`. */
const attr = (tag, name) =>
  tag.match(new RegExp(`(?<![\\w-])${name}="([^"]*)"`))?.[1] ?? null;

const paths = [...svg.matchAll(/<path\b([^>]*)\/>/gs)].map((m) => ({
  id: attr(m[1], "id"),
  d: attr(m[1], "d"),
  stroke: attr(m[1], "stroke"),
  fill: attr(m[1], "fill"),
  strokeWidth: Number(attr(m[1], "stroke-width") ?? 0),
  cap: attr(m[1], "stroke-linecap"),
  join: attr(m[1], "stroke-linejoin"),
}));
if (paths.length === 0) throw new Error(`no <path> elements in ${SRC}`);

/**
 * Sample every path into points. The mark only uses absolute M/L/C/Z; anything
 * else means the export changed and the bounds below would silently lie, so it
 * throws rather than guessing.
 */
function samplePoints(d) {
  const tokens = d.match(/[A-Za-z]|-?\d*\.?\d+(?:e-?\d+)?/g) ?? [];
  const points = [];
  let i = 0;
  let cmd = null;
  let cur = [0, 0];
  let start = [0, 0];
  const num = () => Number(tokens[i++]);
  while (i < tokens.length) {
    if (/[A-Za-z]/.test(tokens[i])) cmd = tokens[i++];
    if (cmd === "M" || cmd === "L") {
      cur = [num(), num()];
      points.push(cur);
      if (cmd === "M") {
        start = cur;
        cmd = "L"; // implicit lineto for repeated coordinate pairs
      }
    } else if (cmd === "C") {
      const [x1, y1, x2, y2, x, y] = [num(), num(), num(), num(), num(), num()];
      const [x0, y0] = cur;
      for (let k = 1; k <= 20; k += 1) {
        const t = k / 20;
        const u = 1 - t;
        points.push([
          u ** 3 * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t ** 3 * x,
          u ** 3 * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t ** 3 * y,
        ]);
      }
      cur = [x, y];
    } else if (cmd === "Z" || cmd === "z") {
      cur = start;
    } else {
      throw new Error(`unsupported path command "${cmd}" — update this script`);
    }
  }
  return points;
}

const box = { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity };
for (const p of paths) {
  const pad = p.strokeWidth / 2;
  for (const [x, y] of samplePoints(p.d)) {
    box.minX = Math.min(box.minX, x - pad);
    box.maxX = Math.max(box.maxX, x + pad);
    box.minY = Math.min(box.minY, y - pad);
    box.maxY = Math.max(box.maxY, y + pad);
  }
}
const inkW = box.maxX - box.minX;
const inkH = box.maxY - box.minY;
const scale = ART_DP / Math.max(inkW, inkH);
const translateX = (CANVAS_DP - inkW * scale) / 2 - box.minX * scale;
const translateY = (CANVAS_DP - inkH * scale) / 2 - box.minY * scale;

/**
 * VectorDrawable group transforms are `translate ∘ scale` with the translation
 * applied last, so translateX/Y are viewport units, not group-local ones.
 */
function render(ink, header) {
  const lines = [
    '<?xml version="1.0" encoding="utf-8"?>',
    header,
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
    `    android:width="${CANVAS_DP}dp"`,
    `    android:height="${CANVAS_DP}dp"`,
    `    android:viewportWidth="${CANVAS_DP}"`,
    `    android:viewportHeight="${CANVAS_DP}">`,
    "    <group",
    `        android:scaleX="${scale.toFixed(6)}"`,
    `        android:scaleY="${scale.toFixed(6)}"`,
    `        android:translateX="${translateX.toFixed(4)}"`,
    `        android:translateY="${translateY.toFixed(4)}">`,
  ];
  for (const p of paths) {
    lines.push("        <path");
    if (p.id) lines.push(`            android:name="${p.id}"`);
    if (p.fill && p.fill !== "none") lines.push(`            android:fillColor="${ink}"`);
    if (p.stroke && p.stroke !== "none") {
      lines.push(`            android:strokeColor="${ink}"`);
      lines.push(`            android:strokeWidth="${p.strokeWidth}"`);
      if (p.cap) lines.push(`            android:strokeLineCap="${p.cap}"`);
      if (p.join) lines.push(`            android:strokeLineJoin="${p.join}"`);
    }
    lines.push(`            android:pathData="${p.d}" />`);
  }
  lines.push("    </group>", "</vector>", "");
  return lines.join("\n");
}

const generated = (extra) =>
  `<!--\n  GENERATED by apps/android/scripts/logo-svg-to-launcher-icon.mjs (#846) from\n  apps/ios/Pebbles/Resources/pbbls-logo-loader.svg. Do not hand-edit — re-run\n  the script when the brand mark changes.\n${extra}-->`;

writeFileSync(
  resolve(OUT, "ic_launcher_foreground.xml"),
  render(
    INK,
    generated(
      "\n  The adaptive-icon foreground: the mark in the iOS icon's cream, sized to\n" +
        `  ${ART_DP}dp inside the ${CANVAS_DP}dp canvas so no launcher mask can clip it.\n`,
    ),
  ),
);
writeFileSync(
  resolve(OUT, "ic_launcher_monochrome.xml"),
  render(
    MONO_INK,
    generated(
      "\n  The themed-icon (monochrome) layer: identical geometry, drawn opaque so\n" +
        "  the system can re-tint it from the wallpaper palette.\n",
    ),
  ),
);

console.log(
  `ink box ${inkW.toFixed(2)}×${inkH.toFixed(2)} → scale ${scale.toFixed(6)}, ` +
    `translate (${translateX.toFixed(3)}, ${translateY.toFixed(3)})`,
);
console.log(`wrote ${OUT}/ic_launcher_foreground.xml`);
console.log(`wrote ${OUT}/ic_launcher_monochrome.xml`);
