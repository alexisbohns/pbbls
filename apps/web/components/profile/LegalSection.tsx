"use client"

import Link from "next/link"
import { Scale, ChevronRight } from "lucide-react"
import { useTranslations } from "next-intl"

const LEGAL_LINKS = [
  { key: "legalNotice" as const, href: "/docs/legal-notice" },
  { key: "terms" as const,       href: "/docs/terms" },
  { key: "privacy" as const,     href: "/docs/privacy" },
]

export function LegalSection() {
  const t = useTranslations("legal")
  return (
    <nav aria-label={t("navAria")}>
      <ul className="divide-y divide-border rounded-xl border border-border">
        {LEGAL_LINKS.map((link) => (
          <li key={link.href}>
            <Link
              href={link.href}
              className="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-muted/50 first:rounded-t-xl last:rounded-b-xl"
            >
              <Scale className="size-5 text-muted-foreground" aria-hidden />
              <span className="flex-1 text-sm font-medium">{t(link.key)}</span>
              <ChevronRight className="size-4 text-muted-foreground" aria-hidden />
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  )
}
