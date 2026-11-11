import type { SVGProps } from "react"
import { REGULAR, drawForm, formKey, type Form, type GlyphForms } from "./forms"

export type GlyphSvgProps = Omit<SVGProps<SVGSVGElement>, "children"> & {
  /** Width and height, 24 by default; size by class where you can (`PBBLS_ICON_SIZE`). */
  size?: number | string
  /** The form to draw (spec D3), regular by default. */
  form?: Form
  /** Fade a newly filled path in (spec D9): for a form that changes in place. Nothing moves under reduced motion. */
  animate?: boolean
}

/**
 * One glyph in one form as an inline SVG: the DNA's 1.75 round stroke in
 * `currentColor`, filled paths in `currentColor` (even-odd and unstroked when
 * carved). Hidden from assistive tech unless labelled, `role="img"` when it is.
 * No hooks: it renders in a Server Component.
 */
export function GlyphSvg({ glyph, size = 24, form = REGULAR, animate = false, ...props }: GlyphSvgProps & { glyph: GlyphForms }) {
  const labelled = props["aria-label"] !== undefined || props["aria-labelledby"] !== undefined
  const paths = drawForm(glyph, form)
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
      data-form={formKey(form)}
      {...props}
    >
      {paths.map((p, i) => (
        <path
          // A path that starts filling is a new element, so the fade starts from its first frame.
          key={`${i}${p.fill ? "f" : "s"}${p.d.length}`}
          d={p.d}
          fill={p.fill ? "currentColor" : undefined}
          fillRule={p.evenOdd ? "evenodd" : undefined}
          stroke={p.stroke ? undefined : "none"}
          className={animate && p.fill ? "pbbls-glyph-fill" : undefined}
        />
      ))}
    </svg>
  )
}
