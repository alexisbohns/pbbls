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
        "min-w-0 flex-1 touch-pan-y overflow-x-hidden overflow-y-auto px-4 pt-[calc(2rem+var(--safe-area-top))] pb-8 md:pb-8",
        "transition-[padding-bottom] duration-300 ease-in-out motion-reduce:transition-none",
        hideBottomNav ? "pb-[calc(2rem+var(--safe-area-bottom))]" : "pb-[calc(5rem+var(--safe-area-bottom))]",
      )}
    >
      <div className="mx-auto max-w-5xl">{children}</div>
    </main>
  )
}
