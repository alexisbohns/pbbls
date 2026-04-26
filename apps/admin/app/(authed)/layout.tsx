import { headers } from "next/headers"
import { Sidebar } from "@/components/layout/Sidebar"
import { TopBar } from "@/components/layout/TopBar"
import { requireAdmin } from "@/lib/supabase/admin-guard"

export default async function AuthedLayout({ children }: { children: React.ReactNode }) {
  const admin = await requireAdmin()
  const pathname = (await headers()).get("x-pathname") ?? "/logs"

  return (
    <div className="flex min-h-screen">
      <Sidebar activeHref={pathname} />
      <div className="flex flex-1 flex-col">
        <TopBar email={admin.email} />
        <main className="flex-1 p-6">{children}</main>
      </div>
    </div>
  )
}
