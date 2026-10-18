#!/usr/bin/env node
/**
 * Writes design/pbbls-m3_evo-theme.json as the v3 export (#1118): the input
 * export with the seventeen neutral-derived roles of all six schemes
 * recomputed on the Spine paper, every other role left byte-identical.
 *
 * Usage, from apps/android:
 *     npm run generate:theme            # rewrites design/pbbls-m3_evo-theme.json in place
 *     node scripts/generate-theme-export.mjs in.json out.json
 *
 * Then run scripts/generate-color-schemes.py and, from the repo root,
 * `npm run generate:m3 --workspace=apps/web` (apps/android/CLAUDE.md, Theme).
 * A Theme Builder re-export also resets the error roles and drops Sand; redo those by hand first (#990), then run this.
 *
 * The recipe (spec 2026-10-18-android-spine-brand-scheme-design.md):
 *   - neutral palette hue 100 / chroma 4; in the dark schemes the surface
 *     tiers read instead from hue 275 / chroma 3 (the web's night ground) while
 *     the inks stay warm — contrast depends on tone only, so no ratio moves;
 *   - neutral-variant palette hue 92 / chroma 8;
 *   - primary / secondary / tertiary / error / Sand: untouched (v2 verbatim).
 * Idempotent: running it on its own output changes nothing.
 */
import { readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  DynamicScheme,
  Hct,
  MaterialDynamicColors,
  TonalPalette,
  argbFromHex,
  hexFromArgb,
} from "@material/material-color-utilities";

const here = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT = path.resolve(here, "../design/pbbls-m3_evo-theme.json");
const SRC = process.argv[2] ? path.resolve(process.argv[2]) : DEFAULT;
const DST = process.argv[3] ? path.resolve(process.argv[3]) : SRC;

const ISSUE = "#1118";
const DATE = "2026-10-18"; // the recipe's date, fixed so re-runs are byte-identical

/** The roles M3 derives from the neutral and neutral-variant palettes. */
const NEUTRAL_ROLES = [
  "background", "onBackground",
  "surface", "surfaceDim", "surfaceBright",
  "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
  "onSurface", "surfaceVariant", "onSurfaceVariant",
  "outline", "outlineVariant",
  "inverseSurface", "inverseOnSurface",
];
/** The dark schemes take these from the cool palette (spec D3). */
const SURFACE_TIERS = new Set([
  "background", "surface", "surfaceDim", "surfaceBright",
  "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
]);
const LEVELS = {
  "light": { isDark: false, contrastLevel: 0 },
  "light-medium-contrast": { isDark: false, contrastLevel: 0.5 },
  "light-high-contrast": { isDark: false, contrastLevel: 1 },
  "dark": { isDark: true, contrastLevel: 0 },
  "dark-medium-contrast": { isDark: true, contrastLevel: 0.5 },
  "dark-high-contrast": { isDark: true, contrastLevel: 1 },
};
const TONAL_SPOT = 2; // Variant.TONAL_SPOT; the enum is not exported from the package root in 0.3.0

const WARM = TonalPalette.fromHueAndChroma(100, 4);
const COOL = TonalPalette.fromHueAndChroma(275, 3);
const NEUTRAL_VARIANT = TonalPalette.fromHueAndChroma(92, 8);

const d = JSON.parse(readFileSync(SRC, "utf8"));
if (typeof d.seed !== "string") throw new Error("export has no seed");
const seed = Hct.fromInt(argbFromHex(d.seed));

// Tonal Spot's own primary/secondary/tertiary palettes, so the neutral-derived
// roles are computed in the same scheme they would be in Theme Builder. Only
// the neutral-derived roles are read from these schemes.
const scheme = ({ isDark, contrastLevel }, neutral) =>
  new DynamicScheme({
    sourceColorArgb: seed.toInt(),
    variant: TONAL_SPOT,
    isDark,
    contrastLevel,
    primaryPalette: TonalPalette.fromHueAndChroma(seed.hue, 36),
    secondaryPalette: TonalPalette.fromHueAndChroma(seed.hue, 16),
    tertiaryPalette: TonalPalette.fromHueAndChroma((seed.hue + 60) % 360, 24),
    neutralPalette: neutral,
    neutralVariantPalette: NEUTRAL_VARIANT,
  });

const before = JSON.parse(JSON.stringify(d.schemes));
let changed = 0;
for (const [key, level] of Object.entries(LEVELS)) {
  if (!d.schemes[key]) throw new Error(`export has no scheme "${key}"`);
  const warm = scheme(level, WARM);
  const cool = scheme(level, COOL);
  for (const role of NEUTRAL_ROLES) {
    const from = level.isDark && SURFACE_TIERS.has(role) ? cool : warm;
    const hex = hexFromArgb(MaterialDynamicColors[role].getArgb(from)).toUpperCase();
    if (d.schemes[key][role] !== hex) changed++;
    d.schemes[key][role] = hex;
  }
}

// The palettes block is Theme Builder's record of the key palettes; keep it
// honest for the two we re-seed. The dark surface tiers' cool palette has no
// slot here — a single `neutral` cannot express it — so the description says so.
const PALETTES = { "neutral": WARM, "neutral-variant": NEUTRAL_VARIANT };
for (const [name, palette] of Object.entries(PALETTES)) {
  if (!d.palettes?.[name]) throw new Error(`export has no palette "${name}"`);
  for (const tone of Object.keys(d.palettes[name])) {
    d.palettes[name][tone] = hexFromArgb(palette.tone(Number(tone))).toUpperCase();
  }
}

// Guard: a role this script does not own must not move. This is what makes
// "v2 verbatim" a checked claim rather than a comment.
for (const [key, roles] of Object.entries(before)) {
  for (const [role, hex] of Object.entries(roles)) {
    if (!NEUTRAL_ROLES.includes(role) && d.schemes[key][role] !== hex) {
      throw new Error(`guard: ${key}.${role} moved from ${hex} to ${d.schemes[key][role]}`);
    }
  }
}

d.description = [
  "TYPE: CUSTOM",
  `Pebbles M3-evo export v3 (${ISSUE}): v2 with the neutral-derived roles recomputed on the Spine paper.`,
  "Neutral palette hue 100 / chroma 4 (dark surface tiers: hue 275 / chroma 3, which the single palettes.neutral below cannot express), neutral-variant hue 92 / chroma 8;",
  "primary, secondary, tertiary, error and Sand are v2 verbatim; palettes.neutral and palettes.neutral-variant follow the recipe.",
  "A Theme Builder re-export resets the error roles to the seed's and drops Sand: redo those by hand first, as #990 did, then run",
  "apps/android/scripts/generate-theme-export.mjs (@material/material-color-utilities 0.3.0), or the neutrals revert to the seed's;",
  "then scripts/generate-color-schemes.py and, from the repo root, npm run generate:m3 --workspace=apps/web.",
  `export v3 ${DATE}, generated with material-color-utilities (${ISSUE})`,
].join("\n");

writeFileSync(DST, JSON.stringify(d, null, 2) + "\n");
console.log(`[generate:theme] ${changed} of ${NEUTRAL_ROLES.length * Object.keys(LEVELS).length} neutral role values changed → ${path.relative(process.cwd(), DST)}`);
