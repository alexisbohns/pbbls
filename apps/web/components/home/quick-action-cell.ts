/**
 * A quick action's cell (#1384, spec D11): the whole stone piece, label at the
 * foot, a focus ring inset into the piece. The first piece's content sits
 * closer to its edge than the cut ones', as in the mock.
 */
export function quickActionCell(index: number): string {
  return [
    "pb-type-label flex h-full w-full flex-col items-start justify-between pt-[15px] pr-3 pb-[14px] text-left text-m3-on-primary-container",
    "rounded-[20px] outline-none focus-visible:outline-2 focus-visible:-outline-offset-4 focus-visible:outline-m3-on-primary-container",
    "has-[:focus-visible]:outline-2 has-[:focus-visible]:-outline-offset-4 has-[:focus-visible]:outline-m3-on-primary-container",
    index === 0 ? "pl-4" : "pl-5",
  ].join(" ")
}
