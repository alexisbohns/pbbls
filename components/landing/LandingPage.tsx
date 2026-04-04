"use client"

import { useEffect } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useAuth } from "@/lib/data/auth-context"
import { Button } from "@/components/ui/button"

export function LandingPage() {
  const { isAuthenticated, isLoading } = useAuth()
  const router = useRouter()

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace("/path")
    }
  }, [isLoading, isAuthenticated, router])

  if (isLoading || isAuthenticated) return null

  return (
    <section className="flex min-h-full flex-col items-center justify-center px-6 text-center">
      <h1 className="text-4xl font-bold tracking-tight">pbbls</h1>
      <p className="mt-3 max-w-sm text-muted-foreground">
        Collect meaningful moments, one pebble at a time.
      </p>

      <div className="mt-8 flex flex-col items-center gap-3">
        <Button size="lg" render={<Link href="/login" />}>
          Log in
        </Button>
        <Link
          href="/register"
          className="text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground"
        >
          Create account
        </Link>
      </div>
    </section>
  )
}
