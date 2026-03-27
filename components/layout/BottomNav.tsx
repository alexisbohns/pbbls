"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { Route, CirclePlus, FolderOpen } from "lucide-react"
import { cn } from "@/lib/utils"

const navItems: ReadonlyArray<{
  href: string
  label: string
  icon: typeof Route
  primary?: true
}> = [
  { href: "/path", label: "Path", icon: Route },
  { href: "/record", label: "Record", icon: CirclePlus, primary: true },
  { href: "/collections", label: "Collections", icon: FolderOpen },
]

export function BottomNav() {
  const pathname = usePathname()

  return (
    <nav
      aria-label="Main navigation"
      className="fixed inset-x-0 bottom-0 z-50 border-t border-border bg-background md:hidden"
    >
      <ul className="flex items-center justify-around py-2">
        {navItems.map(({ href, label, icon: Icon, primary }) => {
          const isActive = pathname.startsWith(href)
          return (
            <li key={href}>
              <Link
                href={href}
                className={cn(
                  "flex flex-col items-center gap-0.5 px-3 py-1 text-xs font-medium transition-colors",
                  isActive
                    ? "text-foreground"
                    : "text-muted-foreground",
                  primary && !isActive && "text-primary",
                )}
                aria-current={isActive ? "page" : undefined}
              >
                <Icon className={cn("size-5", primary && "size-6")} />
                {label}
              </Link>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
