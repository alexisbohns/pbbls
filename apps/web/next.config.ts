import { withSerwist } from "@serwist/turbopack";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // The address given to the App Store and Play. Temporary, so the page can move.
  async redirects() {
    return [{ source: "/support", destination: "/docs/support", permanent: false }];
  },
};

export default withSerwist(nextConfig);