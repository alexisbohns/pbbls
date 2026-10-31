// A guide page (#1384, spec D14) wears Home's M3 island and its italic cut.
import "@fontsource-variable/ysabeau/wght-italic.css"
import { M3Island } from "@/components/layout/M3Island"

export default function GuidesLayout({ children }: { children: React.ReactNode }) {
  return <M3Island>{children}</M3Island>
}
