import { Route, CirclePlus, FolderOpen, Users } from "lucide-react"

export const NAV_ITEMS: ReadonlyArray<{
  href: string
  label: string
  icon: typeof Route
  primary?: true
}> = [
  { href: "/path", label: "Path", icon: Route },
  { href: "/record", label: "Record", icon: CirclePlus, primary: true },
  { href: "/collections", label: "Collections", icon: FolderOpen },
  { href: "/souls", label: "Souls", icon: Users },
]
