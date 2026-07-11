/**
 * Regenerates `app/src/screenshotTest/kotlin/app/pbbls/android/PebbleSvgFixtures.kt`
 * — the authentic server-composed pebble SVGs used by the SVG-fidelity
 * screenshot grid (issue #531, umbrella design D10 / risk 2).
 *
 * Uses the real engine sources (the same code path as the compose-pebble Edge
 * Function) with the same synthetic glyph strokes as
 * `packages/supabase/scripts/smoke-test-engine.ts`.
 *
 * Run from the repo root (deno is not required; tsx handles the .ts imports):
 *   npx tsx apps/android/scripts/gen-pebble-svg-fixtures.ts \
 *     > apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/PebbleSvgFixtures.kt
 */
import { createGlyphArtwork } from "../../../packages/supabase/supabase/functions/_shared/engine/glyph.ts";
import { composePebble } from "../../../packages/supabase/supabase/functions/_shared/engine/compose.ts";
import { getShape } from "../../../packages/supabase/supabase/functions/_shared/engine/shapes/index.ts";
import type { PebbleSize, PebbleValence, Stroke } from "../../../packages/supabase/supabase/functions/_shared/engine/types.ts";

const SIZES: PebbleSize[] = ["small", "medium", "large"];
const VALENCES: PebbleValence[] = ["lowlight", "neutral", "highlight"];

// Same synthetic strokes as packages/supabase/scripts/smoke-test-engine.ts —
// a curve and a diagonal, enough to exercise Q/T path commands + scaling.
const SYNTHETIC_STROKES: Stroke[] = [
  { d: "M 20 100 Q 100 20 180 100 T 340 100", width: 3 },
  { d: "M 50 50 L 150 150", width: 3 },
];

// A fossil overlay for one variant (the optional 0.3-opacity middle layer).
const FOSSIL_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="200" height="200">
<path d="M 40 160 C 80 40 120 40 160 160" stroke="black" stroke-width="4" fill="none"/>
</svg>`;

const entries: Array<{ name: string; svg: string }> = [];

for (const size of SIZES) {
  for (const valence of VALENCES) {
    const artwork = createGlyphArtwork(SYNTHETIC_STROKES);
    const { svg } = composePebble({
      size,
      valence,
      shapeSvg: getShape(size, valence),
      glyphSvg: artwork.svg,
      // Fossil layer on exactly one variant for feature coverage.
      fossilSvg: size === "small" && valence === "neutral" ? FOSSIL_SVG : undefined,
    });
    const upper = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);
    entries.push({ name: `${size}${upper(valence)}`, svg });
  }
}

const kotlinConsts = entries
  .map(({ name, svg }) => {
    // Triple-quoted Kotlin raw string; SVG markup contains no triple quotes.
    return `    val ${name}: String =\n        \"\"\"\n${svg
      .split("\n")
      .map((l) => `        ${l}`)
      .join("\n")}\n        \"\"\".trimIndent()`;
  })
  .join("\n\n");

process.stdout.write(`// SVG path data lines are machine-generated and cannot wrap.
@file:Suppress("ktlint:standard:max-line-length")

package app.pbbls.android

/**
 * Authentic server-composed pebble SVGs for the SVG-fidelity spike (issue
 * #531, umbrella design D10 / risk 2). Generated from the real engine
 * sources (\`packages/supabase/supabase/functions/_shared/engine/\`) with the
 * same synthetic glyph strokes as \`scripts/smoke-test-engine.ts\`, covering
 * all 9 size x valence shape seeds - including \`medium-neutral\` (defs +
 * clipPath + clip-path url ref) and \`large-lowlight\` (fill-rule/clip-rule) -
 * plus one variant with the optional fossil layer (smallNeutral).
 *
 * Do not hand-edit; regenerate with
 * \`npx tsx apps/android/scripts/gen-pebble-svg-fixtures.ts\` (see that file).
 */
object PebbleSvgFixtures {
${kotlinConsts}

    val all: List<Pair<String, String>> =
        listOf(
${entries.map((e) => `            "${e.name}" to ${e.name},`).join("\n")}
        )
}
`);
