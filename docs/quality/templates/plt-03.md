<!-- Kritik issue skeleton for PLT-03 (Platform & Store Compliance) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, auth + the surface label + milestone. -->

Title: [Quality] PLT-03 sign-in option gap on {surface}: {gap_summary}

## Criterion
PLT-03 Sign-in options meet platform equity rules (store-accounts)

## Observed level
{observed_level}/4 on `{surface}` (target: {target_level}/4)

## Evidence
{evidence_bullets}

## Risk
Impact {impact}/5 x Likelihood {likelihood}/5 = {severity}. Apple 4.8 rejection blocks releases; provider asymmetry strands accounts on one surface.

## Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}

## Acceptance criteria
- {acceptance_criteria}

---
_Criterion: **PLT-03 · Sign-in options meet platform equity rules** (`store-accounts`) — see [criteria reference](../criteria/index.md)._
_Question: If any client offers a third-party or social login, is a privacy-preserving option (per Apple 4.8) offered with equal prominence, implemented with correct nonce/PKCE handling, and is the identity-provider set consistent across all client surfaces?_
_References: [Apple App Store Review Guidelines — 4.8 Login Services](https://developer.apple.com/app-store/review/guidelines/) · [Sign in with Apple (Apple Developer)](https://developer.apple.com/sign-in-with-apple/) · [Supabase Auth: Login with Apple](https://supabase.com/docs/guides/auth/social-login/auth-apple)_