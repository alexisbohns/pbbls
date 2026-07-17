Resolves #

## Changes
<!-- List key files and what changed -->

## Notes
<!-- Implementation decisions, trade-offs, things to watch -->

## Lab Note (EN/FR)
<!--
  DRAFT ONLY — a proposal, not published from this PR. At release time a human copies the
  YAML below and pastes it into the Lab admin (click "New log" → the form prefills from the
  clipboard). Never write to Supabase / `logs` from the dev loop.

  Include this section ONLY if the PR is user-facing:
    - has the `feat` label, OR
    - touches a user-visible Arkaik view node (see `docs/arkaik/bundle.json`).
  Otherwise DELETE this entire section.

  Author it with the `lab-note` skill (.claude/skills/lab-note/) — it defines the schema,
  the allowed values, and the friendly casual tone (French uses "Tu"). PR-time defaults:
  status: in_progress, published: false, and omit release-date (the maintainer sets those
  at release).
-->

```yaml
species: feature          # announcement | feature
platform: ios             # all | webapp | ios | android | project | infra
status: in_progress       # backlog | planned | in_progress | shipped
published: false
en:
  title:
  summary:
fr:
  title:
  summary:
```

## Checklist
- [ ] Branch name follows `type/issueNumber-description`
- [ ] PR title uses conventional commits: `type(scope): description`
- [ ] Labels applied (species + scope)
- [ ] Milestone assigned
- [ ] `npm run build` passes
- [ ] `npm run lint` passes
