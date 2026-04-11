"use client"

import { useState, useEffect, type FormEvent } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useAuth } from "@/lib/data/auth-context"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"

export default function RegisterPage() {
  const { register, signInWithApple, isAuthenticated, isLoading } = useAuth()
  const router = useRouter()

  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [termsAccepted, setTermsAccepted] = useState(false)
  const [privacyAccepted, setPrivacyAccepted] = useState(false)
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

    if (!email.trim() || !password || !confirmPassword) {
      setError("Please fill in all fields.")
      return
    }

    if (password !== confirmPassword) {
      setError("Passwords do not match.")
      return
    }

    if (!termsAccepted || !privacyAccepted) {
      setError("You must accept the Terms of Service and Privacy Policy.")
      return
    }

    setSubmitting(true)
    try {
      await register({
        email: email.trim(),
        password,
        terms_accepted: termsAccepted,
        privacy_accepted: privacyAccepted,
      })
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Something went wrong."
      setError(message)
      setSubmitting(false)
    }
  }

  const handleAppleSignIn = async () => {
    setError(null)
    try {
      await signInWithApple()
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Something went wrong."
      setError(message)
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
          <label htmlFor="register-email" className="text-sm font-medium">
            Email
          </label>
          <Input
            id="register-email"
            type="email"
            autoComplete="email"
            autoCapitalize="none"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
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

        <div className="flex items-start gap-2 text-left">
          <Checkbox
            id="register-terms"
            checked={termsAccepted}
            onCheckedChange={(checked) => setTermsAccepted(checked === true)}
            disabled={submitting}
            required
          />
          <label
            htmlFor="register-terms"
            className="text-sm text-muted-foreground"
          >
            I accept the{" "}
            <Link
              href="/docs/terms"
              target="_blank"
              rel="noopener noreferrer"
              className="underline underline-offset-4 hover:text-foreground"
            >
              Terms of Service
            </Link>
          </label>
        </div>

        <div className="flex items-start gap-2 text-left">
          <Checkbox
            id="register-privacy"
            checked={privacyAccepted}
            onCheckedChange={(checked) => setPrivacyAccepted(checked === true)}
            disabled={submitting}
            required
          />
          <label
            htmlFor="register-privacy"
            className="text-sm text-muted-foreground"
          >
            I accept the{" "}
            <Link
              href="/docs/privacy"
              target="_blank"
              rel="noopener noreferrer"
              className="underline underline-offset-4 hover:text-foreground"
            >
              Privacy Policy
            </Link>
          </label>
        </div>

        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}

        <Button
          type="submit"
          size="lg"
          disabled={submitting || !termsAccepted || !privacyAccepted}
        >
          {submitting ? "Creating account\u2026" : "Create account"}
        </Button>
      </form>

      <div className="mt-4 flex w-full max-w-xs flex-col gap-3">
        <div className="relative flex items-center justify-center">
          <span className="absolute inset-x-0 top-1/2 h-px bg-border" />
          <span className="relative bg-background px-2 text-xs text-muted-foreground">
            or
          </span>
        </div>

        <Button
          variant="outline"
          size="lg"
          onClick={handleAppleSignIn}
          disabled={submitting}
          aria-label="Sign up with Apple"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="currentColor"
            className="mr-2"
            aria-hidden="true"
          >
            <path d="M17.05 20.28c-.98.95-2.05.88-3.08.4-1.09-.5-2.08-.48-3.24 0-1.44.62-2.2.44-3.06-.4C2.79 15.25 3.51 7.59 9.05 7.31c1.35.07 2.29.74 3.08.8 1.18-.24 2.31-.93 3.57-.84 1.51.12 2.65.72 3.4 1.8-3.12 1.87-2.38 5.98.48 7.13-.57 1.5-1.31 2.99-2.54 4.09zM12.03 7.25c-.15-2.23 1.66-4.07 3.74-4.25.29 2.58-2.34 4.5-3.74 4.25z" />
          </svg>
          Sign up with Apple
        </Button>
      </div>

      <Link
        href="/login"
        className="mt-6 text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground"
      >
        Log in
      </Link>
    </section>
  )
}
