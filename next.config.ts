import { withSerwist } from "@serwist/turbopack";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {};

export default withSerwist(nextConfig);

module.exports = {
  allowedDevOrigins: ['192.168.1.165'],
}