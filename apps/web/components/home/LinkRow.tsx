import type { ComponentType } from "react"
import Link from "next/link"
import { ChevronRight } from "lucide-react"
import { IconChip } from "./IconChip"

type LinkRowProps = {
  href: string
  /** The chip's stone seed, so each row's stone has its own outline. */
  seed: string
  icon: ComponentType<{ className?: string }> | null
  title: string
  line: string
}

/**
 * A list row that leads somewhere (#1384, spec D8, D14, D17): a stone chip,
 * the title over its line, a chevron. The whole row is the link; the chip and
 * the chevron are decoration. Home's About and You's Glyphs share it.
 */
export function LinkRow({ href, seed, icon, title, line }: LinkRowProps) {
  return (
    <Link
      href={href}
      className="flex items-center gap-3.5 rounded-xl px-1 py-2.5 outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <IconChip seed={seed} icon={icon} />
      <span className="min-w-0 flex-1">
        <span className="pb-type-row-title block text-m3-on-surface">{title}</span>
        <span className="pb-type-row-line mt-0.5 block text-m3-on-surface-variant">{line}</span>
      </span>
      <ChevronRight aria-hidden="true" className="size-[18px] shrink-0 text-m3-outline" />
    </Link>
  )
}
