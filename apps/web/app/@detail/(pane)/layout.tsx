import { DetailPane } from "@/components/layout/DetailPane"

/**
 * Every detail's frame (#1384, two-area spec D2, D5), hoisted out of the
 * pages it holds: a layout stays mounted while the page under it changes, so
 * moving from one detail to the next swaps the content inside one frame
 * instead of closing one pane and opening another. The slot's null pages sit
 * outside this group, so a closed slot draws no frame at all.
 */
export default function DetailPaneLayout({ children }: { children: React.ReactNode }) {
  return <DetailPane>{children}</DetailPane>
}
