import { Suspense, type ReactNode } from "react"
import type { Metadata } from "next"
import { DesignDirectory } from "@/components/sandbox/design/DesignDirectory"

export const metadata: Metadata = { title: "Design" }

// The directory reads its theme from the URL (useSearchParams); Next 16 needs
// a Suspense boundary around that for a prerendered route, or the build fails.
export default function DesignLayout({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={null}>
      <DesignDirectory>{children}</DesignDirectory>
    </Suspense>
  )
}
