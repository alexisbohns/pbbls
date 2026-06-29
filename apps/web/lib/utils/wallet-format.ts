import type { KarmaEvent } from "@/lib/types"

// i18n key suffix for a movement's reason (consumed under the `wallet.reason` ns).
// Returns the literal union (not `string`) so next-intl can type-check the
// resulting `reason.${...}` message key.
export function reasonKey(reason: KarmaEvent["reason"]): KarmaEvent["reason"] {
  return reason
}

// Signed, display-ready amount: "+3" for credits in, "-50" for spends.
export function signedAmount(delta: number): string {
  return delta > 0 ? `+${delta}` : `${delta}`
}

// Direction for non-colour-only cues / icon choice.
export function direction(delta: number): "in" | "out" {
  return delta >= 0 ? "in" : "out"
}
