import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar"
import { AppSidebar } from "@/components/layout/Sidebar"
import { TopBar } from "@/components/layout/TopBar"
import { requireAdmin } from "@/lib/supabase/admin-guard"

export default async function AuthedLayout({ children }: { children: React.ReactNode }) {
  const admin = await requireAdmin()

  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <TopBar email={admin.email} />
        <main className="flex-1 p-6">{children}</main>
      </SidebarInset>
    </SidebarProvider>
  )
}
