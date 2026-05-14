"use client"

import { useState } from "react"
import { useTranslations } from "next-intl"
import { QuickPebbleEditor } from "@/components/path/QuickPebbleEditor"
import { PebblePeek } from "@/components/path/PebblePeek"

export default function RecordPage() {
  const [selectedPebbleId, setSelectedPebbleId] = useState<string | null>(null)
  // /record is a dedicated capture route — auto-open the overlay on mount so
  // the user lands on the editor itself, not its collapsed trigger.
  const [editorExpanded, setEditorExpanded] = useState(true)
  const t = useTranslations("record")

  return (
    <section className="mx-auto max-w-lg px-4 py-8">
      <h1 className="sr-only">{t("srTitle")}</h1>
      <QuickPebbleEditor
        expanded={editorExpanded}
        onExpandedChange={setEditorExpanded}
        onPebbleCreated={(pebble) => setSelectedPebbleId(pebble.id)}
      />
      <PebblePeek
        pebbleId={selectedPebbleId}
        onClose={() => setSelectedPebbleId(null)}
      />
    </section>
  )
}
