import { useDataProvider } from "@/lib/data/provider-context"
import type { Store } from "@/lib/data/data-provider"

export function useReset(): { reset: () => Promise<Store> } {
  const { provider, setStore } = useDataProvider()

  const reset = async (): Promise<Store> => {
    const snapshot = await provider.reset()
    setStore(snapshot)
    return snapshot
  }

  return { reset }
}
