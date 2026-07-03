"use client"

import type { ReactNode } from "react"
import { X } from "lucide-react"
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetClose,
} from "@/components/ui/sheet"

type PickerSheetProps = {
  title: string
  /** Accessible label for the header close (X) button. */
  closeLabel: string
  children: ReactNode
  /**
   * Optional trigger placed inside the Sheet root. Omit for controlled callers
   * whose trigger lives elsewhere (e.g. a pill button in a parent form that
   * drives `open`). When provided, pass a `<SheetTrigger …/>` element.
   */
  trigger?: ReactNode
  open?: boolean
  onOpenChange?: (open: boolean) => void
  contentClassName?: string
}

/**
 * Shared drawer shell for all pickers. Captures the identical Sheet chrome —
 * responsive panel, header with title + `X` close — so every picker (domain,
 * collection, valence, emotion, souls) opens the same drawer; only the body
 * differs. When `open`/`onOpenChange` are omitted the Sheet runs uncontrolled.
 */
export function PickerSheet({
  title,
  closeLabel,
  children,
  trigger,
  open,
  onOpenChange,
  contentClassName,
}: PickerSheetProps) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      {trigger}
      <SheetContent className={contentClassName}>
        <SheetHeader className="relative">
          <SheetTitle>{title}</SheetTitle>
          <SheetClose
            aria-label={closeLabel}
            variant="ghost"
            size="icon-sm"
            className="absolute right-0 top-0"
          >
            <X aria-hidden />
          </SheetClose>
        </SheetHeader>
        {children}
      </SheetContent>
    </Sheet>
  )
}
