import type { Metadata } from "next"
import { ComponentsCanvas } from "@/components/sandbox/design/canvas/ComponentsCanvas"

export const metadata: Metadata = { title: "Components · Design" }

export default function DesignComponentsPage() {
  return <ComponentsCanvas />
}
