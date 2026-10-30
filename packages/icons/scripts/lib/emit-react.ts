/**
 * The React emitter (spec §5): `packages/icons/react/<Name>.tsx` per icon, plus
 * `types.ts` and `index.ts`. A component is a plain function with no hooks and
 * no `"use client"`, so it renders in a Server Component; `aria-hidden` unless
 * labelled, `role="img"` when it is.
 */
import { GENERATED_NOTICE, type EmittedIcon, type Emitter } from "./emit"
import { pascalName } from "./names"
import { type ParsedPath } from "./parse"

export const REACT_DIR = "packages/icons/react"

const HEADER = `// ${GENERATED_NOTICE}\n`

const TYPES = `${HEADER}import type { SVGProps } from "react"

/** Every SVG prop but \`children\`, plus \`size\` (width and height, 24 by default). */
export type PbblsIconProps = Omit<SVGProps<SVGSVGElement>, "children"> & { size?: number | string }
`

function pathElement(path: ParsedPath): string {
  const attrs = [`d="${path.d}"`]
  if (path.filled) attrs.push('fill="currentColor"')
  if (path.evenOdd) attrs.push('fillRule="evenodd"')
  if (path.unstroked) attrs.push('stroke="none"')
  return `      <path ${attrs.join(" ")} />`
}

function docComment(icon: EmittedIcon): string {
  const meaning = icon.meaning.replace(/\*\//g, "* /")
  const twin = icon.variant === null ? "" : ` (the \`${icon.variant}\` twin)`
  return `/** \`${icon.name}\` — ${meaning}${twin} */`
}

export function componentFile(icon: EmittedIcon): string {
  return `${HEADER}import type { PbblsIconProps } from "./types"

${docComment(icon)}
export function ${pascalName(icon.name)}({ size = 24, ...props }: PbblsIconProps) {
  const labelled = props["aria-label"] !== undefined || props["aria-labelledby"] !== undefined
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden={labelled ? undefined : true}
      role={labelled ? "img" : undefined}
      focusable="false"
      {...props}
    >
${icon.paths.map(pathElement).join("\n")}
    </svg>
  )
}
`
}

function indexFile(icons: EmittedIcon[]): string {
  const lines = icons.map((icon) => {
    const name = pascalName(icon.name)
    return `export { ${name} } from "./${name}"`
  })
  return `${HEADER}export type { PbblsIconProps } from "./types"\n${lines.join("\n")}\n`
}

export const reactEmitter: Emitter = {
  name: "react",
  owned: { dirs: [REACT_DIR], files: [], patterns: [] },
  emit(icons) {
    const out = new Map<string, string>()
    for (const icon of icons) out.set(`${REACT_DIR}/${pascalName(icon.name)}.tsx`, componentFile(icon))
    out.set(`${REACT_DIR}/types.ts`, TYPES)
    out.set(`${REACT_DIR}/index.ts`, indexFile(icons))
    return out
  },
}
