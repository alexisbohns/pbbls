import { Suspense } from "react"
import type { Metadata } from "next"
import { DesignFrame } from "@/components/sandbox/design/frame/DesignFrame"

export const metadata: Metadata = { title: "Design frame" }

// The frame reads its target and theme from the URL (useSearchParams); Next 16
// needs a Suspense boundary around that for a prerendered route.
export default function DesignFramePage() {
  return (
    <Suspense fallback={null}>
      <DesignFrame />
    </Suspense>
  )
}
