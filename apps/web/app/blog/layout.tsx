// The blog (#1209) sets small caps, lining figures and real italics with the
// Ysabeau cuts the Path already ships. The root layout loads Ysabeau upright
// only, so the other faces load here, where they are drawn.
import "@fontsource-variable/ysabeau-sc"
import "@fontsource-variable/ysabeau-office"
import "@fontsource-variable/ysabeau/wght-italic.css"

export default function BlogLayout({ children }: { children: React.ReactNode }) {
  return children
}
