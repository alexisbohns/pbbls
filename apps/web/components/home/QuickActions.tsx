"use client"

import Link from "next/link"
import { useTranslations } from "next-intl"
import { PebbleFlat, PlusCircle } from "@pbbls/icons"
import { SplitStone } from "@/components/stones/SplitStone"
import { CameraAction } from "./CameraAction"
import { PRIMARY_CONTAINER_FILL } from "./home-fills"
import { quickActionCell } from "./quick-action-cell"

/**
 * Home's quick actions (#1384, spec D11): Camera · Quick moment · New pebble,
 * one rock split in three, each piece a control the size of its piece. The
 * stone is decoration; the labels are the names.
 */
export function QuickActions({ onQuickMoment }: { onQuickMoment: () => void }) {
  const t = useTranslations("home.actions")
  return (
    <SplitStone
      n={3}
      height={100}
      fill={PRIMARY_CONTAINER_FILL}
      cells={[
        <CameraAction key="camera" />,
        <button key="quick" type="button" onClick={onQuickMoment} className={quickActionCell(1)}>
          <PebbleFlat className="size-7" />
          <span>{t("quick")}</span>
        </button>,
        <Link key="pebble" href="/record" className={quickActionCell(2)}>
          <PlusCircle className="size-7" />
          <span>{t("pebble")}</span>
        </Link>,
      ]}
    />
  )
}
