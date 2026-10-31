import Link from "next/link"
import { ChevronRight } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { FIRST_FIVE_ORDER, type FirstFive } from "@/lib/home/first-five"
import type { FirstFiveTask } from "@/lib/types"
import { FirstFiveStepper } from "./FirstFiveStepper"
import { TaskMark } from "./TaskMark"

const TASK_KEY: Record<FirstFiveTask, "moment" | "pebble" | "collection" | "soul" | "glyph"> = {
  "first-moment": "moment",
  "first-pebble": "pebble",
  "first-collection": "collection",
  "first-soul": "soul",
  "first-glyph": "glyph",
}

type FirstFiveBoxProps = {
  state: FirstFive
  /** Where an open task's row goes; the moment task opens the Quick moment dialog instead. */
  hrefFor: (task: FirstFiveTask) => string
  onQuickMoment: () => void
  onClaim: () => void
  claiming?: boolean
  claimFailed?: boolean
}

const ROW = "flex w-full items-center gap-3 py-[13px] text-left"

/**
 * Your first five (#1384, spec D12): the figure, the cut-stone stepper, the
 * five tasks in Home's order and, once all are done, the claim. Done rows are
 * plain text with their tick; open rows lead straight to the action.
 */
export function FirstFiveBox({ state, hrefFor, onQuickMoment, onClaim, claiming = false, claimFailed = false }: FirstFiveBoxProps) {
  const t = useTranslations("home.firstFive")
  const titleId = "home-first-five-title"
  return (
    <section aria-labelledby={titleId} className="rounded-[26px] bg-m3-surface-container pt-6 pr-4 pb-2.5 pl-[18px]">
      <div className="mb-4 flex items-baseline justify-between gap-3">
        <h2 id={titleId} className="pb-type-section text-m3-on-surface">
          {t("title")}
        </h2>
        <span className="pb-type-figure text-m3-primary">
          {t.rich("count", {
            count: state.count,
            of: (chunks) => <small className="pb-type-figure-small text-m3-on-surface-variant">{chunks}</small>,
          })}
        </span>
      </div>
      <FirstFiveStepper count={state.count} />
      <ol>
        {FIRST_FIVE_ORDER.map((task) => {
          const key = TASK_KEY[task]
          const done = state.done[task]
          const words = (
            <span className="min-w-0 flex-1">
              <span className={done ? "pb-type-row-title block font-medium text-m3-on-surface-variant" : "pb-type-row-title block text-m3-on-surface"}>
                {t(`tasks.${key}.title`)}
                {done && <span className="sr-only">, {t("done")}</span>}
              </span>
              <span className="pb-type-row-line mt-0.5 block text-m3-on-surface-variant">{t(`tasks.${key}.line`)}</span>
            </span>
          )
          const chevron = <ChevronRight aria-hidden="true" className="size-[18px] shrink-0 text-m3-outline" />
          const open = "rounded-xl outline-none focus-visible:ring-2 focus-visible:ring-ring"
          return (
            <li key={task}>
              {done ? (
                <div className={ROW}>
                  <TaskMark done />
                  {words}
                </div>
              ) : task === "first-moment" ? (
                <button type="button" onClick={onQuickMoment} className={`${ROW} ${open}`}>
                  <TaskMark done={false} />
                  {words}
                  {chevron}
                </button>
              ) : (
                <Link href={hrefFor(task)} className={`${ROW} ${open}`}>
                  <TaskMark done={false} />
                  {words}
                  {chevron}
                </Link>
              )}
            </li>
          )
        })}
      </ol>
      {state.state === "claimable" && (
        <div className="flex flex-col items-start gap-2 pt-2 pb-3">
          <p className="pb-type-row-line text-m3-on-surface-variant">{t("claimLine")}</p>
          <Button onClick={onClaim} disabled={claiming} className="h-10 rounded-full px-5">
            {t("claim")}
          </Button>
          {claimFailed && (
            <p role="alert" className="pb-type-row-line text-m3-on-surface-variant italic">
              {t("claimFailed")}
            </p>
          )}
        </div>
      )}
    </section>
  )
}
