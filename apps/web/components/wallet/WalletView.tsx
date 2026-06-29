"use client"

import { useTranslations } from "next-intl"
import { Sparkle } from "lucide-react"
import { useWallet } from "@/lib/data/useWallet"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { PageLayout } from "@/components/layout/PageLayout"
import { PageHeader } from "@/components/layout/PageHeader"
import { WalletHistoryItem } from "./WalletHistoryItem"

export function WalletView() {
  const t = useTranslations("wallet")
  const { balance, totalEarned, totalSpent, history, hasMore, loadMore, loading } = useWallet()

  return (
    <PageLayout>
      <div className="flex flex-col gap-6">
        <PageHeader title={t("title")} backHref="/path" />
        <Card className="flex flex-col gap-2 p-6">
        <div className="flex items-center gap-2">
          <Sparkle className="size-5" aria-hidden />
          <span className="text-3xl font-bold tabular-nums">{loading ? "—" : balance}</span>
          <span className="text-sm text-muted-foreground">{t("balance")}</span>
        </div>
        {balance < 0 && !loading && (
          <p className="text-sm text-amber-600 dark:text-amber-400">{t("debtHint")}</p>
        )}
        <div className="mt-2 flex gap-6 text-sm text-muted-foreground">
          <span>{t("earned")}: <span className="tabular-nums">{totalEarned}</span></span>
          <span>{t("spent")}: <span className="tabular-nums">{totalSpent}</span></span>
        </div>
      </Card>

      <section>
        <h2 className="mb-2 text-sm font-semibold text-muted-foreground">{t("history")}</h2>
        {history.length === 0 && !loading ? (
          <p className="text-sm text-muted-foreground">{t("empty")}</p>
        ) : (
          <ul className="divide-y">
            {history.map((e) => <WalletHistoryItem key={e.id} event={e} />)}
          </ul>
        )}
        {hasMore && (
          <Button variant="ghost" className="mt-3 w-full" onClick={() => loadMore()}>
            {t("loadMore")}
          </Button>
        )}
        </section>
      </div>
    </PageLayout>
  )
}
