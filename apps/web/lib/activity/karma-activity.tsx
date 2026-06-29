import { toast } from "sonner"
import { KarmaActivityPill } from "@/components/activity/KarmaActivityPill"
import type { KarmaReason } from "@/lib/types"

// Stable id → a new credit replaces the current pill rather than stacking.
const KARMA_ACTIVITY_ID = "karma-activity"

/**
 * Fire a glanceable "+N karma" activity pill. No-op for non-positive amounts
 * (deletions/clawbacks must stay silent). Feature-agnostic: any credit source
 * calls this with the amount and the reason.
 */
export function notifyKarma(amount: number, reason: KarmaReason): void {
  if (amount <= 0) return
  toast.custom(
    (id) => <KarmaActivityPill toastId={id} amount={amount} reason={reason} />,
    { id: KARMA_ACTIVITY_ID, duration: 3000 },
  )
}
