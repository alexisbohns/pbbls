import type { Pebble, Mark } from "@/lib/types"
import type { PebbleParams, Glyph } from "./types"
import { EMOTIONS } from "@/lib/config/emotions"

/** 28 days in milliseconds. */
const RETROACTIVE_THRESHOLD_MS = 28 * 24 * 60 * 60 * 1000

/** Fallback color when the emotion ID is unrecognized. */
const NEUTRAL_GRAY = "#9CA3AF"

function resolveEmotionColor(emotionId: string): string {
  const emotion = EMOTIONS.find((e) => e.id === emotionId)
  return emotion?.color ?? NEUTRAL_GRAY
}

function isRetroactive(happenedAt: string, createdAt: string): boolean {
  const elapsed =
    new Date(createdAt).getTime() - new Date(happenedAt).getTime()
  return elapsed > RETROACTIVE_THRESHOLD_MS
}

function extractGlyph(mark: Mark | null): Glyph | null {
  if (!mark) return null
  return { strokes: mark.strokes, viewBox: mark.viewBox }
}

/**
 * Transforms a domain Pebble (and its optional Mark) into the engine's
 * input contract. Pure, deterministic, and side-effect-free.
 */
export function toPebbleParams(
  pebble: Pebble,
  mark: Mark | null,
): PebbleParams {
  return {
    intensity: pebble.intensity,
    positiveness: pebble.positiveness,
    emotionColor: resolveEmotionColor(pebble.emotion_id),
    retroactive: isRetroactive(pebble.happened_at, pebble.created_at),
    glyph: extractGlyph(mark),
  }
}
