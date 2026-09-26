#!/usr/bin/env node
/**
 * Generate the adaptive launcher-icon layers (#846) and the bare Welcome mark
 * (#856) from the iOS brand mark.
 *
 * Source of truth is `apps/ios/Pebbles/Resources/pbbls-logo-loader.svg` — the
 * same stroked line art the iOS app icon renders and the loader draws on. This
 * script re-expresses it as two Android VectorDrawables (foreground + the
 * monochrome layer themed icons tint) so the two surfaces can never drift:
 * re-run it instead of hand-editing the XML.
 *
 *   node apps/android/scripts/logo-svg-to-launcher-icon.mjs
 *
 * Geometry: the mark is centred on its ink box (path bounds widened by half a
 * stroke) and scaled so its farthest ink sits `ART_RADIUS_DP` from the centre
 * of the 108dp adaptive canvas — inside the 66dp circle every launcher mask is
 * guaranteed to show, with room to spare. Fitting the box instead (the #846
 * geometry, 66dp a side) put the pebble's bulges 37.8dp out, and round and
 * squircle masks cut them off (#856). The group
 * transform carries the scale, which means stroke widths scale with it and the
 * path data stays byte-identical to the SVG's.
 */
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const SRC = resolve(here, "../../ios/Pebbles/Resources/pbbls-logo-loader.svg");
const OUT = resolve(here, "../app/src/main/res/drawable");

/**
 * Adaptive-icon canvas, and how far from its centre the ink may reach. 33dp is
 * the mask-safe radius; 28dp keeps the mark near Material's 48dp product-icon
 * keyline, so it reads at the same weight as the icons around it.
 */
const CANVAS_DP = 108;
const ART_RADIUS_DP = 28;
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
// Radius of the ink around the ink box's centre, strokes included.
const cx = (box.minX + box.maxX) / 2;
const cy = (box.minY + box.maxY) / 2;
let inkRadius = 0;
for (const p of paths) {
  for (const [x, y] of samplePoints(p.d)) {
    inkRadius = Math.max(inkRadius, Math.hypot(x - cx, y - cy) + p.strokeWidth / 2);
  }
}
const scale = ART_RADIUS_DP / inkRadius;
const translateX = CANVAS_DP / 2 - cx * scale;
const translateY = CANVAS_DP / 2 - cy * scale;

/**
 * VectorDrawable group transforms are `translate ∘ scale` with the translation
 * applied last, so translateX/Y are viewport units, not group-local ones.
 */
const launcherGeometry = {
  width: CANVAS_DP,
  height: CANVAS_DP,
  scale,
  translateX,
  translateY,
};

/**
 * The bare mark (#856): the viewport is the ink box itself, so the drawable has
 * no adaptive-icon padding and fills whatever box Compose gives it. Welcome
 * draws it in place of the retired Rive logo, tinted from the theme.
 */
const MARK_DP = 120;
const markScale = MARK_DP / Math.max(inkW, inkH);
const markGeometry = {
  width: inkW * markScale,
  height: inkH * markScale,
  scale: markScale,
  translateX: -box.minX * markScale,
  translateY: -box.minY * markScale,
};

function render(ink, header, geo = launcherGeometry) {
  const lines = [
    '<?xml version="1.0" encoding="utf-8"?>',
    header,
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
    `    android:width="${geo.width.toFixed(2).replace(/\.00$/, "")}dp"`,
    `    android:height="${geo.height.toFixed(2).replace(/\.00$/, "")}dp"`,
    `    android:viewportWidth="${geo.width.toFixed(2).replace(/\.00$/, "")}"`,
    `    android:viewportHeight="${geo.height.toFixed(2).replace(/\.00$/, "")}">`,
    "    <group",
    `        android:scaleX="${geo.scale.toFixed(6)}"`,
    `        android:scaleY="${geo.scale.toFixed(6)}"`,
    `        android:translateX="${geo.translateX.toFixed(4)}"`,
    `        android:translateY="${geo.translateY.toFixed(4)}">`,
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
        `  within ${ART_RADIUS_DP}dp of the ${CANVAS_DP}dp canvas's centre so no launcher mask can clip it.\n`,
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

writeFileSync(
  resolve(OUT, "pbbls_logo.xml"),
  render(
    MONO_INK,
    generated(
      "\n  The bare mark for Welcome (#856, replacing the Rive logo): cropped to its\n" +
        "  ink box, drawn opaque so Compose can tint it with colorScheme.primary.\n",
    ),
    markGeometry,
  ),
);

console.log(
  `ink box ${inkW.toFixed(2)}×${inkH.toFixed(2)} → scale ${scale.toFixed(6)}, ` +
    `translate (${translateX.toFixed(3)}, ${translateY.toFixed(3)})`,
);
console.log(`wrote ${OUT}/ic_launcher_foreground.xml`);
console.log(`wrote ${OUT}/ic_launcher_monochrome.xml`);
console.log(`wrote ${OUT}/pbbls_logo.xml`);
