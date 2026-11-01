import { GuidePage } from "@/components/home/GuidePage"

export default async function GuideRoute({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  return <GuidePage slug={slug} />
}
