"use client"

import { useTranslations } from "next-intl"
import { KarmaDot } from "@pbbls/icons"
import { useWallet } from "@/lib/data/useWallet"
import { Button } from "@/components/ui/button"
import { DetailTopBar } from "@/components/layout/DetailTopBar"
import { ListGroup } from "@/components/m3/ListGroup"
import { ListItem } from "@/components/m3/ListItem"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
import { WalletHistoryItem } from "./WalletHistoryItem"

/** A figure in Ysabeau's lining numerals: Inclusive's slashed zero never shows a count. */
const FIGURE = "pb-type-figure text-m3-on-surface"

/**
 * The karma wallet, a detail beside the Path (#1384, two-area spec D2, D6):
 * the balance as a display figure, the totals and the history as M3 list
 * groups, every number set in Ysabeau.
 */
export function WalletView() {
  const t = useTranslations("wallet")
  const { balance, totalEarned, totalSpent, history, hasMore, loadMore, loading, error } = useWallet()

  return (
    <>
      <DetailTopBar title={t("title")} />
      <div className="mx-auto flex w-full max-w-2xl flex-col gap-8 px-4 pt-2 pb-12">
        <section aria-label={t("balance")} className="flex flex-col items-start gap-1 px-1">
          <span className="m3-type-label-large text-m3-on-surface-variant">{t("balance")}</span>
          <span className="flex items-center gap-3 text-m3-on-surface">
            <KarmaDot className={cn(PBBLS_ICON_SIZE.tile, "text-m3-primary")} />
            <span className="pb-type-counter text-[3.5rem]">{loading ? "—" : balance}</span>
          </span>
          {balance < 0 && !loading && <p className="m3-type-body-medium text-m3-error">{t("debtHint")}</p>}
        </section>

        <ul className="m3-list-group">
          <ListItem headline={t("earned")} trailing={<span className={FIGURE}>{loading ? "—" : totalEarned}</span>} />
          <ListItem headline={t("spent")} trailing={<span className={FIGURE}>{loading ? "—" : totalSpent}</span>} />
        </ul>

        {history.length === 0 && !loading ? (
          <section aria-labelledby="wallet-history">
            <h3 id="wallet-history" className="m3-type-label-large px-4 pb-2 text-m3-primary">
              {t("history")}
            </h3>
            <p className="m3-type-body-medium px-4 text-m3-on-surface-variant">{error ? t("historyError") : t("empty")}</p>
          </section>
        ) : (
          <ListGroup title={t("history")}>
            {history.map((e) => (
              <WalletHistoryItem key={e.id} event={e} />
            ))}
          </ListGroup>
        )}
        {hasMore && (
          <Button variant="ghost" className="m3-type-label-large h-10 self-center rounded-full px-4 text-m3-primary" onClick={() => loadMore()}>
            {t("loadMore")}
          </Button>
        )}
      </div>
    </>
  )
}
