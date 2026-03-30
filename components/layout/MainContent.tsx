"use client"

import { usePathname } from "next/navigation"
import { cn } from "@/lib/utils"

interface MainContentProps {
  children: React.ReactNode
}

export function MainContent({ children }: MainContentProps) {
  const pathname = usePathname()
  const hideBottomNav = pathname.startsWith("/record")

  return (
    <main
      className={cn(
        "min-w-0 flex-1 overflow-x-hidden overflow-y-auto px-4 py-8 md:pb-8",
        "transition-[padding-bottom] duration-300 ease-in-out motion-reduce:transition-none",
        hideBottomNav ? "pb-8" : "pb-20",
      )}
    >
      <div className="mx-auto max-w-5xl">{children}</div>
    </main>
  )
}
