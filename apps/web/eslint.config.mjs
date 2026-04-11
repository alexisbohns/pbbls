import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // Serwist build artifacts:
    "public/sw.js",
    "public/sw.js.map",
  ]),
  {
    rules: {
      "@typescript-eslint/no-explicit-any": "error",
      "no-restricted-imports": ["error", {
        patterns: [
          {
            group: ["@/lib/data/local-provider"],
            message: "Import data hooks from @/lib/data/ instead. Only DataProvider.tsx may import providers directly.",
          },
          {
            group: ["@/lib/data/supabase-provider"],
            message: "Import data hooks from @/lib/data/ instead. Only DataProvider.tsx may import providers directly.",
          },
        ],
      }],
    },
  },
  {
    files: ["components/layout/DataProvider.tsx"],
    rules: {
      "no-restricted-imports": "off",
    },
  },
]);

export default eslintConfig;
