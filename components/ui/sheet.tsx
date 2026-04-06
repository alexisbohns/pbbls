"use client"

import * as React from "react"
import { Dialog as DialogPrimitive } from "@base-ui/react/dialog"

import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"

function Sheet({ ...props }: DialogPrimitive.Root.Props) {
  return <DialogPrimitive.Root data-slot="sheet" {...props} />
}

function SheetTrigger({ ...props }: DialogPrimitive.Trigger.Props) {
  return <DialogPrimitive.Trigger data-slot="sheet-trigger" {...props} />
}

function SheetPortal({ ...props }: DialogPrimitive.Portal.Props) {
  return <DialogPrimitive.Portal data-slot="sheet-portal" {...props} />
}

function SheetOverlay({
  className,
  ...props
}: DialogPrimitive.Backdrop.Props) {
  return (
    <DialogPrimitive.Backdrop
      data-slot="sheet-overlay"
      className={cn(
        "fixed inset-0 z-50 bg-black/10 supports-backdrop-filter:backdrop-blur-xs transition-opacity duration-200 data-open:opacity-100 data-closed:opacity-0",
        className
      )}
      {...props}
    />
  )
}

/**
 * Sheet content — responsive:
 * - Desktop (md+): slides in from the right as a side panel
 * - Mobile: slides up from the bottom as a bottom sheet
 */
function SheetContent({
  className,
  children,
  ...props
}: DialogPrimitive.Popup.Props) {
  return (
    <SheetPortal>
      <SheetOverlay />
      <DialogPrimitive.Popup
        data-slot="sheet-content"
        className={cn(
          // Shared
          "fixed z-50 bg-popover text-popover-foreground ring-1 ring-foreground/10 outline-none",
          // Mobile: bottom sheet
          "inset-x-0 bottom-0 max-h-[85dvh] overflow-y-auto rounded-t-2xl p-4 pt-2",
          "translate-y-full data-open:translate-y-0 transition-transform duration-200 ease-out",
          // Desktop: side sheet from right — full height, scrollable
          "md:top-0 md:bottom-0 md:right-0 md:left-auto md:max-h-none md:h-full md:w-[420px] md:overflow-y-auto md:rounded-t-none md:rounded-l-2xl md:p-6",
          "md:translate-y-0 md:translate-x-full md:data-open:translate-x-0",
          className
        )}
        {...props}
      >
        {/* Mobile drag handle */}
        <div className="mx-auto mb-3 h-1 w-10 rounded-full bg-muted-foreground/30 md:hidden" aria-hidden />
        {children}
      </DialogPrimitive.Popup>
    </SheetPortal>
  )
}

function SheetHeader({
  className,
  ...props
}: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="sheet-header"
      className={cn("grid place-items-start gap-1.5 mb-4", className)}
      {...props}
    />
  )
}

function SheetTitle({
  className,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Title>) {
  return (
    <DialogPrimitive.Title
      data-slot="sheet-title"
      className={cn("font-heading text-lg font-semibold", className)}
      {...props}
    />
  )
}

function SheetClose({
  className,
  variant = "ghost",
  size = "sm",
  ...props
}: DialogPrimitive.Close.Props &
  Pick<React.ComponentProps<typeof Button>, "variant" | "size">) {
  return (
    <DialogPrimitive.Close
      data-slot="sheet-close"
      className={cn(className)}
      render={<Button variant={variant} size={size} />}
      {...props}
    />
  )
}

export {
  Sheet,
  SheetTrigger,
  SheetPortal,
  SheetOverlay,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetClose,
}
