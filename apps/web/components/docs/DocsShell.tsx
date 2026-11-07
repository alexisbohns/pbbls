"use client"

import type { ReactNode } from "react"
import { DocsMobileBar } from "@/components/docs/DocsMobileBar"
import { DocsNav } from "@/components/docs/DocsNav"
import { DocsToc } from "@/components/docs/DocsToc"
import { DocsWordmark } from "@/components/docs/DocsWordmark"
import { LocaleToggle } from "@/components/docs/LocaleToggle"
import { useActiveHeading } from "@/lib/hooks/useActiveHeading"
import type { DocsPageMeta, DocsTocItem } from "@/lib/docs/types"

type DocsShellProps = {
  pages: readonly DocsPageMeta[]
  current?: string
  title: string
  toc?: readonly DocsTocItem[]
  children: ReactNode
}

const NO_TOC: readonly DocsTocItem[] = []

/**
 * The docs' frame. From 1024 px, three columns: the main navigation, the page,
 * the page's own contents; both sides stay put while the page scrolls. Below,
 * one column under a sticky bar that carries the contents and the menu.
 */
export function DocsShell({ pages, current, title, toc = NO_TOC, children }: DocsShellProps) {
  const active = useActiveHeading(toc.map((item) => item.id))

  return (
    <div className="mx-auto w-full max-w-[1400px] px-4 lg:grid lg:grid-cols-[19rem_minmax(0,1fr)_15rem] lg:gap-12 lg:pr-8 lg:pl-3 xl:gap-16">
      <aside className="hidden lg:sticky lg:top-0 lg:flex lg:max-h-dvh lg:flex-col lg:gap-6 lg:self-start lg:overflow-y-auto lg:[scrollbar-width:none] lg:[&::-webkit-scrollbar]:hidden lg:[&>*]:shrink-0 lg:pt-6 lg:pb-12">
        <DocsWordmark className="ml-1.5 self-start" />
        <LocaleToggle className="ml-1.5" />
        <DocsNav pages={pages} current={current} />
      </aside>

      <div className="min-w-0">
        <DocsMobileBar pages={pages} current={current} title={title} toc={toc} active={active} />
        <div className="mx-auto max-w-[42rem] pt-6 pb-16 lg:pt-12">{children}</div>
      </div>

      <aside className="hidden lg:sticky lg:top-0 lg:block lg:max-h-dvh lg:self-start lg:overflow-y-auto lg:[scrollbar-width:none] lg:[&::-webkit-scrollbar]:hidden lg:pt-12 lg:pb-12">
        <DocsToc items={toc} active={active} />
      </aside>
    </div>
  )
}
