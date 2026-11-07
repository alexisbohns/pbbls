type DocsHeaderProps = {
  kicker: string
  title: string
  lead?: string
}

/**
 * A docs page opens like a week on the Path: a spaced small-caps kicker, the
 * title large and light, an italic line under it. The title is the `<h1>`.
 */
export function DocsHeader({ kicker, title, lead }: DocsHeaderProps) {
  return (
    <header className="mb-8">
      <p className="font-spine-sc text-xs font-semibold tracking-[.2em] text-m3-on-surface-variant uppercase">{kicker}</p>
      <h1 className="mt-4 font-spine text-[40px] leading-[1.02] font-light tracking-[-.026em] text-balance text-m3-on-surface [font-variant-numeric:lining-nums] lg:text-[52px]">
        {title}
      </h1>
      {lead && <p className="mt-3 font-spine text-lg font-[380] text-m3-on-surface-variant italic">{lead}</p>}
    </header>
  )
}
