/**
 * A refusal from the `compose-pebble` edge function (the old composer's
 * create; deleted with it in #1050 Part 5).
 *
 * It answers a failed RPC with `400 { error: "<RPC message>" }`, and since the
 * moments split (#1046) the RPC messages are slugs (`pebble_needs_domain`,
 * `moment_empty`, `soul_not_owned`, …). The slug is what a composer branches on
 * to say something better than "try again".
 */
export class ComposeError extends Error {
  readonly slug: string

  constructor(fn: string, slug: string) {
    super(`${fn} failed: ${slug}`)
    this.name = "ComposeError"
    this.slug = slug
  }
}

/** The server's slug when `err` is a compose refusal, else null. */
export function composeErrorSlug(err: unknown): string | null {
  return err instanceof ComposeError ? err.slug : null
}

/**
 * Which `record.draft` message a failed publish shows: the server's refusal in
 * words when the composer can act on it, else the generic "try again".
 */
export function publishErrorMessageKey(err: unknown): "needsDomain" | "publishFailed" {
  return composeErrorSlug(err) === "pebble_needs_domain" ? "needsDomain" : "publishFailed"
}

/**
 * The `error` field of an edge function's JSON error body, or null when there
 * is no readable body (network failure, relay error, non-JSON response).
 * `context` is what supabase-js puts on a `FunctionsHttpError`: the Response.
 */
export async function readEdgeErrorBody(context: unknown): Promise<string | null> {
  if (!(context instanceof Response)) return null
  try {
    const body: unknown = await context.clone().json()
    if (typeof body === "object" && body !== null) {
      const message = (body as Record<string, unknown>).error
      if (typeof message === "string" && message.trim() !== "") return message
    }
  } catch {
    // Not JSON: fall through to the generic message.
  }
  return null
}
