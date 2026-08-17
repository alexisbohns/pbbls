// Serves /.well-known/assetlinks.json for Android App Links (M49, design D11).
//
// A route handler rather than a public/ static file so the signing fingerprint
// comes from ANDROID_ASSETLINKS_SHA256 at request time: the static version
// shipped a placeholder that was never substituted, and nothing could catch it
// (#697). See lib/deep-links/assetlinks.ts for the reasoning and the format.
//
// Google's verifier fetches this on install and caches it, so a corrected env
// var can take a while to reach devices.
import {
  buildAssetLinks,
  type AssetLinksStatement,
} from "@/lib/deep-links/assetlinks"

// Read the env var per request, not at build time — the point of the env var is
// that setting or rotating the fingerprint needs no redeploy.
export const dynamic = "force-dynamic"

export function GET() {
  const statements: AssetLinksStatement[] | null = buildAssetLinks(
    process.env.ANDROID_ASSETLINKS_SHA256,
  )

  // 404 rather than a statement list with a bogus fingerprint: an unconfigured
  // environment (preview, fork, local) should look unconfigured. Serving a
  // syntactically valid file that can never verify is exactly the failure mode
  // that hid this bug for a whole milestone.
  if (!statements) {
    return new Response(
      JSON.stringify({
        error: "ANDROID_ASSETLINKS_SHA256 is not set to a valid SHA-256 fingerprint",
      }),
      { status: 404, headers: { "Content-Type": "application/json" } },
    )
  }

  return new Response(JSON.stringify(statements), {
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "public, max-age=3600",
    },
  })
}
