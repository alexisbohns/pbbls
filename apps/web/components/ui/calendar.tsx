"use client"

import {
  DayPicker,
  type DayPickerProps,
  UI,
  DayFlag,
  SelectionState,
} from "react-day-picker"
import { ChevronLeft, ChevronRight } from "lucide-react"

import { cn } from "@/lib/utils"

const classNames = {
  [UI.Root]: "p-3",
  [UI.Months]: "flex flex-col",
  [UI.Month]: "space-y-4",
  [UI.MonthCaption]: "flex items-center justify-center pt-1 relative",
  [UI.CaptionLabel]: "text-sm font-medium",
  [UI.Nav]: "flex items-center justify-between absolute inset-x-0",
  [UI.PreviousMonthButton]:
    "inline-flex size-7 items-center justify-center rounded-md border border-input bg-transparent hover:bg-muted hover:text-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 outline-none disabled:pointer-events-none disabled:opacity-50",
  [UI.NextMonthButton]:
    "inline-flex size-7 items-center justify-center rounded-md border border-input bg-transparent hover:bg-muted hover:text-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 outline-none disabled:pointer-events-none disabled:opacity-50",
  [UI.MonthGrid]: "w-full border-collapse space-y-1",
  [UI.Weekdays]: "flex",
  [UI.Weekday]:
    "w-9 text-[0.8rem] font-normal text-muted-foreground rounded-md",
  [UI.Week]: "flex w-full mt-2",
  [UI.Day]:
    "relative size-9 p-0 text-center text-sm focus-within:z-20",
  [UI.DayButton]:
    "inline-flex size-9 items-center justify-center rounded-md text-sm font-normal transition-colors hover:bg-muted hover:text-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 outline-none aria-selected:opacity-100",
  [DayFlag.today]: "bg-muted text-foreground font-medium",
  [DayFlag.outside]: "text-muted-foreground/50",
  [DayFlag.disabled]: "text-muted-foreground/50 opacity-50",
  [DayFlag.hidden]: "invisible",
  [SelectionState.selected]:
    "bg-primary text-primary-foreground hover:bg-primary hover:text-primary-foreground focus:bg-primary focus:text-primary-foreground rounded-md",
}

function Calendar({ className, showOutsideDays = true, ...props }: DayPickerProps) {
  return (
    <DayPicker
      showOutsideDays={showOutsideDays}
      className={cn(classNames[UI.Root], className)}
      classNames={classNames}
      components={{
        Chevron: ({ orientation }) =>
          orientation === "left" ? (
            <ChevronLeft className="size-4" />
          ) : (
            <ChevronRight className="size-4" />
          ),
      }}
      {...props}
    />
  )
}

export { Calendar }
