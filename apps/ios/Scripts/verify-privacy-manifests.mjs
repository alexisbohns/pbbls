#!/usr/bin/env node
// Asserts that both shippable bundles carry a privacy manifest, and that the
// required-reason API declarations still match what the Swift sources actually
// call. A presence check that only checks presence rots; this one fails when the
// code moves out from under it, in both directions.
//
// Runs on plain Node (no Xcode), so CI gates it without a macOS runner.
import { readFileSync, existsSync, readdirSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, relative } from "node:path";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const failures = [];
const fail = (msg) => failures.push(msg);

const BUNDLES = [
  { name: "Pebbles", manifest: "Pebbles/Resources/PrivacyInfo.xcprivacy", sources: ["Pebbles"] },
  { name: "PebblesWidget", manifest: "PebblesWidget/PrivacyInfo.xcprivacy", sources: ["PebblesWidget"] },
];

// Required-reason categories and the symbols that trigger each. Crude on
// purpose: a false positive fails loud with file:line so a human decides.
const CATEGORIES = {
  NSPrivacyAccessedAPICategoryUserDefaults: ["@AppStorage", "UserDefaults"],
  NSPrivacyAccessedAPICategoryFileTimestamp: [
    "creationDate",
    "modificationDate",
    "attributesOfItem",
    "NSURLContentModificationDateKey",
    "stat(",
    "fstat",
  ],
  NSPrivacyAccessedAPICategorySystemBootTime: ["systemUptime", "mach_absolute_time"],
  NSPrivacyAccessedAPICategoryDiskSpace: ["volumeAvailableCapacity", "systemFreeSize"],
  NSPrivacyAccessedAPICategoryActiveKeyboards: ["activeInputModes"],
};

/** Minimal plist reader for the seven-key subset a manifest uses. */
const parseManifest = (xml) => {
  const bool = (key) => {
    const m = xml.match(new RegExp(`<key>${key}</key>\\s*<(true|false)\\s*/>`));
    return m ? m[1] === "true" : undefined;
  };
  const arrayBody = (key) => {
    const open = xml.indexOf(`<key>${key}</key>`);
    if (open === -1) return undefined;
    const rest = xml.slice(open);
    if (/^<key>[^<]+<\/key>\s*<array\s*\/>/.test(rest)) return "";
    const start = rest.indexOf("<array>");
    if (start === -1) return undefined;
    let depth = 0;
    for (let i = start; i < rest.length; i += 1) {
      if (rest.startsWith("<array>", i)) depth += 1;
      else if (rest.startsWith("</array>", i)) {
        depth -= 1;
        if (depth === 0) return rest.slice(start + 7, i);
      }
    }
    return undefined;
  };
  return { bool, arrayBody };
};

/** Strip // line comments, /* block comments *​/ and string literals before matching. */
const stripNoise = (line) => line.replace(/\/\/.*$/, "").replace(/"(?:[^"\\]|\\.)*"/g, '""');

const swiftFiles = (dir) => {
  const out = [];
  const walk = (d) => {
    for (const entry of readdirSync(d)) {
      const full = join(d, entry);
      if (statSync(full).isDirectory()) walk(full);
      else if (entry.endsWith(".swift")) out.push(full);
    }
  };
  if (existsSync(dir)) walk(dir);
  return out;
};

for (const bundle of BUNDLES) {
  const manifestPath = join(root, bundle.manifest);

  // 1. Presence, at the exact path.
  if (!existsSync(manifestPath)) {
    fail(`${bundle.name}: no privacy manifest at ${bundle.manifest} (ITMS-91053 on upload).`);
    continue;
  }
  // 5. Never under Shared/, which compiles into both targets.
  if (bundle.manifest.includes("Shared/")) {
    fail(`${bundle.name}: manifest must not live under Shared/ — one per shippable bundle.`);
  }

  const xml = readFileSync(manifestPath, "utf8");
  // 2. Parses and is not empty.
  if (!xml.includes("<plist") || !xml.includes("</plist>")) {
    fail(`${bundle.name}: ${bundle.manifest} is not a plist.`);
    continue;
  }
  const { bool, arrayBody } = parseManifest(xml);

  // 3. Tracking posture.
  if (bool("NSPrivacyTracking") !== false) {
    fail(`${bundle.name}: NSPrivacyTracking must be present and false.`);
  }
  const domains = arrayBody("NSPrivacyTrackingDomains");
  if (domains === undefined) {
    fail(`${bundle.name}: NSPrivacyTrackingDomains missing.`);
  } else if (domains.trim() !== "") {
    fail(`${bundle.name}: NSPrivacyTrackingDomains must be empty while NSPrivacyTracking is false.`);
  }
  const accessed = arrayBody("NSPrivacyAccessedAPITypes");
  if (accessed === undefined) fail(`${bundle.name}: NSPrivacyAccessedAPITypes missing.`);
  if (arrayBody("NSPrivacyCollectedDataTypes") === undefined) {
    fail(`${bundle.name}: NSPrivacyCollectedDataTypes missing.`);
  }

  // 4. Symbol → category coupling, asserted in both directions.
  const declared = new Set(
    [...(accessed ?? "").matchAll(/<string>(NSPrivacyAccessedAPICategory\w+)<\/string>/g)].map(
      (m) => m[1],
    ),
  );
  const found = new Map();
  for (const src of bundle.sources.flatMap((s) => swiftFiles(join(root, s)))) {
    const lines = readFileSync(src, "utf8").split("\n");
    lines.forEach((raw, idx) => {
      const line = stripNoise(raw);
      for (const [category, symbols] of Object.entries(CATEGORIES)) {
        for (const symbol of symbols) {
          if (line.includes(symbol)) {
            if (!found.has(category)) found.set(category, []);
            found.get(category).push(`${relative(root, src)}:${idx + 1}`);
          }
        }
      }
    });
  }

  for (const [category, sites] of found) {
    if (!declared.has(category)) {
      fail(
        `${bundle.name}: ${category} is used but not declared. First use: ${sites[0]}` +
          `${sites.length > 1 ? ` (+${sites.length - 1} more)` : ""}`,
      );
    }
  }
  for (const category of declared) {
    if (!found.has(category)) {
      fail(
        `${bundle.name}: ${category} is declared but no source uses it. ` +
          `Remove the declaration, or the manifest is describing code that no longer exists.`,
      );
    }
  }
}

// 6. project.yml wires both manifests, each under its own target.
const projectYml = readFileSync(join(root, "project.yml"), "utf8");
for (const bundle of BUNDLES) {
  if (!projectYml.includes(`- path: ${bundle.manifest}`)) {
    fail(`project.yml: no explicit '- path: ${bundle.manifest}' entry for ${bundle.name}.`);
  }
}

if (failures.length > 0) {
  console.error("verify-privacy-manifests: FAILED\n");
  for (const f of failures) console.error(`  • ${f}`);
  console.error(
    "\nWhat this cannot prove is what lands in the built bundle — that is the " +
      "archive's Privacy Report and the upload response.",
  );
  process.exit(1);
}
console.log(
  `verify-privacy-manifests: ok — ${BUNDLES.length} bundles, declarations match source usage.`,
);
