/**
 * compose-and-write
 *
 * Given a pebble_id and an admin supabase client, load the pebble + its
 * resolved glyph source, run the engine, write render_svg/render_manifest/
 * render_version back to the row, and return the composed output.
 *
 * Shared by both compose-pebble (create flow) and backfill-pebble-render
 * (ops flow) so both produce byte-identical output for the same pebble_id.
 */

import type { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

import type { Stroke } from "./engine/types.ts";
import { createGlyphArtwork } from "./engine/glyph.ts";
import { composePebble } from "./engine/compose.ts";
import { getShape } from "./engine/shapes/index.ts";
import { intensityToSize, positivenessToValence } from "./engine/resolve.ts";

const RENDER_VERSION = "0.1.0";

export interface ComposedRender {
  render_svg: string;
  // deno-lint-ignore no-explicit-any
  render_manifest: any;
  render_version: string;
}

export async function composeAndWriteRender(
  admin: SupabaseClient,
  pebbleId: string,
): Promise<ComposedRender> {
  // ── Load pebble + first domain + domain's default glyph id ────────────
  //
  // PostgREST nested select returns pebble_domains in insertion order
  // (no explicit ordering column exists on the junction table). For iOS
  // pebbles this is a one-element array so "first" is unambiguous.
  const { data: pebble, error: loadError } = await admin
    .from("pebbles")
    .select(`
      id, intensity, positiveness, glyph_id,
      pebble_domains(
        domains(default_glyph_id)
      )
    `)
    .eq("id", pebbleId)
    .single();

  if (loadError || !pebble) {
    console.error("compose-and-write: load pebble failed:", loadError);
    throw new Error(`load pebble failed: ${loadError?.message ?? "not found"}`);
  }

  // ── Resolve glyph strokes per the priority rule ──────────────────────
  //   1. pebbles.glyph_id (if new-format view_box === "0 0 200 200")
  //   2. domain's default_glyph_id
  //   3. empty (engine produces a blank 200×200 glyph)
  let strokes: Stroke[] = [];

  if (pebble.glyph_id) {
    const { data: userGlyph, error: userGlyphError } = await admin
      .from("glyphs")
      .select("strokes, view_box")
      .eq("id", pebble.glyph_id)
      .single();
    if (userGlyphError) {
      console.error("compose-and-write: load user glyph failed:", userGlyphError);
    } else if (userGlyph && userGlyph.view_box === "0 0 200 200") {
      strokes = (userGlyph.strokes ?? []) as Stroke[];
    }
  }

  if (strokes.length === 0) {
    // deno-lint-ignore no-explicit-any
    const pebbleDomains = (pebble as any).pebble_domains as Array<{
      domains: { default_glyph_id: string | null } | null;
    }> | null;

    const defaultGlyphId = pebbleDomains?.[0]?.domains?.default_glyph_id ?? null;
    if (defaultGlyphId) {
      const { data: domainGlyph, error: domainGlyphError } = await admin
        .from("glyphs")
        .select("strokes")
        .eq("id", defaultGlyphId)
        .single();
      if (domainGlyphError) {
        console.error("compose-and-write: load domain glyph failed:", domainGlyphError);
      } else if (domainGlyph) {
        strokes = (domainGlyph.strokes ?? []) as Stroke[];
      }
    }
  }

  // ── Run the engine ───────────────────────────────────────────────────
  let svg: string;
  // deno-lint-ignore no-explicit-any
  let manifest: any;
  try {
    const artwork = createGlyphArtwork(strokes);
    const size = intensityToSize((pebble as { intensity: number }).intensity);
    const valence = positivenessToValence((pebble as { positiveness: number }).positiveness);
    const shapeSvg = getShape(size, valence);
    const output = composePebble({
      size,
      valence,
      shapeSvg,
      glyphSvg: artwork.svg,
    });
    svg = output.svg;
    manifest = output.manifest;
  } catch (err) {
    console.error("compose-and-write: engine error:", err);
    throw new Error(`engine error: ${err instanceof Error ? err.message : String(err)}`);
  }

  // ── Write render columns ─────────────────────────────────────────────
  const { error: updateError } = await admin
    .from("pebbles")
    .update({
      render_svg: svg,
      render_manifest: manifest,
      render_version: RENDER_VERSION,
    })
    .eq("id", pebbleId);

  if (updateError) {
    console.error("compose-and-write: render write-back failed:", updateError);
    throw new Error(`write-back failed: ${updateError.message}`);
  }

  return { render_svg: svg, render_manifest: manifest, render_version: RENDER_VERSION };
}
