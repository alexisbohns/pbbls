/**
 * Longest name a moment takes on web (Capture and Edit moment, R5). Front-end
 * only: neither `moments.name` nor `save_moment` constrains length, and nothing
 * server-side enforces it.
 */
export const NAME_LIMIT = 40

export function clampName(raw: string): string {
  return raw.slice(0, NAME_LIMIT)
}
