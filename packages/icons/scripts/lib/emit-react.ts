/**
 * The React emitter (spec §5): `packages/icons/react/<Name>.tsx` per icon, plus
 * `types.ts` and `index.ts`. A component is a plain function with no hooks and
 * no `"use client"`, so it renders in a Server Component; `aria-hidden` unless
 * labelled, `role="img"` when it is.
 */
import { GENERATED_NOTICE, type EmittedIcon, type Emitter } from "./emit"
import { loadGlyphsOnce } from "./emit-glyphs"
import { formsOf, type GlyphData, type GlyphForms } from "./forms"
import { pascalName } from "./names"
import { type ParsedPath } from "./parse"
import { loadStates, type StateTable } from "./states"

export const REACT_DIR = "packages/icons/react"

const HEADER = `// ${GENERATED_NOTICE}\n`

const TYPES = `${HEADER}import type { FunctionComponent, SVGProps } from "react"
import type { GlyphSvgProps } from "../runtime/GlyphSvg"

/** A twin's props (static drawing, until part 7): every SVG prop but \`children\`, plus \`size\`. */
export type PbblsIconProps = Omit<SVGProps<SVGSVGElement>, "children"> & { size?: number | string }

/** A glyph's props: a twin's, plus \`form\` and \`animate\`. */
export type PbblsGlyphProps = GlyphSvgProps

/** A glyph component: it draws any of its forms, listed in \`forms\` (form keys, generator order). */
export type PbblsGlyphComponent = FunctionComponent<PbblsGlyphProps> & { readonly forms: readonly string[] }
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

/** A base component: its glyph's drawing data inlined (tree-shaken per import), its form keys attached, drawn by `GlyphSvg`. */
export function glyphComponentFile(glyph: GlyphData): string {
  const name = pascalName(glyph.name)
  const meaning = glyph.meaning.replace(/\*\//g, "* /")
  const drawing: GlyphForms = { name: glyph.name, paths: glyph.paths, parts: glyph.parts, steps: glyph.steps, carved: glyph.carved, dotted: glyph.dotted, overrides: glyph.overrides }
  return `${HEADER}import type { GlyphForms } from "../runtime/forms"
import { GlyphSvg } from "../runtime/GlyphSvg"
import type { PbblsGlyphProps } from "./types"

const GLYPH: GlyphForms = ${JSON.stringify(drawing)}

/** \`${glyph.name}\` — ${meaning} */
export function ${name}(props: PbblsGlyphProps) {
  return <GlyphSvg glyph={GLYPH} {...props} />
}
${name}.forms = ${JSON.stringify(formsOf(glyph))} as const
`
}

function statesFile(states: StateTable): string {
  return `${HEADER}export const STATES = ${JSON.stringify(states)} as const

export type PbblsGlyphState = keyof typeof STATES
`
}

function indexFile(icons: EmittedIcon[]): string {
  const lines = icons.map((icon) => {
    const name = pascalName(icon.name)
    return `export { ${name} } from "./${name}"`
  })
  return `${HEADER}export type { PbblsIconProps, PbblsGlyphProps, PbblsGlyphComponent } from "./types"
export { STATES, type PbblsGlyphState } from "./states"
export { glyphForm, REGULAR, MARKED, SOLID, DOTTED, progress, type Form } from "../runtime/public"
${lines.join("\n")}\n`
}

/** Every file of `react/`: a base component per glyph, a static twin per variant, the types, the states and the index. */
export function reactFiles(icons: EmittedIcon[], glyphs: readonly GlyphData[], states: StateTable): Map<string, string> {
  const byName = new Map(glyphs.map((g) => [g.name, g]))
  const out = new Map<string, string>()
  for (const icon of icons) {
    const path = `${REACT_DIR}/${pascalName(icon.name)}.tsx`
    if (icon.variant !== null) {
      out.set(path, componentFile(icon))
      continue
    }
    const glyph = byName.get(icon.name)
    if (!glyph) throw new Error(`${icon.name}: no glyph for this icon`)
    out.set(path, glyphComponentFile(glyph))
  }
  out.set(`${REACT_DIR}/types.ts`, TYPES)
  out.set(`${REACT_DIR}/states.ts`, statesFile(states))
  out.set(`${REACT_DIR}/index.ts`, indexFile(icons))
  return out
}

export function reactEmitter(packageRoot: string): Emitter {
  return {
    name: "react",
    owned: { dirs: [REACT_DIR], files: [], patterns: [] },
    emit: (icons) => reactFiles(icons, loadGlyphsOnce(packageRoot), loadStates(packageRoot)),
  }
}
