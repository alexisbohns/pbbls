"use client"

import { useState, useEffect, type FormEvent } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useAuth } from "@/lib/data/auth-context"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

export default function LoginPage() {
  const { login, profile, isAuthenticated, isLoading } = useAuth()
  const router = useRouter()

  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace(profile?.onboarding_completed ? "/path" : "/onboarding")
    }
  }, [isLoading, isAuthenticated, profile, router])

  if (isLoading || isAuthenticated) return null

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!username.trim() || !password) {
      setError("Please fill in all fields.")
      return
    }

    setSubmitting(true)
    try {
      await login({ username: username.trim(), password })
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Something went wrong."
      setError(message)
      setSubmitting(false)
      return
    }
  }

  return (
    <section className="flex min-h-full flex-col items-center justify-center px-6 text-center">
      <h1 className="text-4xl font-bold tracking-tight">Log in</h1>
      <p className="mt-3 max-w-sm text-muted-foreground">
        Welcome back. Pick up where you left off.
      </p>

      <form
        onSubmit={handleSubmit}
        className="mt-8 flex w-full max-w-xs flex-col gap-4"
        noValidate
      >
        <div className="flex flex-col gap-1.5 text-left">
          <label htmlFor="login-username" className="text-sm font-medium">
            Username
          </label>
          <Input
            id="login-username"
            type="text"
            autoComplete="username"
            autoCapitalize="none"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            aria-invalid={error ? true : undefined}
            disabled={submitting}
          />
        </div>

        <div className="flex flex-col gap-1.5 text-left">
          <label htmlFor="login-password" className="text-sm font-medium">
            Password
          </label>
          <Input
            id="login-password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={error ? true : undefined}
            disabled={submitting}
          />
        </div>

        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}

        <Button type="submit" size="lg" disabled={submitting}>
          {submitting ? "Logging in\u2026" : "Log in"}
        </Button>
      </form>

      <Link
        href="/register"
        className="mt-6 text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground"
      >
        Create account
      </Link>
    </section>
  )
}
