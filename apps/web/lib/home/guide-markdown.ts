import rehypeStringify from "rehype-stringify"
import remarkParse from "remark-parse"
import remarkRehype from "remark-rehype"
import { unified } from "unified"

// The guides' bodies (#1384, spec D14) through the processor AnnouncementDetail
// uses: Markdown to HTML, raw HTML dropped (remark-rehype without
// `allowDangerousHtml`), so a body can never inject markup.
const processor = unified().use(remarkParse).use(remarkRehype).use(rehypeStringify)

export function renderGuideMarkdown(md: string): string {
  return String(processor.processSync(md))
}
