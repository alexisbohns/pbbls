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
import { cn } from "@/lib/utils"

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
  /** Optional footer (e.g. a "Done" button), pinned below the scrollable body. */
  footer?: ReactNode
}

/**
 * Shared drawer shell for all pickers. Captures the identical Sheet chrome —
 * responsive panel, header with title + `X` close — so every picker (domain,
 * collection, valence, emotion, souls) opens the same drawer at the same
 * size; only the body differs. The body sits in a flex-1 scroll region so
 * short content (e.g. a handful of souls) fills the available height instead
 * of leaving blank space below it, and `footer` — when provided — stays
 * pinned below that region instead of scrolling away with the content. When
 * `open`/`onOpenChange` are omitted the Sheet runs uncontrolled.
 */
export function PickerSheet({
  title,
  closeLabel,
  children,
  trigger,
  open,
  onOpenChange,
  contentClassName,
  footer,
}: PickerSheetProps) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      {trigger}
      <SheetContent className={cn("flex h-[92dvh] flex-col md:h-full", contentClassName)}>
        <SheetHeader className="relative shrink-0">
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
        <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>
        {footer && <div className="mt-4 shrink-0">{footer}</div>}
      </SheetContent>
    </Sheet>
  )
}
