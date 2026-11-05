import type { Metadata } from "next"
import { ViewsWall } from "@/components/sandbox/design/canvas/ViewsWall"

export const metadata: Metadata = { title: "Views · Design" }

export default function DesignViewsPage() {
  return <ViewsWall />
}
