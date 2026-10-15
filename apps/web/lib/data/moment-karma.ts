/**
 * The karma one write earned (R13): the pebble's own events after minus
 * before. Null on either side (a failed read) answers 0, so nothing is shown.
 */
export function karmaDelta(before: number | null, after: number | null): number {
  if (before === null || after === null) return 0
  return after - before
}
