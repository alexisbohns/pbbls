import { useTranslations } from "next-intl"
import { RippleBadge } from "@/components/profile/ripples/RippleBadge"
import { cn } from "@/lib/utils"

/**
 * "Ripples Level 3" beside the ripple badge at one line's height (#1384 D15).
 * The badge drops its digit there: the words state the level.
 */
export function RipplesLine({ level, activeToday, className }: { level: number; activeToday: boolean; className?: string }) {
  const t = useTranslations("you")
  return (
    <div className={cn("pb-type-label flex items-center justify-center gap-2 text-m3-on-surface", className)}>
      <RippleBadge level={level} activeToday={activeToday} className="size-6" showLevel={false} />
      <span>{t("ripples", { level })}</span>
    </div>
  )
}
