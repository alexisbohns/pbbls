// You (#1384) sets its italic lines in Ysabeau's italic cut (the root layout
// loads it upright only) and wears the brand M3 roles as an island (spec D3).
import "@fontsource-variable/ysabeau/wght-italic.css"
import { YouScreen } from "@/components/you/YouScreen"
import { M3Island } from "@/components/layout/M3Island"
import { TabArea } from "@/components/layout/TabArea"

/**
 * The tab's left area (#1384, two-area spec D2): its page, held by this group
 * layout so it stays mounted while a detail of the tab opens beside it (from
 * 1024 px) or over it (below). The route's own page renders under it: null on
 * the tab root and on a detail, the page itself on a route not moved yet.
 */
export default function YouGroupLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <TabArea tab="/profile">
        <M3Island>
          <YouScreen />
        </M3Island>
      </TabArea>
      {children}
    </>
  )
}
