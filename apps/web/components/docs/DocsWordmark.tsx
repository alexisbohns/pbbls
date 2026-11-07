import Link from "next/link"
import { useTranslations } from "next-intl"
import { cn } from "@/lib/utils"

/** "Pebbles" in spaced small caps, as on top of the Path; leads back to the app. */
export function DocsWordmark({ className }: { className?: string }) {
  const brand = useTranslations("brand")
  const t = useTranslations("docs")
  return (
    <Link
      href="/path"
      aria-label={t("back")}
      className={cn(
        "inline-block rounded-sm py-1 font-spine-sc text-[11px] font-[650] tracking-[.24em] text-m3-on-surface uppercase outline-none hover:text-m3-on-surface-variant focus-visible:ring-2 focus-visible:ring-ring",
        className,
      )}
    >
      {brand("name")}
    </Link>
  )
}
