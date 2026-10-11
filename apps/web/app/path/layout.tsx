// The Spine (#1052) sets small caps and lining figures with the real Ysabeau
// cuts. They load only where the Path is drawn, not app-wide.
import "@fontsource-variable/ysabeau-sc"
import "@fontsource-variable/ysabeau-office"

export default function PathLayout({ children }: { children: React.ReactNode }) {
  return children
}
