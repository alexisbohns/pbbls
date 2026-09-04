/**
 * The peer in a canonical connection row (M49: one ordered row, user_a <
 * user_b). RLS already scopes the row to its two members, so a non-member
 * result can only mean a stale session — callers treat null as "not mine".
 */
export function peerIdOf(
  row: { user_a: string; user_b: string },
  selfId: string,
): string | null {
  if (row.user_a === selfId) return row.user_b
  if (row.user_b === selfId) return row.user_a
  return null
}
