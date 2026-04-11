import { defaultCache } from "@serwist/turbopack/worker";
import type { PrecacheEntry, SerwistGlobalConfig } from "serwist";
import { NetworkOnly, Serwist } from "serwist";

declare global {
  interface WorkerGlobalScope extends SerwistGlobalConfig {
    __SW_MANIFEST: (PrecacheEntry | string)[] | undefined;
  }
}

declare const self: ServiceWorkerGlobalScope & typeof globalThis;

// defaultCache includes a cross-origin NetworkFirst rule that caches ALL
// requests to *.supabase.co (auth, data, storage) for up to 1 hour with
// a 10 s network timeout fallback. This means a cached 401 from before
// sign-in can be served back after login, causing permanent blank pages.
//
// Fix: prepend a NetworkOnly rule for Supabase so auth and data requests
// always hit the network and are never served from the SW cache.
const runtimeCaching = [
  {
    matcher: ({ url }: { url: URL }) => url.hostname.endsWith(".supabase.co"),
    handler: new NetworkOnly(),
  },
  ...defaultCache,
];

const serwist = new Serwist({
  precacheEntries: self.__SW_MANIFEST,
  precacheOptions: { cleanupOutdatedCaches: true },
  skipWaiting: true,
  clientsClaim: true,
  navigationPreload: true,
  runtimeCaching,
  fallbacks: {
    entries: [
      {
        url: "/offline",
        matcher: ({ request }) => request.destination === "document",
      },
    ],
  },
});

serwist.addEventListeners();
