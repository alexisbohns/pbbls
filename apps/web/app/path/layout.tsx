// The Spine (#1052) sets small caps and lining figures with the real Ysabeau
// cuts. They load only where the Path is drawn, not app-wide.
import "@fontsource-variable/ysabeau-sc"
import "@fontsource-variable/ysabeau-office"
// The root layout loads Ysabeau upright only; without the italic face the
// browser slants the upright glyphs instead of setting the real italic.
import "@fontsource-variable/ysabeau/wght-italic.css"

export default function PathLayout({ children }: { children: React.ReactNode }) {
  return children
}
