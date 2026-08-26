<!-- Kritik issue skeleton for ARC-06 (Code Quality & Architecture) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, ui + the surface label + milestone. -->

Title: [Quality] Idiom drift on {surface}: {legacy_pattern} in {screen_or_module}

Criterion: {criterion_id} {criterion_name}
Surface: {surface}

**Observed level:** {observed_level} ({level_label})
**Target level:** {target_level}

## Evidence
{legacy_pattern_locations}

## Risk
{impact_x_likelihood_rationale}

## Remediation
- [ ] Migrate {module} to {target_idiom}
- [ ] Comment remaining deliberate exceptions
- [ ] Enable lint rule {rule} in CI

## Acceptance criteria
- One idiom era in the touched screens
- Lint enforces the idiom going forward
- No behavior regression (tests/screenshots green)

---
_Criterion: **ARC-06 · Platform idiom adherence** (`idioms`) — see [criteria reference](../criteria/index.md)._
_Question: Does each client use its framework's current idioms (App Router server/client split, modern SwiftUI observation and concurrency, Compose state hoisting and unidirectional data flow) rather than legacy or foreign patterns?_
_References: [Next.js Documentation: App Router — Server and Client Components](https://nextjs.org/docs/app) · [Apple Developer Documentation: Migrating from the Observable Object protocol to the Observable macro — Observation migration guide](https://developer.apple.com/documentation/swiftui/migrating-from-the-observable-object-protocol-to-the-observable-macro) · [Android Developers: State and Jetpack Compose — State hoisting](https://developer.android.com/develop/ui/compose/state)_