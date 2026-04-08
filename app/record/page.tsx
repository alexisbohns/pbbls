"use client"

import { useState } from "react"
import { QuickPebbleEditor } from "@/components/path/QuickPebbleEditor"
import { PebblePeek } from "@/components/path/PebblePeek"

export default function RecordPage() {
  const [selectedPebbleId, setSelectedPebbleId] = useState<string | null>(null)

  return (
    <section className="mx-auto max-w-lg px-4 py-8">
      <h1 className="sr-only">Record a pebble</h1>
      <QuickPebbleEditor onPebbleCreated={setSelectedPebbleId} />
      <PebblePeek
        pebbleId={selectedPebbleId}
        onClose={() => setSelectedPebbleId(null)}
      />
    </section>
  )
}
