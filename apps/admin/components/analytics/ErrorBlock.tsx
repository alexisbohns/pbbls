import Link from "next/link"
import { AlertTriangle } from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"

type ErrorBlockProps = {
  label: string
  /** Verbose admin-facing message. */
  message: string
  /** Same-URL link target for the retry button. Defaults to current. */
  retryHref?: string
}

export function ErrorBlock({ label, message, retryHref }: ErrorBlockProps) {
  return (
    <Card className="border-destructive/40">
      <CardContent className="flex items-start gap-3 py-4">
        <AlertTriangle className="mt-0.5 size-5 text-destructive" aria-hidden />
        <div className="space-y-1">
          <p className="text-sm font-medium">{label}</p>
          <pre className="whitespace-pre-wrap break-words text-xs text-muted-foreground">
            {message}
          </pre>
          {retryHref ? (
            <Link href={retryHref} className="text-xs underline">
              Retry
            </Link>
          ) : null}
        </div>
      </CardContent>
    </Card>
  )
}
