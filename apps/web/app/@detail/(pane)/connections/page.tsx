"use client"

import { useTranslations } from "next-intl"
import { useConnections } from "@/lib/data/useConnections"
import { DetailTopBar } from "@/components/layout/DetailTopBar"
import { InviteSection } from "@/components/connections/InviteSection"
import { ConnectionsList } from "@/components/connections/ConnectionsList"

/** Connections, in the detail area beside People (#1384, two-area spec D2, D6). */
export default function ConnectionsPage() {
  const t = useTranslations("connections")
  // One hook instance feeds both sections — InviteSection would otherwise
  // trigger a second list fetch through its own copy.
  const { connections, loading, error, ready, createInvite, removeConnection } = useConnections()

  return (
    <>
      <DetailTopBar title={t("title")} />
      <div className="mx-auto flex w-full max-w-2xl flex-col gap-8 px-4 pt-2 pb-12">
        <InviteSection ready={ready} createInvite={createInvite} />
        <ConnectionsList connections={connections} loading={loading} error={error} onRemove={removeConnection} />
      </div>
    </>
  )
}
