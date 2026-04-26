import { Button } from "@/components/ui/button"

export function TopBar({ email }: { email: string }) {
  return (
    <header className="bg-card flex items-center justify-between border-b px-6 py-3">
      <div />
      <form action="/auth/signout" method="post" className="flex items-center gap-3">
        <span className="text-muted-foreground text-sm">{email}</span>
        <Button type="submit" variant="outline" size="sm">
          Sign out
        </Button>
      </form>
    </header>
  )
}
