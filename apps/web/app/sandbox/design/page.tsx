import { Suspense } from "react"
import type { Metadata } from "next"
import { DesignScreen } from "@/components/sandbox/design/DesignScreen"

export const metadata: Metadata = { title: "Design" }

// DesignScreen reads its theme from the URL (useSearchParams); Next 16 needs a
// Suspense boundary around that for a prerendered route, or the build fails.
export default function DesignPage() {
  return (
    <Suspense fallback={null}>
      <DesignScreen />
    </Suspense>
  )
}
