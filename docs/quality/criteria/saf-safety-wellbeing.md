# SAF — Safety & Wellbeing

> Generated from [`library/framework.json`](../library/framework.json) v0.1.0 — do not edit by hand.

Harm to humans: crisis pathways, emotionally safe design, UGC moderation enforcement, social abuse vectors, bystander harm paths, minors safety.

---

## SAF-01 · Crisis and self-harm response pathways

**When a user records an extreme negative emotional state, does every client respond non-harmfully and put localized crisis resources within one interaction, with reviewed guardrails on any generated reflective content?**

`crisis-pathways` · applies to: `web` `ios` `android` `supabase` · default impact **5/5** · weight **3/3**

A product that invites users to record distress must never answer distress with celebration, gamification, or judgment, and must make localized crisis resources (helplines, support links per shipped locale) reachable within one interaction of the moment of recording. Detection or intervention algorithms are optional; a non-harmful response and reachable resources are not. Any server- or model-generated reflective content has explicitly reviewed branches for negative states (no advice, no diagnosis, no minimizing) and a static safe fallback when generation fails.

*Why it matters:* An emotion-journaling product is disproportionately likely to be open at the exact moment a user is in crisis; a tone-deaf reward animation or an unguarded generated response at that moment is the highest-severity UX failure this product class can have.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No crisis resources exist anywhere; recording an extreme negative state triggers the same celebratory or gamified response as any other entry, and generated content has no negative-state branches. |
| **1 · Ad-hoc** | A helpline link exists on one static page, in one client or one locale only; the recording flow never surfaces it and generated reflective content is unguarded. |
| **2 · Defined** | Every client ships a localized resources screen and the negative-state completion path deliberately suppresses celebration, but reachability from the recording moment is inconsistent across clients and generated-content branches are unreviewed. |
| **3 · Managed** | All clients surface resources within one interaction of a high-distress entry, negative-branch copy is reviewed in every locale, generated content has tested guardrails plus a static fallback, and the resource list is locale-correct. |
| **4 · Verified** | CI enforces the pathway: localization completeness checks cover crisis-resource keys in all locales, UI or snapshot tests pin the post-negative-entry screen on each client, and a test suite exercises generated-content guardrail branches so regressions fail the build. |

### Audit checklist

