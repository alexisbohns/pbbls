/**
 * Apple token revocation — client secret shape and signature, the exchange →
 * subject check → revoke sequence, and every failure branch, against a fake
 * fetch. No network.
 *
 * Run: deno test packages/supabase/supabase/functions/delete-account/
 */

import { assert, assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";

import {
  APPLE_REVOKE_URL,
  APPLE_TOKEN_URL,
  type AppleConfig,
  appleConfigFromEnv,
  appleSubject,
  buildClientSecret,
  DEFAULT_CLIENT_ID,
  revokeAppleAuthorization,
} from "./apple-revoke.ts";

const NOW = 1_800_000_000;
const SUB = "001234.abcdef0123456789.0042";

async function testKeys(): Promise<{ config: AppleConfig; publicKey: CryptoKey }> {
  const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const der = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
  let binary = "";
  for (const byte of der) binary += String.fromCharCode(byte);
  const body = btoa(binary).match(/.{1,64}/g)!.join("\n");
  return {
    config: {
      teamId: "TEAM123456",
      keyId: "KEY1234567",
      clientId: DEFAULT_CLIENT_ID,
      privateKeyPem: `-----BEGIN PRIVATE KEY-----\n${body}\n-----END PRIVATE KEY-----`,
    },
    publicKey: pair.publicKey,
  };
}

function b64urlDecode(part: string): Uint8Array<ArrayBuffer> {
  const b64 = part.replaceAll("-", "+").replaceAll("_", "/");
  return Uint8Array.from(atob(b64 + "=".repeat((4 - (b64.length % 4)) % 4)), (c) => c.charCodeAt(0));
}

function decodeJson(part: string): Record<string, unknown> {
  return JSON.parse(new TextDecoder().decode(b64urlDecode(part)));
}

function idToken(sub: string): string {
  const enc = (v: unknown) => btoa(JSON.stringify(v)).replaceAll("=", "").replaceAll("+", "-").replaceAll("/", "_");
  return `${enc({ alg: "RS256" })}.${enc({ sub, aud: DEFAULT_CLIENT_ID })}.sig`;
}

interface Call {
  url: string;
  form: URLSearchParams;
}

function fakeFetch(responses: Record<string, Response | Error>): { fetch: typeof fetch; calls: Call[] } {
  const calls: Call[] = [];
  const fetchImpl = ((input: string | URL | Request, init?: RequestInit) => {
    const url = String(input);
    calls.push({ url, form: new URLSearchParams(String(init?.body)) });
    const answer = responses[url];
    if (!answer) throw new Error(`unexpected ${url}`);
    if (answer instanceof Error) return Promise.reject(answer);
    return Promise.resolve(answer.clone());
  }) as typeof fetch;
  return { fetch: fetchImpl, calls };
}

const tokenOk = (sub = SUB) =>
  new Response(JSON.stringify({ access_token: "a", refresh_token: "r-token", id_token: idToken(sub) }), { status: 200 });

Deno.test("client secret is a verifiable ES256 JWT with Apple's claims", async () => {
  const { config, publicKey } = await testKeys();
  const jwt = await buildClientSecret(config, NOW);
  const [h, p, s] = jwt.split(".");
  assertEquals(decodeJson(h), { alg: "ES256", kid: "KEY1234567", typ: "JWT" });
  assertEquals(decodeJson(p), {
    iss: "TEAM123456",
    iat: NOW,
    exp: NOW + 600,
    aud: "https://appleid.apple.com",
    sub: DEFAULT_CLIENT_ID,
  });
  const ok = await crypto.subtle.verify(
    { name: "ECDSA", hash: "SHA-256" },
    publicKey,
    b64urlDecode(s),
    new TextEncoder().encode(`${h}.${p}`),
  );
  assert(ok, "signature verifies with the public key");
});

Deno.test("exchanges the code, then revokes the refresh token", async () => {
  const { config } = await testKeys();
  const { fetch, calls } = fakeFetch({ [APPLE_TOKEN_URL]: tokenOk(), [APPLE_REVOKE_URL]: new Response("", { status: 200 }) });
  const outcome = await revokeAppleAuthorization({ code: "c-123", expectedSub: SUB, config, fetch, nowSeconds: NOW });
  assertEquals(outcome, { status: "revoked" });
  assertEquals(calls.map((c) => c.url), [APPLE_TOKEN_URL, APPLE_REVOKE_URL]);
  assertEquals(calls[0].form.get("code"), "c-123");
  assertEquals(calls[0].form.get("grant_type"), "authorization_code");
  assertEquals(calls[0].form.get("client_id"), DEFAULT_CLIENT_ID);
  assertEquals(calls[1].form.get("token"), "r-token");
  assertEquals(calls[1].form.get("token_type_hint"), "refresh_token");
  assert(calls[1].form.get("client_secret")?.split(".").length === 3);
});

Deno.test("missing secrets → unconfigured, no network", async () => {
  const { fetch, calls } = fakeFetch({});
  const outcome = await revokeAppleAuthorization({ code: "c", expectedSub: SUB, config: null, fetch, nowSeconds: NOW });
  assertEquals(outcome, { status: "unconfigured" });
  assertEquals(calls.length, 0);
});

Deno.test("a code for another Apple ID is never revoked", async () => {
  const { config } = await testKeys();
  const { fetch, calls } = fakeFetch({ [APPLE_TOKEN_URL]: tokenOk("someone-else") });
  const outcome = await revokeAppleAuthorization({ code: "c", expectedSub: SUB, config, fetch, nowSeconds: NOW });
  assertEquals(outcome, { status: "sub_mismatch" });
  assertEquals(calls.length, 1);
});

Deno.test("a rejected code fails at the exchange", async () => {
  const { config } = await testKeys();
  const { fetch } = fakeFetch({ [APPLE_TOKEN_URL]: new Response('{"error":"invalid_grant"}', { status: 400 }) });
  const outcome = await revokeAppleAuthorization({ code: "c", expectedSub: SUB, config, fetch, nowSeconds: NOW });
  assertEquals(outcome, { status: "failed", step: "exchange", detail: '400 {"error":"invalid_grant"}' });
});

Deno.test("a network error at the revoke is reported, not thrown", async () => {
  const { config } = await testKeys();
  const { fetch } = fakeFetch({ [APPLE_TOKEN_URL]: tokenOk(), [APPLE_REVOKE_URL]: new Error("offline") });
  const outcome = await revokeAppleAuthorization({ code: "c", expectedSub: SUB, config, fetch, nowSeconds: NOW });
  assertEquals(outcome, { status: "failed", step: "revoke", detail: "offline" });
});

Deno.test("a malformed key fails at the secret", async () => {
  const { config } = await testKeys();
  const { fetch, calls } = fakeFetch({});
  const outcome = await revokeAppleAuthorization({
    code: "c",
    expectedSub: SUB,
    config: { ...config, privateKeyPem: "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----" },
    fetch,
    nowSeconds: NOW,
  });
  assertEquals(outcome.status, "failed");
  assertEquals(calls.length, 0);
});

Deno.test("config reads secrets, unescapes \\n and defaults the client id", () => {
  const env: Record<string, string> = {
    APPLE_TEAM_ID: " TEAM123456 ",
    APPLE_KEY_ID: "KEY1234567",
    APPLE_PRIVATE_KEY: "-----BEGIN PRIVATE KEY-----\\nAAAA\\n-----END PRIVATE KEY-----",
  };
  assertEquals(appleConfigFromEnv((k) => env[k]), {
    teamId: "TEAM123456",
    keyId: "KEY1234567",
    clientId: DEFAULT_CLIENT_ID,
    privateKeyPem: "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----",
  });
  assertEquals(appleConfigFromEnv((k) => (k === "APPLE_KEY_ID" ? undefined : env[k])), null);
});

Deno.test("apple subject comes from identity_data.sub, then provider_id", () => {
  assertEquals(appleSubject([{ provider: "email", id: "x" }]), null);
  assertEquals(appleSubject(null), null);
  assertEquals(appleSubject([{ provider: "apple", id: "row", provider_id: "pid", identity_data: { sub: SUB } }]), SUB);
  assertEquals(appleSubject([{ provider: "apple", id: "row", provider_id: "pid", identity_data: {} }]), "pid");
});
