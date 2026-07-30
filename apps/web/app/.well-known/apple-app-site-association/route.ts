// Serves /.well-known/apple-app-site-association for iOS universal links
// (M49, design D11). A route handler rather than a public/ static file
// because the extension-less path must carry an explicit application/json
// Content-Type. Dotted route segments are sanctioned by the Next docs
// (node_modules/next/dist/docs/01-app/02-guides/backend-for-frontend.md
// lists `.well-known` among custom route-handler endpoints).
//
// Modern (iOS 13+) appIDs + components format. Team id from
// apps/ios/project.yml:15, bundle id from :51. Apple's CDN caches this file —
// changes can take a while to propagate to devices.
export function GET() {
  return new Response(
    JSON.stringify({
      applinks: {
        details: [
          {
            appIDs: ["256Z7G8WLM.app.pbbls.ios"],
            components: [{ "/": "/invite/*" }],
          },
        ],
      },
    }),
    { headers: { "Content-Type": "application/json" } },
  )
}
