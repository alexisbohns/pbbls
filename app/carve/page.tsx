"use client"

import Link from "next/link"
import { CarveEditor } from "@/components/carve/CarveEditor"

export default function CarvePage() {
  return (
    <section>
      <nav className="mb-6">
        <Link
          href="/path"
          className="text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          &larr; Back to Path
        </Link>
      </nav>

      <h1 className="mb-6 text-2xl font-semibold">Carve a mark</h1>

      <CarveEditor />
    </section>
  )
}
