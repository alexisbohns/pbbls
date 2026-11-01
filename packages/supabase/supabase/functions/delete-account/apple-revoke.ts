/**
 * Sign in with Apple token revocation on account deletion (#1456, App Store
 * guideline 5.1.1(v)).
 *
 * Supabase never keeps an Apple refresh token for the native idToken flow, so
 * the iOS client hands us a fresh, single-use `authorizationCode` (valid five
 * minutes) with the delete request. We exchange it at Apple's token endpoint
 * for a refresh token, check the token belongs to the Apple identity linked to
 * the account being deleted, then revoke it. Revoking the refresh token ends
 * the app's grant: the app disappears from the person's "Sign in with Apple"
 * list and the next sign-in is a first-time authorization.
 *
 * Both calls authenticate with a client secret: an ES256 JWT signed with the
 * Sign in with Apple key (.p8) from the Apple Developer account.
 *
 * Secrets (supabase secrets set …):
 *   APPLE_TEAM_ID       — the 10-character Team ID
 *   APPLE_KEY_ID        — the key's 10-character Key ID
 *   APPLE_PRIVATE_KEY   — the .p8 file contents (PEM, newlines or literal \n)
 *   APPLE_CLIENT_ID     — optional, defaults to the iOS bundle id
 *
 * Pure apart from the injected `fetch`, so it is unit tested without network.
 */

export const APPLE_TOKEN_URL = "https://appleid.apple.com/auth/token";
export const APPLE_REVOKE_URL = "https://appleid.apple.com/auth/revoke";
export const DEFAULT_CLIENT_ID = "app.pbbls.ios";

export interface AppleConfig {
  teamId: string;
  keyId: string;
  clientId: string;
  privateKeyPem: string;
}

export type RevokeOutcome =
  | { status: "revoked" }
  | { status: "unconfigured" }
  | { status: "sub_mismatch" }
  | { status: "failed"; step: "secret" | "exchange" | "revoke"; detail: string };

/** Reads the Apple secrets; null when any required one is missing. */
export function appleConfigFromEnv(get: (name: string) => string | undefined): AppleConfig | null {
  const teamId = get("APPLE_TEAM_ID")?.trim();
  const keyId = get("APPLE_KEY_ID")?.trim();
  const privateKeyPem = get("APPLE_PRIVATE_KEY")?.replaceAll("\\n", "\n").trim();
  if (!teamId || !keyId || !privateKeyPem) return null;
  return { teamId, keyId, privateKeyPem, clientId: get("APPLE_CLIENT_ID")?.trim() || DEFAULT_CLIENT_ID };
}

/**
 * The Apple `sub` of the account's linked Apple identity, or null when the
 * account has none. GoTrue stores the provider subject as `identity_data.sub`
 * and as `provider_id` (older rows: `id`).
 */
export function appleSubject(
  identities: ReadonlyArray<{
    provider: string;
    id?: string;
    provider_id?: string;
    identity_data?: Record<string, unknown> | null;
  }> | null | undefined,
): string | null {
  const apple = identities?.find((identity) => identity.provider === "apple");
  if (!apple) return null;
  const sub = apple.identity_data?.sub;
  if (typeof sub === "string" && sub) return sub;
  return apple.provider_id || apple.id || null;
}

/** ES256 client secret, valid ten minutes (Apple accepts up to six months). */
export async function buildClientSecret(config: AppleConfig, nowSeconds: number): Promise<string> {
  const header = { alg: "ES256", kid: config.keyId, typ: "JWT" };
  const payload = {
    iss: config.teamId,
    iat: nowSeconds,
    exp: nowSeconds + 600,
    aud: "https://appleid.apple.com",
    sub: config.clientId,
  };
  const signingInput = `${base64url(json(header))}.${base64url(json(payload))}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(config.privateKeyPem),
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"],
  );
  // WebCrypto returns the raw r||s form, which is exactly what JWS ES256 wants.
  const signature = await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" },
    key,
    new TextEncoder().encode(signingInput),
  );
  return `${signingInput}.${base64url(new Uint8Array(signature))}`;
}

/**
 * Exchange `code` and revoke the resulting refresh token. Never throws: the
 * caller decides what a failure means (delete-account logs it and carries on,
 * because erasure must not hinge on Apple being reachable).
 */
export async function revokeAppleAuthorization(args: {
  code: string;
  expectedSub: string;
  config: AppleConfig | null;
  fetch: typeof fetch;
  nowSeconds: number;
}): Promise<RevokeOutcome> {
  const { code, expectedSub, config, nowSeconds } = args;
  if (!config) return { status: "unconfigured" };

  let clientSecret: string;
  try {
    clientSecret = await buildClientSecret(config, nowSeconds);
  } catch (err) {
    return { status: "failed", step: "secret", detail: message(err) };
  }

  let tokens: { refresh_token?: unknown; id_token?: unknown };
  try {
    const res = await args.fetch(APPLE_TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id: config.clientId,
        client_secret: clientSecret,
        code,
        grant_type: "authorization_code",
      }),
    });
    if (!res.ok) {
      return { status: "failed", step: "exchange", detail: `${res.status} ${await res.text()}` };
    }
    tokens = await res.json();
  } catch (err) {
    return { status: "failed", step: "exchange", detail: message(err) };
  }

  if (typeof tokens.refresh_token !== "string" || !tokens.refresh_token) {
    return { status: "failed", step: "exchange", detail: "no refresh_token in Apple's answer" };
  }
  // The id_token comes straight from Apple over TLS, so reading its claims
  // without verifying the signature is sound here.
  const sub = typeof tokens.id_token === "string" ? jwtSubject(tokens.id_token) : null;
  if (sub !== expectedSub) return { status: "sub_mismatch" };

  try {
    const res = await args.fetch(APPLE_REVOKE_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id: config.clientId,
        client_secret: clientSecret,
        token: tokens.refresh_token,
        token_type_hint: "refresh_token",
      }),
    });
    if (!res.ok) {
      return { status: "failed", step: "revoke", detail: `${res.status} ${await res.text()}` };
    }
  } catch (err) {
    return { status: "failed", step: "revoke", detail: message(err) };
  }
  return { status: "revoked" };
}

function jwtSubject(token: string): string | null {
  const part = token.split(".")[1];
  if (!part) return null;
  try {
    const b64 = part.replaceAll("-", "+").replaceAll("_", "/");
    const padded = b64 + "=".repeat((4 - (b64.length % 4)) % 4);
    const claims = JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(padded), (c) => c.charCodeAt(0))));
    return typeof claims?.sub === "string" ? claims.sub : null;
  } catch {
    return null;
  }
}

function pemToDer(pem: string): ArrayBuffer {
  const body = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\s+/g, "");
  const bytes = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
  return bytes.buffer;
}

function json(value: unknown): Uint8Array {
  return new TextEncoder().encode(JSON.stringify(value));
}

function base64url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

function message(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}