- [ ] Grep all clients for crisis resource strings: `grep -ril 'crisis\|helpline\|suicide' apps/web apps/ios apps/android` and confirm at least one localized resources surface per client; zero hits on a client is an automatic finding.
- [ ] Locate the entry-completion handler for the emotion/valence recording flow in each client (web submit handler, SwiftUI completion view, Compose completion screen) and trace what renders after a maximum-negative, maximum-intensity entry: verify no confetti/reward/streak prompt fires and a support affordance is reachable within one tap or click.
- [ ] Open every server-side generation path (edge functions and prompt or template files under the database package's functions directory) and read the branches that consume valence/intensity/emotion inputs: verify negative-state outputs never advise, diagnose, or minimize, and that a static fallback string exists for generation failure.
- [ ] Check localization files for the crisis-resource keys in every shipped locale and verify the helpline numbers/URLs differ correctly per locale (a single hardcoded number for all countries is a finding).
- [ ] Verify the resources screen degrades gracefully offline (PWA precache manifest includes it; native clients bundle it statically) since crisis moments do not wait for connectivity.

### Monitoring signals

- grep -ril 'crisis|helpline' per client returns at least one localized resource file for web, ios, and android
- the localization completeness CI check includes the crisis-resource string keys for every shipped locale
- a UI/snapshot test asserting the max-negative-entry completion screen renders the support affordance exists and runs in CI on each client

### References

- [Apple App Review Guidelines — Guideline 1.4 (Physical Harm)](https://developer.apple.com/app-store/review/guidelines/)
- [Google Play Developer Program Policies, Health Content and Services — Health Content and Services policy](https://play.google/developer-content-policy/)

### Typical remediation

Add a localized crisis-resources screen to each client, wire it into the negative-state completion path within one interaction, review and de-gamify negative-branch copy, and add guardrail branches plus a static fallback to every generated-content path. Pin all of it with snapshot and localization tests.

*Issue skeleton:* [`templates/saf-01.md`](../templates/saf-01.md)

---

## SAF-02 · Emotionally safe engagement mechanics

**Are all engagement mechanics (streaks, rewards, reminders, notifications) decoupled from emotional content and free of guilt framing, with a penalty-free exit from every recording flow?**

`emotional-design` · applies to: `web` `ios` `android` `supabase` · default impact **3/5** · weight **2/3**

Gamification and retention mechanics never key on the emotional substance of what users record: no reward, streak, or achievement is conditioned on valence, intensity, emotion choice, or disclosure volume of sensitive fields. Reminder and notification copy carries no guilt or loss framing in any locale. Every recording flow has a first-class skip or cancel with zero penalty, and inactivity never costs earned assets. This is dark-pattern avoidance specialized to products that monetize or reward emotional disclosure.

*Why it matters:* Engagement traps around negative emotions convert a reflective tool into a compulsion loop over the user's worst moments; EU regulators treat manipulative interface design as an enforcement target, not a style issue.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Streaks, guilt notifications, or rewards key directly on emotional content or disclosure volume, and skipping or inactivity is penalized. |
| **1 · Ad-hoc** | Some mechanics happen not to read emotion fields, but no boundary is stated anywhere, and at least one notification or reward pressures continued disclosure or uses loss framing. |
| **2 · Defined** | A written rule decouples rewards and reminders from emotional content and most mechanics comply, but legacy strings or trigger definitions still guilt, pressure, or read valence. |
| **3 · Managed** | All engagement mechanics are inventoried; none reference valence/intensity/emotion fields; notification copy is reviewed in every locale; every flow has a penalty-free exit and inactivity costs nothing. |
| **4 · Verified** | Enforcement is automated: tests or schema checks fail if a reward/achievement trigger references emotion columns, and a string lint blocks loss-framed notification copy in any locale. |

### Audit checklist

- [ ] Inventory engagement mechanics: `grep -rin 'streak\|badge\|karma\|achievement\|reminder' apps packages` and list every trigger; classify each by whether its condition reads an emotional field (valence/positiveness, intensity, emotion id) or disclosure volume.
- [ ] Read the achievement/karma trigger definitions in the database migrations (grep migrations for the achievement grant functions and karma-event inserts) and confirm no grant condition references valence, intensity, or emotion columns.
- [ ] Grep localization files in every locale for loss- and guilt-framed notification strings (patterns like 'don't lose', 'you haven't', "tu n'as pas") and read all scheduled-notification templates on each client.
- [ ] Trace the recording flow on each client and verify a visible cancel/skip exists at every step with no penalty copy, no earned-asset decrement, and no re-prompt nag on exit.
- [ ] Grep wallet/currency RPCs in migrations for any decrement tied to inactivity or non-use; earned assets must only decrease by explicit user spend.

### Monitoring signals

- grep of achievement/karma trigger bodies in migrations for valence/positiveness/emotion column names returns nothing
- a copy-lint list of loss-framing phrases runs over notification templates in all locales in CI and passes
- no scheduled-notification code path conditions its firing on the valence of recent entries (grep notification scheduling for valence fields returns nothing)

### References

- EDPB Guidelines 03/2022 on deceptive design patterns in social media platform interfaces — Guidelines 03/2022, Version 2.0
- [Regulation (EU) 2022/2065 (Digital Services Act) — Art. 25 (Online interface design and organisation)](https://eur-lex.europa.eu/eli/reg/2022/2065/oj)
- FTC Staff Report: Bringing Dark Patterns to Light — 2022 staff report

### Typical remediation

Rewrite reward and reminder triggers to key on neutral activity only, purge loss-framed strings from all locales, add first-class skip paths, and encode the boundary as a test that fails when a trigger references emotion columns.

*Issue skeleton:* [`templates/saf-02.md`](../templates/saf-02.md)

---

## SAF-03 · UGC moderation state machine and takedown

**Does every user-authored content type visible to other users or the public carry a server-enforced moderation state, operator review tooling with an audit trail, and takedown that propagates to all serving paths including storage?**

`ugc-moderation` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **2/3**

Every content type one user authors and another can see (custom visual assets, display names, handles, profile fields, shared free text, photos) has a moderation state machine in the schema, and unapproved or rejected content is excluded from cross-user and anonymous serving paths at the RLS/RPC layer, not by client filtering. Operators have review tooling whose actions are authorization-checked server-side and audit-logged, and takedown removes the content from all serving paths including object storage and caches. Presence of the in-app report controls and the other store-required pillars is PLT-04's concern; this criterion audits enforcement depth once content is reported or reviewed.

*Why it matters:* Both app stores condition UGC apps on moderation that actually works, and the DSA requires a notice-and-action mechanism from hosting services of any size; a report wired to nothing, a takedown that leaves the storage object live, or an operator action without authorization checks turns the visible apparatus into an illusion. Presence defects score under PLT-04; depth defects score here, so one defect hits exactly one criterion.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | User-authored content is served to other users or anonymously with no moderation state and no review tooling; a takedown would be manual SQL. |
| **1 · Ad-hoc** | One content type has an ad-hoc flag or is cleaned up by manual SQL; other types are unmoderated. |
| **2 · Defined** | The main cross-user content types have a status column and an operator queue exists, but some types are uncovered, or takedown does not reach storage or caches. |
| **3 · Managed** | Every cross-user content type is status-gated in RLS/definer RPCs, operator actions are authorization-checked and audit-logged, and takedown propagates through storage and caches. |
| **4 · Verified** | A runnable harness proves unapproved and rejected content is unreachable on cross-user and anonymous paths, a schema check requires moderation columns on any new cross-user content table, and report handling latency is monitored. |

### Audit checklist

- [ ] Enumerate UGC: read the generated database types and migrations for user-authored free text, image, and visual-asset columns; for each, determine whether another user or an anonymous visitor can ever see it (follow the RLS policies and definer RPC projections that expose it).
- [ ] For each cross-user-visible type, verify a status/review column exists and that the public or cross-user read path filters on it inside the policy or RPC body, not in client code; grep migrations for the serving RPCs and read their WHERE clauses.
- [ ] Open the admin surface's moderation routes and confirm approve/reject actions call security definer RPCs with an operator check (grep migrations for `security definer` plus the admin-role predicate); raw table updates from the admin client are a finding.
- [ ] Trace a rejection end to end: confirm the reject RPC also removes or blocks the storage object (bucket delete or signed-URL invalidation) and that CDN/cache headers do not keep serving it.
- [ ] Report-control presence on each client is audited under PLT-04; here, trace what a submitted report becomes: a row in the moderation queue an operator can act on, with the action audit-logged.
- [ ] Check handle and display-name hardening: reserved-word table, charset/length constraints, and an impersonation guard in the claiming RPC.

### Monitoring signals

- a schema check script asserts every cross-user content table has a moderation status column
- grep of anon/cross-user serving policies and RPCs shows a status filter in every body
- a DB harness inserts unapproved and rejected content and asserts both are absent from every cross-user and anonymous read path

### References

- [Apple App Review Guidelines — Guideline 1.2 (User-Generated Content)](https://developer.apple.com/app-store/review/guidelines/)
- [Google Play Developer Program Policies, User Generated Content — User Generated Content policy](https://play.google/developer-content-policy/)
- [Regulation (EU) 2022/2065 (Digital Services Act) — Art. 16 (Notice and action mechanisms)](https://eur-lex.europa.eu/eli/reg/2022/2065/oj)

### Typical remediation

Add status columns and status-gated serving to uncovered content types, move operator actions behind admin-checked definer RPCs with an audit table, and wire storage deletion into rejection. Then encode the reachability rule as a DB harness. Missing report controls are filed under PLT-04.

*Issue skeleton:* [`templates/saf-03.md`](../templates/saf-03.md)

---

## SAF-04 · Block integrity and anti-harassment enforcement

**Does a server-enforced, silent block primitive sever every read and contact path between two users, including pre-existing shares and invite flows, with enumeration- and spam-resistant invites, verified by a contract harness?**

`social-abuse` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **3/3**

Users can block each other, and the block is enforced in RLS and RPC logic on every path a relationship can leak through: shared content (including content shared before the block), profile projections, invites, attribution in achievements or activity, and any presence signal. The block is silent: the blocked party receives shapes indistinguishable from absence, never an oracle. Invite and connection flows resist enumeration (identical outward shapes for revoked and nonexistent tokens), spam (rate limits or expiry), and forced re-contact after decline or block. Presence of the user-facing block control on each surface is PLT-04's concern; this criterion audits that a block, once placed, severs everything.

*Why it matters:* In an intimate-journal product a harassment vector is worse than elsewhere: the abuser may be one of the named people in the victim's entries, so a leaky block or a block oracle directly endangers users leaving abusive relationships.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No block primitive exists; any user can maintain visibility of and contact with another indefinitely. |
| **1 · Ad-hoc** | A block exists but only hides content in one client's UI; server reads still return the data, or the blocked user can detect the block from distinguishable responses. |
| **2 · Defined** | Blocks are enforced server-side on the main paths, but at least one path leaks across a block (stale shares, invites, achievements attribution, presence), or invite spam and enumeration are unmitigated. |
| **3 · Managed** | Blocks sever every server path in the required directions, are silent to the blocked party, invites have expiry, spam resistance, and identical error shapes, and these behaviors are covered by tests. |
| **4 · Verified** | A contract harness runs the full block matrix against the real database (every read path, both directions, shares predating the block, invite states) and any leak fails CI. |

### Audit checklist

- [ ] Read the block data model and RLS policies in the migrations, then enumerate every cross-user read path (shared-content RPCs, profile projection RPCs, invite redemption, achievement/activity attribution) and check each body for a block predicate; client-side filtering does not count.
- [ ] Verify silence: compare the outward shapes the connection and profile RPCs return for blocked vs nonexistent vs revoked states; any distinguishable field, error code, or timing knob is a block oracle.
- [ ] Check that content shared before a block stops being readable after it: trace the shared-content read path and confirm the block check happens at read time, not only at share time.
- [ ] Probe invite abuse: confirm invites expire, creation is bounded per user, redemption by a blocked user is refused with the generic shape, and declined or blocked users cannot be re-invited into a visible prompt loop.
- [ ] Presence of the block control on each client is audited under PLT-04; here, confirm that a block placed from any surface produces the same server-side state and severs the same paths.
- [ ] Check the operator side: blocks placed via moderation or escalation flow through the same server-side primitive, so operator and user blocks are equally leak-proof.

### Monitoring signals

- a DB contract harness exercises the block matrix (all read paths, both directions, pre-block shares, invite states) and runs in CI
- grep of cross-user read RPC bodies shows a block predicate in every one
- a contract test asserts byte-identical outward shapes for revoked vs nonexistent invite tokens

### References

- [Apple App Review Guidelines — Guideline 1.2 (User-Generated Content: ability to block abusive users)](https://developer.apple.com/app-store/review/guidelines/)
- [Google Play Developer Program Policies, User Generated Content — User Generated Content policy (user blocking and reporting)](https://play.google/developer-content-policy/)
- [eSafety Commissioner, Safety by Design — Safety by Design principles](https://www.esafety.gov.au/industry/safety-by-design)

### Typical remediation

Centralize the block predicate (helper function) and apply it in every cross-user RPC and policy, normalize outward shapes to remove oracles, add expiry and rate bounds to invites, then freeze the behavior in a block-matrix harness run against the real database.

*Issue skeleton:* [`templates/saf-04.md`](../templates/saf-04.md)

---

## SAF-05 · Bystander exposure on outbound paths

**Are identifiers of recorded third parties stripped or generalized on every outbound path (shares, public pages, exports, media metadata, operator analytics), with a documented channel for a named third party to request removal?**

`bystander-privacy` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **3/3**

Wherever records naming real third parties who never consented can leave the owner's private scope, the outbound path strips or generalizes identifiers: shared and public projections use explicit field allowlists that exclude third-party identifiers, uploaded media is stripped of location metadata, exports are scoped to the requesting owner, and operator analytics aggregate counts rather than enumerate names. The product documents how a named third party can request removal, and sharing flows warn the owner before third-party labels leave private scope. Publication to an audience removes the GDPR household exemption, so these paths carry controller obligations. Minimization at capture, confinement to the creator, and destruction at account purge are GDP-03's concern; this criterion owns the outbound paths and the bystander's recourse.

*Why it matters:* The people written about are data subjects who never agreed to anything; the CJEU settled in Lindqvist that publishing information about identifiable others is outside the household exemption, and it is precisely the outbound paths (shares, public pages, exports, media, operator tooling) where a privately held mention becomes a publication with controller obligations attached.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Third-party names flow into shared, public, exported, or operator surfaces unfiltered; no outbound path strips anything and no removal channel exists. |
| **1 · Ad-hoc** | Some outbound surfaces happen not to show third-party names, but projections are implicit (select-star or whole-row serialization) and nothing documents the boundary. |
| **2 · Defined** | A stated rule keeps third-party identifiers out of shared and public projections via explicit allowlists, but gaps remain in media metadata, exports, or operator analytics, and the removal channel is undocumented. |
| **3 · Managed** | Every outbound path is traced and strips or generalizes third-party identifiers, media metadata is sanitized on upload, sharing flows warn the owner, and a removal-request channel is documented in the privacy policy. |
| **4 · Verified** | The outbound invariants are enforced: tests assert no third-party identifier appears in any cross-user or public projection, a grep-able ban on whole-row serialization of person tables runs in CI, and the removal channel is backed by a documented operator procedure. |

### Audit checklist

- [ ] Trace each outbound path (shared-entry RPCs, public-profile projections, data export, feeds) and read the projection bodies: confirm explicit field allowlists, and grep migrations and functions for whole-row serialization of person tables (`to_jsonb(` on the table alias, `row_to_json`, `select *`) which must return nothing on those tables.
- [ ] Inspect the media upload pipeline on every client and any server-side processing for metadata stripping (EXIF/GPS removal or forced re-encode); shared or public photos keeping GPS coordinates is a finding.
- [ ] Grep the admin surface's queries for third-party name columns; operator analytics must aggregate counts, not enumerate names.
- [ ] Check the privacy policy or help surface for a documented channel by which a named third party can request removal, and confirm sharing flows warn the owner before third-party labels leave private scope.
- [ ] Capture-time minimization, creator confinement, and purge coverage of bystander records are audited under GDP-03; cross-file any containment defect discovered while tracing outbound paths rather than scoring it here.

### Monitoring signals

- grep for whole-row serialization (to_jsonb, row_to_json, select *) on person/contact tables in migrations and functions returns nothing
- a projection test asserts third-party identifiers are absent from every cross-user and anonymous RPC response shape
- the privacy policy names a removal-request channel for recorded third parties

### References

- [Regulation (EU) 2016/679 (GDPR) — Art. 5(1)(c) (data minimisation) and Art. 14 (information where data are not obtained from the data subject)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- CJEU, Bodil Lindqvist — Case C-101/01 (household exemption does not cover publication to an indefinite audience)
- [Regulation (EU) 2016/679 (GDPR) — Art. 17 (right to erasure)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)

### Typical remediation

Introduce explicit allowlist projections on every outbound path, strip media metadata at upload, aggregate operator analytics, add owner-facing warnings to sharing flows, and publish a removal-request channel. Encode the no-whole-row-serialization rule as a CI grep; containment defects (capture, confinement, purge) are filed under GDP-03.

*Issue skeleton:* [`templates/saf-05.md`](../templates/saf-05.md)

---

## SAF-06 · Age gating and minors protection posture

**Is a minimum age enforced at signup consistent with GDPR Art. 8 for the served member states and with the store age declarations, and if minors are admitted, do they get private-by-default social settings and no profiling-based targeting?**

`minors-safety` · applies to: `web` `ios` `android` `supabase` · default impact **5/5** · weight **3/3**

The product declares a minimum age, enforces it at account creation (not only in terms text), and records the consent basis. The enforced age is consistent with GDPR Art. 8 as implemented in the served member states (member-state laws set 13 to 16; France sets 15) and with the App Store age rating and Play target-audience declaration. If users below adulthood are admitted, their social features default to private, discoverability is off, and no profiling-based advertising targets them.

*Why it matters:* An intimate emotional journal with social features misdeclared as all-ages is simultaneously a child-safety failure, a GDPR Art. 8 consent defect, and a store-policy violation; each alone can remove the product from distribution.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No age gate and no declared minimum age; store listings carry an all-ages rating over intimate content. |
| **1 · Ad-hoc** | Terms text states a minimum age but nothing enforces or records it, and store declarations are inconsistent with the product's content. |
| **2 · Defined** | Signup collects a birthdate or over-threshold attestation and refuses under-threshold users, but member-state consent ages are unmapped or admitted minors get adult defaults. |
| **3 · Managed** | Age handling matches GDPR Art. 8 for every served member state, store declarations match the enforced minimum, consent evidence is stored, and admitted minors get private defaults with documented protections. |
| **4 · Verified** | The age gate and the minors default matrix are covered by automated tests, store metadata (age rating, target-audience declaration) is checked into the repo and diffed in CI, and drift between declared and enforced age fails a check. |

### Audit checklist

- [ ] Grep the signup and onboarding flows on every client for an age input or attestation (`grep -rin 'birth\|age\|dob' ` over the auth/onboarding components); absence on any client while others enforce it is a bypass.
- [ ] Read the auth trigger and consent-capture migrations to confirm where the age or attestation and the consent record are stored server-side; a purely client-side gate that never reaches the database is Ad-hoc at best.
- [ ] Compare the enforced minimum against the served member states' Art. 8 ages (France 15) and against the terms-of-service text; mismatches between enforcement, terms, and jurisdiction are findings.
- [ ] Open the store metadata: the iOS project's age rating configuration and the Play Console target-audience declaration (or their in-repo representations) and verify consistency with the enforced minimum; an all-ages rating over emotional-journal-plus-social content is a misdeclaration.
- [ ] If under-adult users are admitted, verify their defaults in schema column defaults and onboarding code: public profile off, sharing narrowest grade, discoverability off, and no profiling-based ad targeting anywhere.

### Monitoring signals

- an automated test exercises the signup age gate (rejects under-threshold, records consent evidence) and runs in CI
- store age-rating and target-audience metadata are committed to the repo and a CI check diffs them against the documented minimum age
- grep of onboarding code paths confirms minors (where admitted) receive private-by-default social settings

### References

- [Regulation (EU) 2016/679 (GDPR) — Art. 8 (conditions applicable to child's consent)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [Regulation (EU) 2022/2065 (Digital Services Act) — Art. 28 (online protection of minors)](https://eur-lex.europa.eu/eli/reg/2022/2065/oj)
- [Apple App Review Guidelines — Guideline 5.1.4 (Kids)](https://developer.apple.com/app-store/review/guidelines/)
- [Google Play Developer Program Policies, Target Audience and Content — Target Audience and Content policy](https://play.google/developer-content-policy/)

### Typical remediation

Add a server-recorded age gate at signup, align the minimum with the served member states' Art. 8 ages and with both stores' declarations, and if minors are admitted, ship a private-by-default settings matrix. Commit store metadata to the repo and gate drift in CI.

*Issue skeleton:* [`templates/saf-06.md`](../templates/saf-06.md)

---

## SAF-07 · Account takeover harm ceiling

**Is the damage a stolen session or credential can do bounded: recent re-authentication on destructive and exposure-widening actions, global session revocation on credential change, security notifications, export friction, and MFA-protected audited operator access?**

`takeover-ceiling` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **2/3**

A compromised session or password must hit a ceiling before it can destroy or expose the account's intimate contents. Destructive actions (account purge) and exposure-widening actions (making a profile public, widening share visibility, changing email, bulk export) require recent re-authentication or a server-verified confirmation step, not just a valid cookie. Credential changes revoke all sessions and notify the user. Bulk data egress has friction (pagination limits, rate limits, audit). Operator accounts require MFA, are authorization-checked server-side on every action, and are audit-logged, since one operator takeover exposes every user.

*Why it matters:* For a product holding emotional histories, photos, and named third parties, takeover harm is not fraud but irreversible intimate exposure; bounding what a hot session can do matters more than preventing every phish.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | A stolen session can silently export everything, widen visibility, change the email, and purge the account, with no re-authentication, notification, or revocation anywhere. |
| **1 · Ad-hoc** | Some destructive action has a confirmation dialog, but it is client-side only; sessions never expire or revoke, and no security notifications exist. |
| **2 · Defined** | Destructive and exposure-widening actions are identified and most gate on a server-checked confirmation or recent auth, but session revocation on credential change, notifications, or export friction are missing, or operator MFA is unenforced. |
| **3 · Managed** | Every high-harm action gates on recent re-authentication server-side, credential changes rotate and globally revoke sessions with notification, bulk egress is paginated and audited, and operator access requires MFA with audit logs. |
| **4 · Verified** | The ceiling is tested: a harness attempts each high-harm action with a stale or unelevated session and CI fails if any succeeds; operator audit logs exist and are monitored for anomalies. |

### Audit checklist

- [ ] Enumerate high-harm actions (account purge, bulk export, public-profile flip, visibility widening, email/password change) and trace each from client to RPC: verify the server demands recent re-authentication or a server-verified confirmation, not merely an authenticated session; grep migrations for the purge and visibility RPCs and read their preconditions.
- [ ] Check session lifecycle in the auth configuration (Supabase config: refresh-token rotation, JWT expiry) and grep clients for the sign-out scope used; verify password change revokes other sessions and that a global sign-out is reachable in settings.
- [ ] Verify security notifications: email templates for password change, email change, and new sign-in are enabled in the auth configuration; absence means silent takeover.
- [ ] Assess bulk egress friction: grep client data-fetching for unbounded select-all patterns, confirm PostgREST max-rows or RPC pagination bounds, and check whether export actions are rate-limited or at least logged.
- [ ] Audit the operator surface: MFA required for operator accounts, the operator-role check runs server-side on every action (in RLS or RPC bodies, not client route guards alone), no service-role key ships to any client bundle (grep all client build outputs and env usage for service_role), and operator actions write an audit row.
- [ ] Probe the recovery path: reset links expire, reset responses do not reveal account existence, and a recovered account triggers the same revocation and notification as a password change.

### Monitoring signals

- grep for service_role across client workspaces and bundles returns nothing
- a harness attempts purge, export, and visibility-widening with an unelevated session and CI fails on any success
- auth configuration under version control shows refresh-token rotation, bounded JWT expiry, and enabled security-notification templates

### References

- [OWASP Application Security Verification Standard 4.0 — V3 (Session Management) and V2 (Authentication)](https://github.com/OWASP/ASVS)
- [NIST SP 800-63B, Digital Identity Guidelines: Authentication and Lifecycle Management — Section 7 (Session Management)](https://pages.nist.gov/800-63-3/sp800-63b.html)
- [CWE-613: Insufficient Session Expiration — CWE-613](https://cwe.mitre.org/data/definitions/613.html)

### Typical remediation

Add recent-auth preconditions to high-harm RPCs, enable rotation, global revocation, and security-notification templates in the auth config, bound and audit bulk egress, and enforce operator MFA with server-side role checks plus an audit table. Freeze the ceiling with a stale-session harness.

*Issue skeleton:* [`templates/saf-07.md`](../templates/saf-07.md)
