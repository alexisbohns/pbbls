"use client"

import { useState } from "react"
import dynamic from "next/dynamic"
import "@uiw/react-md-editor/markdown-editor.css"

const MDEditor = dynamic(() => import("@uiw/react-md-editor"), { ssr: false })

export function MarkdownField({
  name,
  defaultValue,
  ariaLabel,
}: {
  name: string
  defaultValue: string | null | undefined
  ariaLabel: string
}) {
  const [value, setValue] = useState<string>(defaultValue ?? "")

  return (
    <div data-color-mode="light" aria-label={ariaLabel}>
      {/* Hidden input carries the current editor value into the form's FormData. */}
      <input type="hidden" name={name} value={value} readOnly />
      <MDEditor
        value={value}
        onChange={(v) => setValue(v ?? "")}
        height={240}
        preview="edit"
      />
    </div>
  )
}
