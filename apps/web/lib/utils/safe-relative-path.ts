/**
 * Open-redirect guard for the auth `next` param (M49, design D12). A
 * destination is accepted only when it is STRICTLY relative: starts with a
 * single "/", is not protocol-relative ("//host"), and carries no scheme
 * separator or backslash (browsers normalize "\" to "/", which would smuggle
 * "/\evil.com" past a naive prefix check). Shared by the OAuth `redirectTo`
 * builder, the login page's post-auth redirect, and `/auth/callback`.
 */
export function isSafeRelativePath(value: string | null | undefined): value is string {
  return (
    typeof value === "string" &&
    value.startsWith("/") &&
    !value.startsWith("//") &&
    !value.includes(":") &&
    !value.includes("\\")
  )
}
