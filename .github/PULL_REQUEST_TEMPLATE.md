Resolves #

## Changes
<!-- List key files and what changed -->

## Notes
<!-- Implementation decisions, trade-offs, things to watch -->

## Lab Note (EN/FR)
<!--
  REQUIRED for any change a user would notice. One block, two destinations:
    - On merge, `lab-note.yml` posts it to the Ariko changelog vault automatically.
    - At release time a human copies the same YAML into the Pebbles Lab admin
      (click "New log" → the form prefills from the clipboard).
  Never write to Supabase / `logs` from the dev loop.

  Include this section ONLY if the PR is user-facing:
    - has the `feat` label, OR
    - touches a user-visible Arkaik view node (see `docs/arkaik/bundle.json`).
  Otherwise DELETE this entire section. (If the advisory `lab-note-reminder` still
  comments, add the `no-lab-note` label to silence it.)

  Author it with the `lab-note` skill (.claude/skills/lab-note/) — it defines the schema,
  the allowed values, and the friendly casual tone (French uses "Tu"). PR-time defaults:
  status: in_progress, published: false, and omit release-date (the maintainer sets those
  at release). Both languages are mandatory; no em dashes in either.
  Keep the quotes around every title and summary: a colon in a sentence breaks an
  unquoted YAML value, and that is the most common malformed note.
-->

```yaml
species: feature          # announcement | feature
platform: ios             # all | webapp | ios | android | project | infra
status: in_progress       # backlog | planned | in_progress | shipped
published: false
en:
  title: ""
  summary: ""
fr:
  title: ""
  summary: ""
suggested:                # optional; read only by the Ariko vault
  molecule: pbbls
  type: feature           # feature | improvement | fix | announcement
  tags: [changelog]
  # atom: <slug>          # ONLY when you know the slug exists — never guess
```

## Checklist
- [ ] Branch name follows `type/issueNumber-description`
- [ ] PR title uses conventional commits: `type(scope): description`
- [ ] Labels applied (species + scope)
- [ ] Milestone assigned
- [ ] `npm run build` passes
- [ ] `npm run lint` passes
- [ ] Tests pass for the touched workspace (e.g. `npm run test --workspace=apps/web`)
