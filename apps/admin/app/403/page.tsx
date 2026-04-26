import Link from "next/link"
import { Button } from "@/components/ui/button"

export default function ForbiddenPage() {
  return (
    <main className="grid min-h-screen place-items-center p-8">
      <div className="max-w-md text-center">
        <h1 className="text-3xl font-semibold">Not authorised</h1>
        <p className="text-muted-foreground mt-2">
          Your account is signed in but does not have admin access. If this is unexpected,
          ask the project owner to grant access.
        </p>
        <form action="/auth/signout" method="post" className="mt-6">
          <Button type="submit" variant="outline">
            Sign out
          </Button>
        </form>
        <Link href="/login" className="text-muted-foreground hover:text-foreground mt-3 inline-block text-sm underline">
          Sign in as a different user
        </Link>
      </div>
    </main>
  )
}
