import { M3Island } from "@/components/layout/M3Island"

// Settings wears the brand M3 roles with the tabs it is reached from (#1384).
export default function SettingsLayout({ children }: { children: React.ReactNode }) {
  return <M3Island>{children}</M3Island>
}
