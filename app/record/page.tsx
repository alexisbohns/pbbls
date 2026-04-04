"use client"

import { RecordStepper } from "@/components/record/RecordStepper"

export default function RecordPage() {
  return (
    <section>
      <h1 className="sr-only">Record a pebble</h1>
      <RecordStepper />
    </section>
  )
}
