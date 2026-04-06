"use client"

import type { ReactNode } from "react"

type PageLayoutProps = {
  sidebar?: ReactNode
  children: ReactNode
}

export function PageLayout({ sidebar, children }: PageLayoutProps) {
  if (!sidebar) {
    return (
      <div className="mx-auto max-w-md px-4 pt-4 md:px-6 md:pt-8">
        {children}
      </div>
    )
  }

  return (
    <div className="flex-column items-start md:flex gap-6 px-4 md:px-6 pt-4 md:pt-8">
      <aside className="flex justify-end sticky top-4 w-full">
        <div className="w-full md:max-w-[200px]">
          {sidebar}
        </div>
      </aside>

      <div className="min-w-0 mt-6 md:mt-0 w-full md:min-w-[380px] md:max-w-[600px]">
        {children}
      </div>

      <div aria-hidden className="hidden md:flex w-full" />
    </div>
  )
}
