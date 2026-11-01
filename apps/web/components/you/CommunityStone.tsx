import { ChevronRight } from "lucide-react"
import { useTranslations } from "next-intl"
import { Whatsapp } from "@pbbls/icons"
import { Stone } from "@/components/stones/Stone"
import { TERTIARY_CONTAINER_FILL } from "@/components/home/home-fills"
import { LAB_CONFIG } from "@/lib/config/lab"

/**
 * The community callout on You (#1384 D19): a tertiary stone, an external
 * link to the WhatsApp community that says it opens a new tab.
 */
export function CommunityStone() {
  const t = useTranslations("you.lab")
  return (
    <a
      href={LAB_CONFIG.whatsappInviteURL}
      target="_blank"
      rel="noreferrer noopener"
      className="block rounded-[30px] outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
    >
      <Stone
        seed="whatsapp"
        corner={30}
        fill={TERTIARY_CONTAINER_FILL}
        className="flex items-center gap-3.5 py-[18px] pr-[18px] pl-5 text-m3-on-tertiary-container"
      >
        <Whatsapp className="size-8 shrink-0" />
        <span className="min-w-0 flex-1">
          <span className="pb-type-card-month block text-[1.375rem]">{t("communityTitle")}</span>
          <span className="pb-type-row-line mt-0.5 block opacity-85">{t("communityLine")}</span>
          <span className="sr-only"> {t("newTab")}</span>
        </span>
        <ChevronRight aria-hidden="true" className="size-4 shrink-0" />
      </Stone>
    </a>
  )
}
