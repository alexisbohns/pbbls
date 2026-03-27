import Link from "next/link"
import { CirclePlus } from "lucide-react"
import { Button } from "@/components/ui/button"

export function PathEmptyState() {
  return (
    <section className="flex flex-col items-center justify-center gap-4 py-20 text-center">
      <h2 className="text-lg font-medium">No pebbles yet</h2>
      <p className="text-sm text-muted-foreground">
        Record your first moment to start building your path.
      </p>
      <Button render={<Link href="/record" />}>
        <CirclePlus className="size-4" data-icon="inline-start" />
        Record a pebble
      </Button>
    </section>
  )
}
