import { toast } from "sonner"
import { GlyphPurchasePill } from "@/components/activity/GlyphPurchasePill"

// Stable id → a new purchase replaces the current pill rather than stacking.
const GLYPH_ACTIVITY_ID = "glyph-activity"

/** Fire a glanceable "Glyph unlocked · −N karma" pill after a purchase. */
export function notifyGlyphPurchased(glyphId: string, name: string, amount: number): void {
  if (amount <= 0) return
  toast.custom(
    (id) => (
      <div className="flex w-full justify-center">
        <GlyphPurchasePill toastId={id} glyphId={glyphId} name={name} amount={amount} />
      </div>
    ),
    { id: GLYPH_ACTIVITY_ID, duration: 3000 },
  )
}
