// The Spine (#1052) sets small caps and lining figures with the real Ysabeau
// cuts and its italics with the italic face; they load where the Path is drawn.
import "@fontsource-variable/ysabeau-sc"
import "@fontsource-variable/ysabeau-office"
import "@fontsource-variable/ysabeau/wght-italic.css"
import { PathTab } from "@/components/path/PathTab"
import { M3Island } from "@/components/layout/M3Island"
import { TabArea } from "@/components/layout/TabArea"

/**
 * The tab's left area (#1384, two-area spec D2): its page, held by this group
 * layout so it stays mounted while a detail of the tab opens beside it (from
 * 1024 px) or over it (below). The route's own page renders under it: null on
 * the tab root and on a detail, the page itself on a route not moved yet.
 */
export default function PathGroupLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <TabArea tab="/path">
        <M3Island className="h-full">
          <PathTab />
        </M3Island>
      </TabArea>
      {children}
    </>
  )
}
