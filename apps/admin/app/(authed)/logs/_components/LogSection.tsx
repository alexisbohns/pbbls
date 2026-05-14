import Link from "next/link"
import {
  Apple,
  Bot,
  CircleCheckBig,
  CircleDotDashed,
  CircleFadingArrowUp,
  CirclePile,
  DatabaseZap,
  Monitor,
  MonitorSmartphone,
  Telescope,
  type LucideIcon,
} from "lucide-react"
import {
  Table,
  TableBody,
  TableCell,
  TableRow,
} from "@/components/ui/table"
import { cn } from "@/lib/utils"
import type { LogPlatform, LogRow } from "@/lib/logs/types"

export type LogSectionVariant =
  | "in_progress"
  | "planned"
  | "backlog"
  | "shipped"
  | "drafts"
  | "published"

type DateField = "updated_at" | "released_at" | "published_at"

type VariantConfig = {
  icon: LucideIcon | null
  iconClass: string
  dateField: DateField
  dateLabel: string
}

const VARIANTS: Record<LogSectionVariant, VariantConfig> = {
  in_progress: {
    icon: CircleDotDashed,
    iconClass: "text-blue-500",
    dateField: "updated_at",
    dateLabel: "Updated",
  },
  planned: {
    icon: CircleFadingArrowUp,
    iconClass: "text-yellow-500",
    dateField: "updated_at",
    dateLabel: "Updated",
  },
  backlog: {
    icon: CirclePile,
    iconClass: "text-muted-foreground",
    dateField: "updated_at",
    dateLabel: "Updated",
  },
  shipped: {
    icon: CircleCheckBig,
    iconClass: "text-green-600",
    dateField: "released_at",
    dateLabel: "Released",
  },
  drafts: {
    icon: null,
    iconClass: "",
    dateField: "updated_at",
    dateLabel: "Updated",
  },
  published: {
    icon: null,
    iconClass: "",
    dateField: "published_at",
    dateLabel: "Published",
  },
}

const PLATFORM_ICONS: Record<LogPlatform, LucideIcon> = {
  ios: Apple,
  webapp: Monitor,
  android: Bot,
  all: MonitorSmartphone,
  infra: DatabaseZap,
  project: Telescope,
}

const PLATFORM_ICON_BG: Record<LogPlatform, string> = {
  ios: "#00000011",
  webapp: "#087ea422",
  android: "#2e924922",
  all: "#C07A7A",
  project: "#C07A7A22",
  infra: "#AA333322",
}

const PLATFORM_ICON_FG: Record<LogPlatform, string> = {
  ios: "#000000",
  webapp: "#087ea4",
  android: "#2e9249",
  all: "#FFFFFF",
  project: "#C07A7A",
  infra: "#AA3333",
}

function formatDate(value: string) {
  const date = new Date(value)
  const monthDay = date.toLocaleString(undefined, {
    month: "short",
    day: "2-digit",
  })
  return `${monthDay} ${date.getHours()}h`
}

function getDateValue(log: LogRow, field: DateField): string {
  const value = log[field]
  return typeof value === "string" && value.length > 0 ? value : log.updated_at
}

function isLogPlatform(value: string): value is LogPlatform {
  return value in PLATFORM_ICONS
}

export function LogSection({
  title,
  logs,
  emptyLabel,
  variant,
}: {
  title: string
  logs: LogRow[]
  emptyLabel: string
  variant: LogSectionVariant
}) {
  const config = VARIANTS[variant]
  const Icon = config.icon

  return (
    <section className="space-y-3">
      <h2 className="flex items-center gap-2 text-base font-semibold">
        {Icon && <Icon className={cn("size-5", config.iconClass)} aria-hidden />}
        <span>{title}</span>
        <span className="text-muted-foreground font-normal">· {logs.length}</span>
      </h2>
      {logs.length === 0 ? (
        <div className="border-border rounded-md border border-dashed p-8 text-center">
          <p className="text-muted-foreground text-sm">{emptyLabel}</p>
        </div>
      ) : (
        <Table>
          <TableBody>
            {logs.map((log) => {
              const platform = isLogPlatform(log.platform) ? log.platform : "all"
              const PlatformIcon = PLATFORM_ICONS[platform]
              return (
                <TableRow
                  key={log.id}
                  className="group border-b-0 hover:bg-transparent"
                >
                  <TableCell className="rounded-l-md group-hover:bg-muted/50">
                    <div className="flex items-center gap-3">
                      <div
                        className="flex size-7 shrink-0 items-center justify-center rounded-md"
                        style={{
                          backgroundColor: PLATFORM_ICON_BG[platform],
                          color: PLATFORM_ICON_FG[platform],
                        }}
                        aria-hidden
                      >
                        <PlatformIcon className="size-3" strokeWidth={3} />
                      </div>
                      <Link
                        href={`/logs/${log.id}`}
                        className="font-medium hover:underline"
                      >
                        {log.title_en}
                      </Link>
                    </div>
                  </TableCell>
                  <TableCell className="text-muted-foreground w-28 rounded-r-md text-right text-xs group-hover:bg-muted/50">
                    {formatDate(getDateValue(log, config.dateField))}
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      )}
    </section>
  )
}
