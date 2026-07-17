"use client"

import { useRouter } from "next/navigation"
import { Plus } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { LAB_NOTE_PREFILL_KEY, parseLabNoteYaml } from "@/lib/logs/parse-lab-note"
import type { LogSpecies } from "@/lib/logs/types"

/**
 * Drop-in replacement for the "New log" link that first peeks at the clipboard.
 * The click is a real user gesture, so `navigator.clipboard.readText()` is
 * allowed; if the clipboard holds a Lab Note YAML snippet we stash the parsed
 * values for the New-log form to prefill. Anything else (empty, non-YAML, or a
 * denied permission) just opens a blank form.
 */
export function NewLogButton({ species }: { species: LogSpecies }) {
  const router = useRouter()
  const href = `/logs/new?species=${species}`

  async function handleClick(event: React.MouseEvent<HTMLAnchorElement>) {
    event.preventDefault()
    try {
      const text = await navigator.clipboard.readText()
      const prefill = parseLabNoteYaml(text)
      if (prefill) {
        window.sessionStorage.setItem(LAB_NOTE_PREFILL_KEY, JSON.stringify(prefill))
      } else {
        // Clear any stale snippet so a non-matching clipboard opens a blank form.
        window.sessionStorage.removeItem(LAB_NOTE_PREFILL_KEY)
      }
    } catch {
      // Clipboard unavailable or permission denied — fall back to a blank form.
      window.sessionStorage.removeItem(LAB_NOTE_PREFILL_KEY)
    }
    router.push(href)
  }

  return (
    <a href={href} onClick={handleClick} className={buttonVariants()}>
      <Plus className="size-4" aria-hidden />
      New log
    </a>
  )
}
