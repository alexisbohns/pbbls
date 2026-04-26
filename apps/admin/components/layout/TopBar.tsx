"use client"

import { useRef } from "react"
import { ChevronDown } from "lucide-react"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { SidebarTrigger } from "@/components/ui/sidebar"
import { Separator } from "@/components/ui/separator"

export function TopBar({ email }: { email: string }) {
  const formRef = useRef<HTMLFormElement>(null)

  return (
    <header className="bg-card flex items-center gap-2 border-b px-4 py-2">
      <SidebarTrigger />
      <Separator orientation="vertical" className="h-5" />
      <div className="flex-1" />

      {/* Hidden form so sign-out keeps its POST → 303 → redirect flow */}
      <form ref={formRef} action="/auth/signout" method="post" className="hidden">
        <button type="submit" />
      </form>

      <DropdownMenu>
        {/* Base UI Menu.Trigger renders its own element; style via className */}
        <DropdownMenuTrigger className="hover:bg-muted inline-flex cursor-default items-center gap-1.5 rounded-md px-2.5 py-1 text-sm outline-none transition-colors">
          <span>{email}</span>
          <ChevronDown className="size-4" aria-hidden />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onSelect={() => formRef.current?.requestSubmit()}>
            Sign out
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </header>
  )
}
