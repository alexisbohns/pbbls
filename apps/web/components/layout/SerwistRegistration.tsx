"use client";

import { SerwistProvider } from "@serwist/turbopack/react";

export function SerwistRegistration({ children }: { children: React.ReactNode }) {
  return (
    <SerwistProvider
      swUrl="/sw.js"
      disable={process.env.NODE_ENV === "development"}
      reloadOnOnline
    >
      {children}
    </SerwistProvider>
  );
}
