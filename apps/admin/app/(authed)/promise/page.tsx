import Link from "next/link"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { Button } from "@/components/ui/button"
import type { PromiseLetterRow } from "@/lib/promise-letters/types"
import { PromiseLetterTable } from "./_components/PromiseLetterTable"

export default async function PromisePage() {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.from("promise_letters").select("*").order("updated_at", { ascending: false })
  if (error) console.error("[promise] select failed:", error.message)
  const letters: PromiseLetterRow[] = data ?? []
  return (
    <section className="space-y-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Promise</h1>
          <p className="text-muted-foreground text-sm">
            The letter every new account reads before consenting. The apps show the most recently updated published one.
          </p>
        </div>
        <Button render={<Link href="/promise/new" />}>New letter</Button>
      </header>
      {error ? (
        <p role="alert" className="text-destructive text-sm">Couldn&rsquo;t load the letters: {error.message}</p>
      ) : (
        <PromiseLetterTable letters={letters} />
      )}
    </section>
  )
}
