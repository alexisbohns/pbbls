import { createSerwistRoute } from "@serwist/turbopack";

export const { dynamic, dynamicParams, revalidate, generateStaticParams, GET } =
  createSerwistRoute({
    swSrc: "app/sw.ts",
    globDirectory: ".",
    globPatterns: [
      "public/**/*.{js,css,html,png,svg,ico,webmanifest}",
      ".next/static/**/*.{js,css}",
    ],
    injectionPoint: "self.__SW_MANIFEST",
  });
