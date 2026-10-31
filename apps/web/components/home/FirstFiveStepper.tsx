import { SplitStone } from "@/components/stones/SplitStone"
import { STEP_LIT_FILL, STEP_UNLIT_FILL } from "./home-fills"

const OPTIONS = { corner: 3, gutter: 5, fillet: 2, lean: 0.9 }

/** The first five's progress (#1384, spec D7, D12): one thin stone cut in five, the done pieces lit. Decorative: the figure says it. */
export function FirstFiveStepper({ count }: { count: number }) {
  return (
    <div aria-hidden="true" className="mt-0.5 mb-2">
      <SplitStone
        n={5}
        height={6}
        seed="first-five"
        options={OPTIONS}
        fill={Array.from({ length: 5 }, (_, k) => (k < count ? STEP_LIT_FILL : STEP_UNLIT_FILL))}
      />
    </div>
  )
}
