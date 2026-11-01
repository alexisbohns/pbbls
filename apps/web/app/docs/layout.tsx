// The docs site wears the brand M3 roles and Inclusive Sans, like Home and the
// guides (#1384, spec D3). MainContent toggles `m3` on <body> for /docs too.
import { M3Island } from "@/components/layout/M3Island"

export default function DocsLayout({ children }: { children: React.ReactNode }) {
  return <M3Island>{children}</M3Island>
}
