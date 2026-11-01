"use client"

import { useTranslations } from "next-intl"
import { useConnections } from "@/lib/data/useConnections"
import { PageLayout } from "@/components/layout/PageLayout"
import { PageHeader } from "@/components/layout/PageHeader"
import { InviteSection } from "@/components/connections/InviteSection"
import { ConnectionsList } from "@/components/connections/ConnectionsList"

export default function ConnectionsPage() {
  const t = useTranslations("connections")
  // One hook instance feeds both sections — InviteSection would otherwise
  // trigger a second list fetch through its own copy.
  const { connections, loading, error, ready, createInvite, removeConnection } = useConnections()

  return (
    <PageLayout>
      <section>
        <PageHeader title={t("title")} backHref="/profile" />
        <div className="flex flex-col gap-8">
          <InviteSection ready={ready} createInvite={createInvite} />
          <ConnectionsList
            connections={connections}
            loading={loading}
            error={error}
            onRemove={removeConnection}
          />
        </div>
      </section>
    </PageLayout>
  )
}
