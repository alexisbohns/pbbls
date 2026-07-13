# Android → Google Play internal testing: deploy runbook

This is the one-time setup that turns the manual
download/unzip/upload/verify/install loop into: **merge to `main` → your phone
auto-updates via the Play Store.**

## How the automation works (already in the repo)

`.github/workflows/android-release.yml` builds a **signed release AAB** and
publishes it to the **internal testing** track. Three ways to trigger it:

| Trigger | Result |
|---|---|
| Push / merge to `main` (touching `apps/android/**`) | Auto-publishes to internal testing |
| A PR labelled **`deploy-beta`** | Publishes **that branch** to your phone (opt-in) |
| Actions → *Android Release* → *Run workflow* | Publishes any branch, on demand |

Every run also uploads the AAB as a workflow artifact (`app-release-aab`).

**It's fail-soft:** until the secrets below exist, the job still runs — it just
builds an unsigned AAB and skips the publish step. So nothing goes red while you
work through this list. The debug-APK workflow (`android.yml`) is untouched.

## Do the steps in this order — they depend on each other

```
1. Generate upload keystore  →  2. Add signing secrets  →  3. First CI AAB
        →  4. Seed the FIRST release by hand  →  5. Create service account
        →  6. Done: everything is automatic
```

The reason for the hand-upload in step 4: the Play Publishing API **refuses to
create an app's first-ever release**. One manual upload seeds it; the API takes
over after that.

---

## Step 1 — Generate your upload keystore

You only need **Java** for this one command (`keytool` ships with any JDK/JRE —
it is *not* the Android SDK). If you don't have Java, a lightweight JDK install
is enough; nothing else here needs it.

```bash
keytool -genkeypair -v \
  -keystore upload-keystore.jks \
  -alias pbbls-upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

It prompts for a **keystore password**, a **key password** (you can reuse the
same one), and a name/organisation (press Enter to accept defaults — it doesn't
matter for an upload key).

> **This is your _upload_ key, not the app-signing key.** With Play App Signing
> (step 4) Google holds the real signing key; this upload key just proves the
> AAB came from you. If it's ever lost, Google can reset it — but still keep the
> `.jks` file and its passwords in your password manager, and **never commit
> them** (`.gitignore` already blocks `*.jks`).

## Step 2 — Add the signing secrets to GitHub

Base64-encode the keystore so it can live as a secret:

```bash
# macOS
base64 -i upload-keystore.jks | pbcopy
# Linux
base64 -w0 upload-keystore.jks
```

Then add these under **repo Settings → Secrets and variables → Actions → New
repository secret**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the base64 blob from above |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `pbbls-upload` |
| `KEY_PASSWORD` | the key password |

`SUPABASE_URL` and `SUPABASE_ANON_KEY` already exist from the debug-APK setup —
the release build reuses them.

## Step 3 — Produce the first signed AAB

Trigger the release workflow once — the simplest is **Actions → Android Release
→ Run workflow** on this branch (or merge to `main`). When it's green, open the
run and download the **`app-release-aab`** artifact. It's signed with your
upload key. (Publish is still skipped — no service account yet. Expected.)

## Step 4 — Seed the FIRST release by hand in Play Console

1. Play Console → your app (**`app.pbbls.android`**) → **Testing → Internal
   testing → Create new release**.
2. If asked about **Play App Signing**, accept the default (*Use a
   Google-generated key*). Upload the `app-release.aab` from step 3 — Google
   registers your upload certificate from it automatically.
3. Add a release name/notes → **Save → Review release → Roll out to internal
   testing**.
4. **Testers:** Internal testing → *Testers* tab → create an email list that
   includes your Google account → save. Copy the **“Copy link”** opt-in URL.
5. **On your phone:** open the opt-in link, tap *Become a tester*, then open the
   Play Store link and install. **From now on the Play Store auto-updates it** —
   that's the whole point.

> Internal testing is the least-gated track, so you usually don't need the full
> store listing / content rating / data-safety form to roll out. The Console may
> still prompt for a few app-level declarations before the first rollout — just
> follow its prompts.

## Step 5 — Create the Play service account (turns on automation)

This is the credential CI uses to publish.

1. Play Console → **Setup → API access**. If prompted, link/create a Google
   Cloud project.
2. Google Cloud Console → **IAM & Admin → Service Accounts → Create service
   account** (e.g. `github-play-publisher`). No project-level roles needed.
   Then **Keys → Add key → JSON** and download the file.
3. Back in Play Console → **Users and permissions → Invite new users** → enter
   the service account's email → grant **app access to `app.pbbls.android`**
   with permission to **Release to testing tracks** (Release manager is fine).
4. Add the **entire JSON file contents** as a repo secret named
   **`PLAY_SERVICE_ACCOUNT_JSON`**.

Give it a few minutes to propagate.

## Step 6 — You're done

- **Merge to `main`** → auto-publishes to internal testing → phone updates.
- **Label a PR `deploy-beta`** → that branch goes to your phone (remove the
  label or merge to `main` to revert). Create the label once: repo → *Issues* or
  *Pull requests* → *Labels* → *New label* → `deploy-beta`.
- **Actions → Android Release → Run workflow** → any branch, on demand.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Upload rejected: "not signed" / "unsigned" | A `KEYSTORE_*` secret is missing or mistyped — check the run's *Decode upload keystore* and *Assemble signed release AAB* steps ran. |
| "Version code N has already been used" | Don't **re-run** a release run (same `run_number` → same `versionCode`). Push a new commit instead — a fresh run gets a higher code. |
| First upload errors about "existing users can't upgrade" | The manual seed (step 4) hasn't happened yet — the API can't create the first release. |
| Publish step: "caller does not have permission" | Service account not granted app access in Play Console (step 5.3), or still propagating — wait a few minutes. |
| Nothing publishes, job green | `PLAY_SERVICE_ACCOUNT_JSON` not set yet — the publish step self-skips by design. |

## Security notes

- The **upload keystore** and its passwords: keep in a password manager, never
  commit (`*.jks` is git-ignored).
- The **service account JSON** can publish to your Play account — treat it like
  a password. It only ever lives as the `PLAY_SERVICE_ACCOUNT_JSON` GitHub
  secret; don't download it into the repo.
