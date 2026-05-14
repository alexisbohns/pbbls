"use client"

import { QuickPebbleEditor } from "@/components/path/QuickPebbleEditor"
import { PathBottomBar } from "@/components/path/PathBottomBar"

type PathBottomDockProps = {
  editorExpanded: boolean
  onEditorExpandedChange: (next: boolean) => void
  onPebbleCreated: (id: string) => void
}

export function PathBottomDock({
  editorExpanded,
  onEditorExpandedChange,
  onPebbleCreated,
}: PathBottomDockProps) {
  return (
    <div className="sticky inset-x-0 bottom-0 bg-gradient-to-t from-background to-transparent pt-4">
      <div className="px-4">
        <QuickPebbleEditor
          expanded={editorExpanded}
          onExpandedChange={onEditorExpandedChange}
          onPebbleCreated={onPebbleCreated}
        />
      </div>
      <PathBottomBar />
    </div>
  )
}
