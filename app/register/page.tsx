"use client"

import { useState, useEffect, type FormEvent } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useAuth } from "@/lib/data/auth-context"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

export default function RegisterPage() {
  const { register, isAuthenticated, isLoading } = useAuth()
  const router = useRouter()

  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace("/onboarding")
    }
  }, [isLoading, isAuthenticated, router])

  if (isLoading || isAuthenticated) return null

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!username.trim() || !password || !confirmPassword) {
      setError("Please fill in all fields.")
      return
    }

    if (password !== confirmPassword) {
      setError("Passwords do not match.")
      return
    }

    setSubmitting(true)
    try {
      await register({ username: username.trim(), password })
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Something went wrong."
      setError(message)
      setSubmitting(false)
    }
  }

  return (
    <section className="flex min-h-full flex-col items-center justify-center px-6 text-center">
      <h1 className="text-4xl font-bold tracking-tight">Create account</h1>
      <p className="mt-3 max-w-sm text-muted-foreground">
        Start collecting meaningful moments.
      </p>

      <form
        onSubmit={handleSubmit}
        className="mt-8 flex w-full max-w-xs flex-col gap-4"
        noValidate
      >
        <div className="flex flex-col gap-1.5 text-left">
          <label htmlFor="register-username" className="text-sm font-medium">
            Username
          </label>
          <Input
            id="register-username"
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
          <label htmlFor="register-password" className="text-sm font-medium">
            Password
          </label>
          <Input
            id="register-password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={error ? true : undefined}
            disabled={submitting}
          />
        </div>

        <div className="flex flex-col gap-1.5 text-left">
          <label htmlFor="register-confirm" className="text-sm font-medium">
            Confirm password
          </label>
          <Input
            id="register-confirm"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
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
          {submitting ? "Creating account\u2026" : "Create account"}
        </Button>
      </form>

      <Link
        href="/login"
        className="mt-6 text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground"
      >
        Log in
      </Link>
    </section>
  )
}
