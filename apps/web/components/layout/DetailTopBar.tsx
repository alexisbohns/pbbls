"use client"

import type { ReactNode } from "react"
import Link from "next/link"
import { ChevronLeft, X } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { DETAIL_TITLE_ID, useDetailPane } from "@/lib/hooks/useDetailPane"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"

type DetailTopBarProps = {
  title: string
  /** A detail inside a detail (the changelog in the Lab) goes back to it at every size. */
  backHref?: string
  /** Trailing actions: icon buttons or a menu. */
  actions?: ReactNode
}

/**
 * A detail's M3 small top app bar (#1384, two-area spec D5): close in the
 * right area, back in the overlay (the same action, the platform's word for
 * it), the title as the detail's heading (`h2`, below the left area's `h1`),
 * then the page's actions. It stays at the top while the detail scrolls.
 */
export function DetailTopBar({ title, backHref, actions }: DetailTopBarProps) {
  const t = useTranslations("detail")
  const { wide, close } = useDetailPane()
  const control = "size-11 shrink-0 rounded-full text-m3-on-surface"

  return (
    <header className="sticky top-0 z-10 flex min-h-16 items-center gap-1 bg-background px-1 pt-[var(--safe-area-top)]">
      {backHref ? (
        <Button variant="ghost" size="icon" nativeButton={false} render={<Link href={backHref} />} aria-label={t("back")} className={control}>
          <ChevronLeft className={PBBLS_ICON_SIZE.toolbar} />
        </Button>
      ) : (
        <Button variant="ghost" size="icon" onClick={close} aria-label={wide ? t("close") : t("back")} className={control}>
          {wide ? <X className={PBBLS_ICON_SIZE.toolbar} /> : <ChevronLeft className={PBBLS_ICON_SIZE.toolbar} />}
        </Button>
      )}
      <h2 id={DETAIL_TITLE_ID} tabIndex={-1} className="m3-type-title-large min-w-0 flex-1 truncate px-1 text-m3-on-surface outline-none">
        {title}
      </h2>
      {actions}
    </header>
  )
}
