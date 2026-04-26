import Link from "next/link"
import { ScrollText } from "lucide-react"
import { cn } from "@/lib/utils"

const ITEMS = [{ href: "/logs", label: "Logs", icon: ScrollText }] as const

export function Sidebar({ activeHref }: { activeHref: string }) {
  return (
    <nav aria-label="Primary" className="bg-card flex h-full w-56 flex-col border-r">
      <div className="border-b px-4 py-4">
        <span className="text-sm font-semibold">Back-office</span>
      </div>
      <ul className="flex-1 space-y-1 p-2">
        {ITEMS.map(({ href, label, icon: Icon }) => {
          const active = activeHref === href || activeHref.startsWith(`${href}/`)
          return (
            <li key={href}>
              <Link
                href={href}
                className={cn(
                  "hover:bg-accent flex items-center gap-2 rounded-md px-3 py-2 text-sm",
                  active && "bg-accent font-medium",
                )}
              >
                <Icon className="size-4" aria-hidden />
                {label}
              </Link>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
