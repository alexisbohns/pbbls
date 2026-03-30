import type { ColorWorld } from "@/lib/types"

export interface ColorWorldConfig {
  id: ColorWorld
  label: string
  background: { light: string; dark: string }
}

export const COLOR_WORLDS: ColorWorldConfig[] = [
  { id: "blush-quartz", label: "Blush Quartz", background: { light: "#F8F0F0", dark: "#2B1F21" } },
  { id: "stoic-rock", label: "Stoic Rock", background: { light: "#FFFFFF", dark: "#252525" } },
  { id: "cave-pigment", label: "Cave Pigment", background: { light: "#F5F0E8", dark: "#2B2518" } },
  { id: "dusk-stone", label: "Dusk Stone", background: { light: "#F0EEF0", dark: "#211F2B" } },
  { id: "moss-pool", label: "Moss Pool", background: { light: "#EFF5F2", dark: "#192B22" } },
]

/** Returns { light, dark } background hex values for a given color world ID. */
export function getColorWorldBackground(id: string): { light: string; dark: string } {
  const config = COLOR_WORLDS.find((w) => w.id === id)
  return config?.background ?? COLOR_WORLDS[0].background
}
