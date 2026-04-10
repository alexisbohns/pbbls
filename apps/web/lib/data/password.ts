/**
 * Simple password hashing using the Web Crypto API (SHA-256).
 *
 * This is intentionally lightweight — passwords are stored in localStorage and
 * this auth layer is temporary scaffolding for a future Supabase migration
 * where server-side bcrypt/argon2 will take over.
 */

const SALT_PREFIX = "pbbls:"

function toHex(buffer: ArrayBuffer): string {
  return Array.from(new Uint8Array(buffer))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("")
}

export async function hashPassword(password: string): Promise<string> {
  const data = new TextEncoder().encode(SALT_PREFIX + password)
  const digest = await crypto.subtle.digest("SHA-256", data)
  return toHex(digest)
}

export async function verifyPassword(
  password: string,
  hash: string,
): Promise<boolean> {
  const computed = await hashPassword(password)
  return computed === hash
}
