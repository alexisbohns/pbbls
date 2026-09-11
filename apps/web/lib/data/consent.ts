import type { ConsentKind, ConsentSource } from "@/lib/config/consent"

/** One consent act, as stored in `public.user_consents`. */
export type ConsentRow = {
  id: string
  kind: ConsentKind
  document_version: string
  source: ConsentSource
  granted_at: string
  /** Set when the user withdrew it (Art. 7(3)). */
  withdrawn_at: string | null
  /** Set when a newer policy version replaced it. Distinct from withdrawal. */
  superseded_at: string | null
}

/**
 * The one live consent of a kind, or null.
 *
 * The database enforces at most one (partial unique index on user + kind where
 * neither timestamp is set), so returning the first match is not a guess.
 */
export function activeConsent(rows: ConsentRow[], kind: ConsentKind): ConsentRow | null {
  return (
    rows.find(
      (r) => r.kind === kind && r.withdrawn_at === null && r.superseded_at === null,
    ) ?? null
  )
}
