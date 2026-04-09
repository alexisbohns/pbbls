import Link from "next/link"
import { Scale, ChevronRight } from "lucide-react"

const LEGAL_LINKS = [
  { label: "Legal Notice", href: "/docs/legal-notice" },
  { label: "Terms of Service", href: "/docs/terms" },
  { label: "Privacy Policy", href: "/docs/privacy" },
]

export function LegalSection() {
  return (
    <nav aria-label="Legal">
      <ul className="divide-y divide-border rounded-xl border border-border">
        {LEGAL_LINKS.map((link) => (
          <li key={link.href}>
            <Link
              href={link.href}
              className="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-muted/50 first:rounded-t-xl last:rounded-b-xl"
            >
              <Scale className="size-5 text-muted-foreground" aria-hidden />
              <span className="flex-1 text-sm font-medium">{link.label}</span>
              <ChevronRight className="size-4 text-muted-foreground" aria-hidden />
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  )
}
