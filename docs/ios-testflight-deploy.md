# iOS → TestFlight internal testing: deploy runbook

The iOS twin of `docs/android-play-deploy.md`: **merge to `main` → your iPhone
auto-updates via TestFlight.**

Unlike Android, the pipeline does not run on GitHub Actions. It runs on **Xcode
Cloud**, and its workflow is configured in **App Store Connect**, not in this
repo. This page is the only record of that configuration, so change the two
together. (Why Xcode Cloud: the 2026-10-25 entry in `docs/decisions/log.md`.)

## How the automation works

| Trigger | Result |
|---|---|
| Push / merge to `main` touching `apps/ios/` | Archives, uploads to TestFlight, ships to **Private** (the internal group) |
| **Start Build** on any branch (App Store Connect or Xcode) | Publishes **that branch** to your phone. This replaces Android's `deploy-beta` label |

What the repo contributes (`apps/ios/ci_scripts/`):

- **`ci_post_clone.sh`** installs XcodeGen and writes `Config/Secrets.xcconfig`
  from the workflow's environment variables. It **fails the build** when one is
  missing, rather than shipping a build that crashes at launch. It also escapes
  `//` in the URL, which xcconfig would otherwise read as a comment. Then it
  generates the project and resolves the Swift packages.
- **`ci_post_xcodebuild.sh`** writes TestFlight's *What to Test* from the commit
  being built. `main` is squash-merged, so that text is the PR title, plus the
  branch and short SHA.
- **`ITSAppUsesNonExemptEncryption = NO`** in `Pebbles/Resources/Info.plist`
  answers export compliance, so builds don't sit in *Missing Compliance* until
  someone answers by hand. It holds because the app uses only HTTPS (URLSession)
  and a CryptoKit SHA-256 of the OAuth nonce, and both are exempt. If the app
  ever ships its own encryption, revisit this.

Xcode Cloud **sets the build number itself**, so `CFBundleVersion` in Info.plist
stays `1` and you never bump it. It runs **no tests**: `.github/workflows/ios.yml`
gates every PR (SwiftLint plus the Swift Testing suite) before anything reaches
`main`.

---

## Step 0 — The workflow

There is one workflow, **Default**, under **App Store Connect → Xcode Cloud →
Pebbles → Workflows**. Edit it in place: a second workflow on `main` would
upload every merge twice. The switch next to its name enables it. It was
created in April 2026 and left off until this pipeline went live, which is why
every build before 0.3.0 was uploaded by hand from Xcode. The switch is also
the kill switch: turn it off to stop publishing without losing the
configuration.

## Step 1 — Environment

| Setting | Value | Why |
|---|---|---|
| Xcode version | **26.3** | The same pin as `ios.yml`. Anything below 26 cannot compile the `.glassEffect` branch; anything else ships a binary the PR gate never built |
| macOS version | the newest that Xcode 26.3 accepts | |
| Clean | On | |
| `PBBLS_SUPABASE_URL` | the production Supabase URL, **Secret** | Plain `https://…` is fine; the script escapes it |
| `PBBLS_SUPABASE_ANON_KEY` | the anon/publishable key, **Secret** | |

The variable names must not start with `CI_` or `TEST_RUNNER_`, which Xcode
Cloud reserves.

## Step 2 — Start conditions

1. **Branch Changes**
   - Source branch: **Custom → `main`**.
   - Files and folders: **Custom → Start a build if any changed file matches →
     `apps/ios/`**. A web- or Android-only merge then spends no compute hours.
   - **Auto-cancel builds: on.** A newer merge supersedes the one still
     building, the same as `cancel-in-progress` on Android.
2. **Manual Start**: **Any branch.** This is the `deploy-beta` path. Start one
   from the workflow's *Start Build* button in App Store Connect, or in Xcode
   from **Report navigator → Cloud → (workflow) → Start Build** and pick the
   branch.

Remove any **Pull Request Changes** condition. It would archive every PR, and
`ios.yml` already covers PRs.

