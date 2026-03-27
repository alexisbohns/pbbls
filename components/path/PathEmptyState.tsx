import Link from "next/link"
import { CirclePlus } from "lucide-react"
import { Button } from "@/components/ui/button"
import { EmptyState } from "@/components/layout/EmptyState"

export function PathEmptyState() {
  return (
    <EmptyState
      title="No pebbles yet"
      description="Record your first moment to start building your path."
      action={
        <Button render={<Link href="/record" />}>
          <CirclePlus className="size-4" data-icon="inline-start" />
          Record a pebble
        </Button>
      }
    />
  )
}
