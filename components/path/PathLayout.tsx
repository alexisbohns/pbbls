"use client"

import { PathProfileCard } from "@/components/path/PathProfileCard"

type PathLayoutProps = {
  children: React.ReactNode
}

export function PathLayout({ children }: PathLayoutProps) {
  return (
    <>
      {/* Mobile: profile card as sticky header */}
      <div className="sticky top-0 z-30 bg-background/95 backdrop-blur-sm border-b border-border/50 md:hidden">
        <PathProfileCard />
      </div>

      {/* Desktop: 3-column grid [fill, 380px, fill] */}
      <div className="hidden md:grid md:grid-cols-[1fr_380px_1fr] md:gap-6 md:px-6 md:pt-8">
        <aside className="flex justify-end">
          <div className="sticky top-8 h-fit w-full max-w-[200px]">
            <PathProfileCard />
          </div>
        </aside>

        <div className="min-w-0">
          {children}
        </div>

        {/* Right column: empty for balance */}
        <div aria-hidden />
      </div>

      {/* Mobile: single column content */}
      <div className="px-4 pt-4 md:hidden">
        {children}
      </div>
    </>
  )
}
