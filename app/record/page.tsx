"use client"

import Link from "next/link"
import { RecordStepper } from "@/components/record/RecordStepper"

export default function RecordPage() {
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

      <h1 className="mb-6 text-2xl font-semibold">Record a pebble</h1>

      <RecordStepper />
    </section>
  )
}
