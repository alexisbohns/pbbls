"use client"

import type { LucideIcon } from "lucide-react"
import type { ReactNode } from "react"
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogMedia,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"

export function GamificationBlock({
  icon: Icon,
  label,
  value,
  dialogTitle,
  dialogDescription,
}: {
  icon: LucideIcon
  label: string
  value: string | number
  dialogTitle: string
  dialogDescription: ReactNode
}) {
  return (
    <AlertDialog>
      <AlertDialogTrigger
        className="flex flex-1 cursor-pointer items-center gap-2 rounded-lg border border-transparent p-2 text-left transition-colors hover:bg-muted/50 focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 active:scale-[0.98]"
        aria-label={`${label}: ${value}. Tap to learn more.`}
      >
        <Icon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
        <div className="min-w-0">
          <p className="text-sm font-semibold leading-tight text-foreground">
            {value}
          </p>
          <p className="text-xs leading-tight text-muted-foreground">{label}</p>
        </div>
      </AlertDialogTrigger>

      <AlertDialogContent size="sm">
        <AlertDialogHeader>
          <AlertDialogMedia>
            <Icon />
          </AlertDialogMedia>
          <AlertDialogTitle>{dialogTitle}</AlertDialogTitle>
          <AlertDialogDescription>{dialogDescription}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Got it</AlertDialogCancel>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
