"use client"

import { PathProfileCard } from "@/components/path/PathProfileCard"

type PathLayoutProps = {
  children: React.ReactNode
}

export function PathLayout({ children }: PathLayoutProps) {
  return (
    <>
      <div className="flex-column items-start md:grid md:grid-cols-[1fr_380px_1fr] gap-6 px-4 md:px-6 pt-8 md:pt-8">
        <aside className="flex justify-end sticky top-8">
          <div className="w-full md:max-w-[200px]">
            <PathProfileCard />
          </div>
        </aside>

        <div className="min-w-0 mt-6 md:mt-0">
          {children}
        </div>
        <div aria-hidden className="hidden md:flex" />
      </div>
    </>
  )
}
