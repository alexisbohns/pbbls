import { GuidePage } from "@/components/home/GuidePage"

/** A guide, in the detail area beside Home (#1384, two-area spec D2). */
export default async function GuideDetail({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  return <GuidePage slug={slug} />
}