## Step 3 — Actions and post-actions

- **Actions**: a single **Archive**, platform iOS, scheme **`Pebbles`**,
  distribution preparation **App Store Connect**. Not *TestFlight (Internal
  Testing Only)*: a build prepared that way can never be offered to the external groups
  (Friends, Luni) or submitted for review. Every build still reaches only the
  internal group automatically (the post-action below). Offering one to an
  external group stays a manual choice in TestFlight, and the first build of
  each version waits for Beta App Review there. Delete any **Test** action; it
  would spend compute hours re-running what `ios.yml` already gated.
- **Post-actions**: **TestFlight Internal Testing → Private** (the internal
  group). Internal testers must be App Store Connect users on the team.

## Step 4 — Versions and build numbers

**The version** (`CFBundleShortVersionString`) is set in **both**
`Pebbles/Resources/Info.plist` and `PebblesWidget/Info.plist`, and the two must
match. Bump them together, by hand, in a PR: a version is a release decision,
not a CI side effect. Each version is its own TestFlight train, and the first
build of a train goes through Beta App Review before external groups can have
it.

**The build number** (`CFBundleVersion`) belongs to Xcode Cloud, which stamps
its own counter on every archive. The number only has to be unique within its
version. The counter starts at 1, so a version that already holds hand-uploaded
builds collides with it. Either start a new version, or set **Xcode Cloud →
Settings → Build Number → Next build number** above the highest build in that
version. 0.2.0 holds hand uploads 1–5 from Xcode, which is why Xcode Cloud's
first train is 0.3.0.

## Step 5 — The phone

1. Install **TestFlight** from the App Store and accept the internal invite.
2. Open **Pebbles** in TestFlight, then turn **Automatic Updates** on.

From then on, every merge to `main` that touches `apps/ios/` lands on the phone
by itself.

## Step 6 — Prove it end to end

Save the workflow, turn its switch on, then **Start Build** on `main`. Expect, in order:
post-clone → archive → *Prepare build for TestFlight* → the post-action → a
TestFlight push on the phone. Open the build in TestFlight: *What to Test*
should read the last commit's title.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Post-clone fails: "PBBLS_SUPABASE_URL and PBBLS_SUPABASE_ANON_KEY must both be set" | A variable is missing from the workflow's Environment, or was added to another workflow (step 1). |
| Compile errors about `glassEffect` or iOS 26 symbols | The workflow's Xcode is older than 26 (step 1). |
| "Unable to resolve package dependencies" | The packages are pinned with `exactVersion` in `project.yml`, and post-clone resolves them. Read the post-clone log: a network blip retries cleanly, but a version that no longer exists means a `project.yml` pin moved. |
| Upload rejected: the build number has already been used | Step 4. |
| The build is in TestFlight but stuck in *Missing Compliance* | `ITSAppUsesNonExemptEncryption` is missing from `Info.plist` in that commit. Answer by hand once: *None of the algorithms mentioned above*. |
| ITMS-91053 (missing API declaration) on upload | A new SDK or API needs a required-reason entry in a `PrivacyInfo.xcprivacy`. `npm run verify:privacy --workspace=@pbbls/ios` reproduces the check locally. |
| The build processed, but no TestFlight push on the phone | The post-action has no group (step 3), you are not in that group, or Automatic Updates is off (step 5). |
| Nothing builds after a merge | The merge didn't touch `apps/ios/` (by design), or the Branch Changes condition names another branch. |
| Compute hours run out | The Apple Developer Program includes 25 hours a month. The `apps/ios/` filter and auto-cancel in step 2 are what keep it within that. |

## Security notes

- No signing material lives anywhere in this repo or in GitHub. Xcode Cloud
  uses cloud-managed certificates and profiles for the app and the widget
  extension.
- `PBBLS_SUPABASE_ANON_KEY` is the publishable key; it already ships inside
  every client. Row-level security, not key secrecy, protects the data. Keep it
  marked **Secret** anyway so it stays out of build logs.
