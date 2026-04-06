"use client"

import { PathProfileCard } from "@/components/path/PathProfileCard"

type PathLayoutProps = {
  children: React.ReactNode
}

export function PathLayout({ children }: PathLayoutProps) {
  return (
    <>
      <div className="flex-column items-start md:flex gap-6 px-4 md:px-6 pt-8 md:pt-8">
        <aside className="flex justify-end sticky top-8 w-full">
          <div className="w-full md:max-w-[200px]">
            <PathProfileCard />
          </div>
        </aside>

        <div className="min-w-0 mt-6 md:mt-0 w-full md:min-w-[380px] md:max-w-[600px]">
          {children}
        </div>
        <div aria-hidden className="hidden md:flex w-full" />
      </div>
    </>
  )
}
