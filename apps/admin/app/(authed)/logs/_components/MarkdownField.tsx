"use client"

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
  return (
    <div data-color-mode="light" aria-label={ariaLabel}>
      <input type="hidden" name={name} defaultValue={defaultValue ?? ""} id={`${name}-hidden`} />
      <MDEditor
        value={defaultValue ?? ""}
        onChange={(val) => {
          const hidden = document.getElementById(`${name}-hidden`) as HTMLInputElement | null
          if (hidden) hidden.value = val ?? ""
        }}
        height={240}
        preview="edit"
      />
    </div>
  )
}
