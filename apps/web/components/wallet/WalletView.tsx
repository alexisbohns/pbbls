"use client"

import { useTranslations } from "next-intl"
import { KarmaDot } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { useWallet } from "@/lib/data/useWallet"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { DetailTopBar } from "@/components/layout/DetailTopBar"
import { cn } from "@/lib/utils"
import { WalletHistoryItem } from "./WalletHistoryItem"

/**
 * The karma wallet, a detail beside the Path (#1384, two-area spec D2, D6):
 * the balance as a figure on a card, then the history as a group of M3 list
 * items, on the M3 roles.
 */
export function WalletView() {
  const t = useTranslations("wallet")
  const { balance, totalEarned, totalSpent, history, hasMore, loadMore, loading, error } = useWallet()

  return (
    <>
      <DetailTopBar title={t("title")} />
      <div className="mx-auto flex w-full max-w-2xl flex-col gap-8 px-4 pt-2 pb-12">
        <Card className="pb-card gap-2 p-6">
          <div className="flex items-center gap-2 text-m3-on-surface">
            <KarmaDot className={cn(PBBLS_ICON_SIZE.tile, "text-m3-primary")} />
            <span className="pb-type-counter">{loading ? "—" : balance}</span>
            <span className="pb-type-row-line text-m3-on-surface-variant">{t("balance")}</span>
          </div>
          {balance < 0 && !loading && <p className="pb-type-row-line text-m3-error">{t("debtHint")}</p>}
          <div className="pb-type-row-line mt-2 flex gap-6 text-m3-on-surface-variant">
            <span>
              {t("earned")}: <span className="tabular-nums">{totalEarned}</span>
            </span>
            <span>
              {t("spent")}: <span className="tabular-nums">{totalSpent}</span>
            </span>
          </div>
        </Card>

        <section aria-labelledby="wallet-history">
          <h3 id="wallet-history" className="m3-type-title-small mb-2 px-1 text-m3-primary">
            {t("history")}
          </h3>
          {history.length === 0 && !loading ? (
            <p className="pb-type-row-line px-1 text-m3-on-surface-variant">{error ? t("historyError") : t("empty")}</p>
          ) : (
            <ul className="flex flex-col gap-0.5 overflow-hidden rounded-2xl [&>li]:bg-m3-surface-container [&>li]:px-4">
              {history.map((e) => (
                <WalletHistoryItem key={e.id} event={e} />
              ))}
            </ul>
          )}
          {hasMore && (
            <Button variant="ghost" className="mt-3 w-full" onClick={() => loadMore()}>
              {t("loadMore")}
            </Button>
          )}
        </section>
      </div>
    </>
  )
}
