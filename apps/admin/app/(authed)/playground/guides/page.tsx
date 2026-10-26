import { GuideTable } from "../../guides/_components/GuideTable"
import { denseGuidesFixture, emptyGuidesFixture } from "../../guides/__fixtures__/guides"

export default function GuidesPlaygroundPage() {
  return (
    <section className="space-y-10">
      <h1 className="text-2xl font-semibold">Playground · Guides</h1>
      <div className="space-y-3"><h2 className="font-medium">Dense (published, FR without body, draft without FR)</h2><GuideTable guides={denseGuidesFixture} /></div>
      <div className="space-y-3"><h2 className="font-medium">Empty</h2><GuideTable guides={emptyGuidesFixture} /></div>
    </section>
  )
}
