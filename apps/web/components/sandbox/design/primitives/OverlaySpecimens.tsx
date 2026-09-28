"use client"

import { useState } from "react"
import { toast } from "sonner"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet"
import { ConfirmDialog } from "@/components/ui/ConfirmDialog"
import { PickerSheet } from "@/components/ui/PickerSheet"
import { SelectableItem } from "@/components/ui/SelectableItem"
import { SANDBOX_SOULS } from "@/lib/seed/sandbox-pebbles"
import { Specimen } from "../Specimen"

/** Primitives that portal to <body>: each behind a trigger, themed through <html>. */
export function OverlaySpecimens() {
  const [sort, setSort] = useState("newest")
  const [soulId, setSoulId] = useState(SANDBOX_SOULS[0].id)

  return (
    <>
      <Specimen id="dialog">
        <Dialog>
          <DialogTrigger render={<Button variant="outline" />}>Open dialog</DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Rename collection</DialogTitle>
              <DialogDescription>Dialogs sit on the popover surface, over the scrim.</DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <DialogClose>Cancel</DialogClose>
              <DialogClose variant="default">Save</DialogClose>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </Specimen>

      <Specimen id="alert-dialog">
        <AlertDialog>
          <AlertDialogTrigger render={<Button variant="outline" />}>Open alert dialog</AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Discard this pebble?</AlertDialogTitle>
              <AlertDialogDescription>Alert dialogs ask before something can’t be undone.</AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Keep</AlertDialogCancel>
              <AlertDialogAction>Discard</AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </Specimen>

      <Specimen id="confirm-dialog">
        <ConfirmDialog
          trigger={<Button variant="destructive">Delete collection</Button>}
          title="Delete this collection?"
          description="The pebbles stay on your path."
          confirmLabel="Delete"
          onConfirm={() => {}}
        />
        <ConfirmDialog
          trigger={<Button variant="outline">Leave without saving</Button>}
          title="Leave without saving?"
          description="Your changes to this pebble will be lost."
          variant="default"
          confirmLabel="Leave"
          onConfirm={() => {}}
        />
      </Specimen>

      <Specimen id="sheet">
        <Sheet>
          <SheetTrigger render={<Button variant="outline" />}>Open sheet</SheetTrigger>
          <SheetContent>
            <SheetHeader>
              <SheetTitle>Sheet title</SheetTitle>
            </SheetHeader>
            <p className="text-sm text-muted-foreground">A bottom sheet on mobile, a side panel from md up.</p>
          </SheetContent>
        </Sheet>
      </Specimen>

      <Specimen id="picker-sheet">
        <PickerSheet
          title="Pick a soul"
          closeLabel="Close"
          trigger={<SheetTrigger render={<Button variant="outline" />}>Open picker sheet</SheetTrigger>}
          footer={<Button className="w-full">Done</Button>}
        >
          <div role="radiogroup" aria-label="Souls" className="flex flex-col gap-1">
            {SANDBOX_SOULS.map((soul) => (
              <SelectableItem
                key={soul.id}
                role="radio"
                selected={soul.id === soulId}
                onSelect={() => setSoulId(soul.id)}
                showCheck
              >
                {soul.name}
              </SelectableItem>
            ))}
          </div>
        </PickerSheet>
      </Specimen>

      <Specimen id="popover">
        <Popover>
          <PopoverTrigger render={<Button variant="outline" />}>Open popover</PopoverTrigger>
          <PopoverContent className="w-64 text-sm">Popovers use the popover surface.</PopoverContent>
        </Popover>
      </Specimen>

      <Specimen id="dropdown-menu">
        <DropdownMenu>
          <DropdownMenuTrigger render={<Button variant="outline" />}>Open menu</DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            <DropdownMenuItem>Edit</DropdownMenuItem>
            <DropdownMenuItem>Duplicate</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuRadioGroup value={sort} onValueChange={(value) => setSort(String(value))}>
              <DropdownMenuRadioItem value="newest">Newest first</DropdownMenuRadioItem>
              <DropdownMenuRadioItem value="oldest">Oldest first</DropdownMenuRadioItem>
            </DropdownMenuRadioGroup>
          </DropdownMenuContent>
        </DropdownMenu>
      </Specimen>

      <Specimen id="toast">
        <Button variant="outline" onClick={() => toast("Pebble saved")}>
          Default
        </Button>
        <Button variant="outline" onClick={() => toast.success("Pebble saved")}>
          Success
        </Button>
        <Button variant="outline" onClick={() => toast.error("Couldn’t save the pebble")}>
          Error
        </Button>
        <Button variant="outline" onClick={() => toast.info("Drafts sync when you’re back online")}>
          Info
        </Button>
      </Specimen>
    </>
  )
}
