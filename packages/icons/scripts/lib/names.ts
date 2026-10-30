/**
 * The spellings every surface derives from a manifest name (spec D5).
 *
 * A manifest name is kebab-case (`check-circle`); a twin is `<name>.<variant>`
 * where the variant is `active`, `lit-1`, `lit-2` or `lit-3`. The dot and the
 * dashes are the only separators, so every spelling is a fold of the same words.
 */

const words = (name: string): string[] => name.split(/[.-]/).filter((w) => w.length > 0)

const capitalize = (w: string): string => w.charAt(0).toUpperCase() + w.slice(1)

/** `check-circle` → `CheckCircle`, `path.lit-2` → `PathLit2` (the React component). */
export function pascalName(name: string): string {
  return words(name).map(capitalize).join("")
}

/** `check-circle` → `checkCircle` (the Swift case). */
export function camelName(name: string): string {
  const [first, ...rest] = words(name)
  return (first ?? "") + rest.map(capitalize).join("")
}

/** `check-circle` → `CHECK_CIRCLE` (the Kotlin entry). */
export function constantName(name: string): string {
  return words(name).map((w) => w.toUpperCase()).join("_")
}

/** `check-circle` → `ic_pbbls_check_circle` (the Android drawable). */
export function drawableName(name: string): string {
  return "ic_pbbls_" + words(name).join("_")
}

/** `check-circle` → `pbbls-check-circle` (the iOS asset). */
export function assetName(name: string): string {
  return "pbbls-" + words(name).join("-")
}

/** `home.active` → `home.active.svg`; `home` → `home.svg` (the source file). */
export function sourceFile(name: string): string {
  return `${name}.svg`
}
