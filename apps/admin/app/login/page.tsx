"use client"

import { useActionState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { signIn, type LoginResult } from "./actions"

export default function LoginPage() {
  const [state, formAction, pending] = useActionState<LoginResult, FormData>(signIn, undefined)

  return (
    <main className="grid min-h-screen place-items-center p-8">
      <form action={formAction} className="w-full max-w-sm space-y-4">
        <header className="space-y-1">
          <h1 className="text-2xl font-semibold">Pebbles · Back-office</h1>
          <p className="text-muted-foreground text-sm">Admin sign-in</p>
        </header>

        <div className="space-y-2">
          <Label htmlFor="email">Email</Label>
          <Input id="email" name="email" type="email" autoComplete="email" required />
        </div>

        <div className="space-y-2">
          <Label htmlFor="password">Password</Label>
          <Input id="password" name="password" type="password" autoComplete="current-password" required />
        </div>

        {state?.error ? (
          <p role="alert" className="text-destructive text-sm">
            {state.error}
          </p>
        ) : null}

        <Button type="submit" className="w-full" disabled={pending}>
          {pending ? "Signing in…" : "Sign in"}
        </Button>
      </form>
    </main>
  )
}
