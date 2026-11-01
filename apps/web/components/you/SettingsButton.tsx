import Link from "next/link"
import { useTranslations } from "next-intl"
import { Slider } from "@pbbls/icons"
import { Button } from "@/components/ui/button"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"

/**
 * You's way to Settings (#1384): a standard icon button, not a drawn stone.
 * Chrome stays platform-native on every surface (iOS a toolbar button, Android
 * a top-app-bar icon button); the brand's stones and cards are for content.
 * A 44 px target, named by its label.
 */
export function SettingsButton({ className }: { className?: string }) {
  const t = useTranslations("you")
  return (
    <Button
      variant="ghost"
      size="icon"
      nativeButton={false}
      render={<Link href="/settings" />}
      aria-label={t("settings")}
      className={cn("size-11 rounded-full text-m3-on-surface", className)}
    >
      <Slider className={PBBLS_ICON_SIZE.toolbar} />
    </Button>
  )
}
