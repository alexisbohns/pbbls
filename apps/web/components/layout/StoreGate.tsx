"use client"

import { usePathname } from "next/navigation"
import { useDataProvider } from "@/lib/data/provider-context"
import { isStoreBackedRoute } from "@/lib/config/store-routes"
import { StoreLoadError } from "@/components/layout/StoreLoadError"

/**
 * One shell rather than a branch per screen: the defect is a single global
 * failure expressing itself as whatever each screen shows when its slice of the
 * store is empty. Nine screens each growing their own `if (error)` would be nine
 * places to forget, and a guarantee the tenth ships without it.
 *
 * Fails *open* on an unlisted route — a public profile renders fine without the
 * store, so a false error page there would be a regression.
 */
export function StoreGate({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const { error, loading, refreshStore } = useDataProvider()

  if (error !== null && isStoreBackedRoute(pathname)) {
    return <StoreLoadError onRetry={refreshStore} retrying={loading} />
  }

  return <>{children}</>
}
