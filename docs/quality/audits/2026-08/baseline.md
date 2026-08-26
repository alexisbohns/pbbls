# Machine baseline — audit 2026-08

Snapshot commit: `10181916ba9f56789e62c6351bb380682e5d90da` (origin/main at audit time). Environment: Linux CI-like container, Node v22.22.2, npm ci from lockfile. No Android SDK, no Xcode/SwiftLint (mobile toolchains unavailable here; their CI status is noted below).

| Check | Command | Result |
| --- | --- | --- |
| web lint | `npm run lint --workspace=apps/web` (eslint) | ✅ clean |
| web tests | `npm run test --workspace=apps/web` (vitest) | ✅ 12 files / 125 tests pass, 1.15s |
| admin lint | `npm run lint --workspace=apps/admin` (eslint) | ✅ clean |
| supabase typecheck | `npm run build --workspace=packages/supabase` (tsc --noEmit) | ✅ clean |
| supabase lint | `npm run lint --workspace=packages/supabase` | ⚠️ placeholder script ("no lint step yet") — exits 0 without checking anything |
| android ktlint/tests | `scripts/gradle-if-sdk.sh` | ⏭️ no SDK in this environment; script no-ops **with exit 0**. Runs for real in `android.yml` CI on `apps/android/**` changes only |
| ios swiftlint/tests | `swiftlint` / xcodebuild | ⏭️ toolchain absent here (exit 127); iOS has no GitHub Actions workflow — lint/tests run locally/Xcode Cloud only |
| admin tests | — | ❌ no test script exists in `apps/admin` |
| root | `npm run test` | ❌ no root test task (web only, per root CLAUDE.md) |

Auditor guidance: treat ✅ rows as verified-green at the snapshot; treat the ⏭️/⚠️/❌ rows as *audit evidence in themselves* (verification gaps are TST/AGT findings territory). Do not re-run installs or suites; cheap read-only commands only.
