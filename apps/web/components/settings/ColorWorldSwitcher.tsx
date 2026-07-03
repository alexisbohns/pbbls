"use client"

import { Check, Palette } from "lucide-react"
import { useColorWorld } from "@/components/layout/ColorWorldProvider"
import { COLOR_WORLDS } from "@/lib/config/color-worlds"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"

/**
 * Picks the active color world (Blush Quartz, Stoic Rock, …). Web-only control
 * — iOS has no equivalent. Persists via `ColorWorldProvider` (localStorage +
 * `<html>` class), matching the existing color-world mechanism.
 */
export function ColorWorldSwitcher() {
  const { colorWorld, setColorWorld } = useColorWorld()
  const current = COLOR_WORLDS.find((w) => w.id === colorWorld) ?? COLOR_WORLDS[0]

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button variant="ghost" size="sm">
            <Palette className="size-4" />
            {current.label}
          </Button>
        }
      />
      <DropdownMenuContent align="end">
        {COLOR_WORLDS.map((world) => (
          <DropdownMenuItem key={world.id} onClick={() => setColorWorld(world.id)}>
            <span
              className="inline-block size-3 rounded-full border border-border"
              style={{ backgroundColor: world.background.light }}
              aria-hidden
            />
            {world.label}
            <Check
              className={
                "ml-auto size-4 " + (world.id === colorWorld ? "opacity-100" : "opacity-0")
              }
              aria-hidden
            />
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
