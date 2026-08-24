"use client"

import type { ReactNode } from "react"
import { Loader2 } from "lucide-react"
import { Button } from "@/components/ui/button"

/**
 * The single action a step may offer beneath its content.
 *
 * `text` is the quiet Skip / Done on the optional steps; `primary` is the
 * full-width button on the three steps that have nothing to tap.
 */
export type RecordStepAction =
  | { kind: "text"; label: string; onPress: () => void }
  | { kind: "primary"; label: string; enabled: boolean; loading?: boolean; onPress: () => void }

type RecordStepScaffoldProps = {
  title: string
  subtitle?: string
  action?: RecordStepAction
  children: ReactNode
}

/**
 * Shared geometry for every step: a title, a content slot, and one optional
 * button beneath it.
 *
 * Steps supply content and a button role and never their own layout, so the
 * title baseline and the button position do not drift between screens as the
 * user moves through the flow — which is the whole reason the flow reads as one
 * motion rather than eleven pages.
 */
export function RecordStepScaffold({
  title,
  subtitle,
  action,
  children,
}: RecordStepScaffoldProps) {
  return (
    <div className="flex min-h-0 flex-1 flex-col gap-6">
      <header className="flex flex-col gap-1 text-center">
        <h2 className="font-heading text-xl font-semibold text-balance text-foreground">
          {title}
        </h2>
        {subtitle && <p className="text-sm text-balance text-muted-foreground">{subtitle}</p>}
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">{children}</div>

      {action && (
        <div className="flex shrink-0 justify-center">
          {action.kind === "text" ? (
            <Button variant="ghost" onClick={action.onPress} className="text-muted-foreground">
              {action.label}
            </Button>
          ) : (
            <Button
              onClick={action.onPress}
              disabled={!action.enabled || action.loading}
              className="w-full"
            >
              {action.loading && <Loader2 className="animate-spin" aria-hidden />}
              {action.label}
            </Button>
          )}
        </div>
      )}
    </div>
  )
}
