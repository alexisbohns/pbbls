import type { GuideRow } from "@/lib/guides/types"

const base = { created_at: "2026-10-27T09:00:00Z", updated_at: "2026-10-27T09:00:00Z" }

export const denseGuidesFixture: GuideRow[] = [
  { id: "g1", slug: "pebbles", sort_order: 10, illustration: "pebbles", title_en: "About Pebbles", title_fr: "À propos de Pebbles", summary_en: "The private memory of your life.", summary_fr: "La mémoire privée de ta vie.", body_md_en: "# Pebbles", body_md_fr: "# Pebbles", published: true, ...base },
  { id: "g2", slug: "stones", sort_order: 20, illustration: "stones", title_en: "About the stones", title_fr: "À propos des pierres", summary_en: "How a stone is built.", summary_fr: "Comment une pierre naît.", body_md_en: "# Stones", body_md_fr: null, published: true, ...base },
  { id: "g3", slug: "karma", sort_order: 90, illustration: null, title_en: "About karma", title_fr: null, summary_en: "A quiet count.", summary_fr: null, body_md_en: "# Karma", body_md_fr: null, published: false, ...base },
]

export const emptyGuidesFixture: GuideRow[] = []
