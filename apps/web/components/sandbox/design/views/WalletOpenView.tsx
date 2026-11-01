"use client"

import { WalletView } from "@/components/wallet/WalletView"
import { PathView } from "./PathView"

/** The Path with the wallet open in the detail area (#1384, two-area spec D2). The wallet reads the network, so it shows its empty state here. */
export function WalletOpenView() {
  return <PathView at="/wallet" detail={<WalletView />} />
}
