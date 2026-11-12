# Report a person and a glyph on iOS — design

Issue #838 (iOS report affordance), with #1008 (`report_content` cannot target a connected peer or an inviter) inside it. Milestone M68 · V1 launch. Approved in conversation on 2026-11-12.

## Why

App Store Guideline 1.2 asks an app with user-generated content for four things: a filter, a way to report, a way to block, and a published contact. On iOS three already exist:

| Pillar | iOS today |
|---|---|
| Filter | Market glyphs are admin pre-reviewed before listing |
| Report | **Missing** |
| Block | Connections → "Remove and block" (`remove_connection(id, true)`) |
| Contact | Settings → Support → "Contact support", `mailto:ciao@pbbls.app`, from the admin-edited `settings_links` |

Other people's content reaches an iOS user in three places: the glyph market (drawings and names, `v_glyph_market`), the Connections list (a peer's display name and avatar glyph, `get_connections`), and the invite accept sheet (the inviter's name and glyph, `preview_connection_invite`). Moments are never shown to anyone but their owner on iOS, so there is nothing else to report.

`report_content` already takes a glyph. It cannot take a connected peer or an inviter: its `profile` branch needs the peer's `user_id` and a public profile, and the connection projections never hand a `user_id` to a client (standing rule).

## Decisions

1. **A person report is a `profile` report.** It is filed through a new RPC, `report_person`, which resolves the person on the server. No new `target_kind`. The admin queue, its "hide profile" action (`admin_set_content_hidden('profile', user_id)`) and the one-open-report-per-target index work unchanged, and a hidden profile already makes that person's invites go dark.
2. **`report_content` is not touched.** Glyph reports keep using it. Re-emitting its whole body (and changing its signature for a text token) was the alternative; it buys a single entry point at the cost of a drop-and-recreate and a re-emission.
3. **Reporting a connected person offers "Also remove and block", on by default.** The report and the block happen in one transaction.
4. **Reporting an inviter closes the invite sheet without accepting** and clears the parked invite. There is no block on that route: there is no connection yet.

## Server — `report_person` (part 1)

```sql
report_person(
  p_reason           text,
  p_detail           text    default null,
  p_connection_id    uuid    default null,
  p_invite_token     text    default null,
  p_remove_and_block boolean default false
) returns jsonb
-- security definer, set search_path = public
-- grant execute to authenticated; revoke from public, anon
```

**Checks, in order:**

1. `auth.uid()` null → `not_authenticated` (42501).
2. Exactly one of `p_connection_id` and `p_invite_token` → else `invalid_kind`.
3. `p_reason` in the nine `report_content` reasons (`sexual`, `violence`, `hate`, `harassment`, `self_harm`, `illegal`, `spam`, `impersonation`, `other`) → else `invalid_reason`.

**Connection route.** Select the `connections` row by id. Missing, or the caller is neither `user_a` nor `user_b` → `not_found`. The person is the other party; `via = 'connection'`.

**Invite route.** Select `connection_invites` by token and apply `preview_connection_invite`'s gates (its newest body, `20261007140000_block_severs_public_reads.sql`): missing, revoked or expired, the inviter has blocked the caller, or the inviter's profile has `hidden_at` set → `not_found`. Inviter = caller → `cannot_report_own`. `via = 'invite'`. `p_remove_and_block` is ignored.

**Snapshot.** `{handle, display_name, via}` from the person's `profiles` row.

**Insert.** Into `content_reports` with `target_kind = 'profile'`, `target_id = target_user_id = <person's user id>`, the reason, `nullif(btrim(p_detail), '')` and the snapshot, `on conflict (reporter_id, target_kind, target_id) where status = 'open' do nothing`. On conflict, read back the standing open report: filing twice is a success, and a person reported from their invite and again after connecting is one open report.

**Remove and block.** When the route is a connection and `p_remove_and_block` is true, call `public.remove_connection(p_connection_id, true)` after the insert, in the same transaction. `remove_connection` is called, never re-emitted, so its in-body append markers stay intact.

**Return.** The same projection as `report_content`: `{id, status, created_at}`, `created_at` whole-second UTC. Never a user id, never the snapshot.

**Unchanged.** `report_content`, `admin_list_content_reports`, `resolve_content_report`, `admin_set_content_hidden`, `purge_account` (`content_reports` is already purged), `remove_connection`, `preview_connection_invite`.

**Proof.** `verify-content-reports.ts` gains: a peer with a private profile reported through the connection; a non-party rejected `not_found`; an inviter reported through a live invite; an expired invite → `not_found`; own invite → `cannot_report_own`; both and neither routes → `invalid_kind`; filing twice returns the same report id; report plus remove-and-block leaves the connection gone and a `connection_blocks` row. The migration is replayed on a native local Postgres before `db:push`, then `db:types:remote` regenerates `types/database.ts`.

## iOS (parts 2 and 3)

### `ReportSheet` (part 2)

A native grouped list built from the app's own components (`pebblesList`, `pebblesListRow`, `PebblesCheckbox`-style selection, toolbar buttons). No new visual language.

- Title: "Report \<name>" for a person ("Report this person" when the name is null), "Report this glyph" for a glyph.
- "Why are you reporting?": nine single-choice rows in this order: Harassment, Hate, Impersonation, Sexual content, Violence, Self-harm, Illegal, Spam, Other. EN and FR.
- "Add details (optional)": a multi-line text field capped at 1,000 characters.
- Connection only: a toggle "Also remove and block \<name>", on by default.
- "Send report" is disabled until a reason is picked. While sending it shows progress and the sheet refuses interactive dismissal.
- Success, including a duplicate: the sheet turns to "Thanks. We'll look at it." with a Done button.
- Errors, matched by substring on the slug: `not_found` → "This is no longer available."; `cannot_report_own` → "You can't report your own."; anything else → "Couldn't send the report. Try again." inline, the error logged through `os.Logger`.

**Code.**

- `ReportService` (`Features/Report/`): `report(glyphId:reason:detail:)` → `report_content('glyph', …)`; `report(connectionId:reason:detail:removeAndBlock:)` and `report(inviteToken:reason:detail:)` → `report_person`. Returns `ReportReceipt {id, status, createdAt}`; throws `ReportError` (`.notFound`, `.ownContent`, `.other(String)`), mapped from the slugs.
- `ReportReason`: the nine cases, raw values = the server slugs, a `LocalizedStringResource` label each.
- `ReportTarget`: `.glyph(UUID)`, `.connection(id: UUID, name: String?)`, `.invite(token: String, name: String?)`. Decides the title, whether the toggle shows, and which service call runs.
- `ReportSheetModel` (`@Observable`): reason, detail, `removeAndBlock`, phase (`editing`, `sending`, `sent`, `failed(message)`), `canSend`. All logic lives here.
- `ReportSheet`: the view. `onSent: (ReportTarget, Bool) -> Void` tells the caller whether a block happened.

**Tests (Swift Testing).** `ReportReason` raw values equal the server slugs; each slug maps to its `ReportError`; `canSend` follows the reason and the phase; a duplicate (same receipt id twice) lands in `sent`; the toggle only exists for `.connection`; the receipt decodes a `report_person` response captured verbatim from the harness run.

### Entry points (part 3)

1. **Connections list.** The row's `pebblesContextMenu` gains "Report" (icon from the existing set) above "Remove". After a send with the toggle on, the row leaves the list as a removal does.
2. **Glyph detail drawer.** A "Report" button for a market glyph the user does not own. Ownership is known when the drawer has it; the server's `cannot_report_own` covers the rest.
3. **Invite accept sheet.** A quiet "Report" text button under Accept, shown only for a valid preview. After a send the sheet dismisses without accepting and `connections.pendingInviteToken` is cleared.

Verified on the simulator with a signed-in throwaway account (the maintainer signs in), with screenshots of all three for Arkaik and the App Store review notes.

## Delivery

One stack of three parts, all in M68:

| Part | Branch | Content |
|---|---|---|
| 1 | `fix/1008-report-person-ios` | Migration, `types/database.ts`, harness cases, this spec, a decision-log entry |
| 2 | `feat/838-report-sheet-ios` | `ReportService`, `ReportReason`, `ReportTarget`, `ReportSheetModel`, `ReportSheet`, strings, tests |
| 3 | `feat/838-report-entries-ios` | The three entry points, simulator pass |

Every branch carries `-ios` so Vercel skips it (part 1 is server-only, but its only client in this stack is iOS).

- **Arkaik:** the Connections, glyph detail and invite accept views move to `development` when part 2 starts, each gains a report acceptance, and part 3 records the screenshots. Covered in Arkaik, so the PRs carry `no-lab-note`.
- **Data safety:** no change. Report reasons and details were already collected through `report_content`, and the privacy policy covers moderation.
- **Decision log:** person reports are `profile` reports filed through `report_person`, never new kinds on `report_content`.
- **Kritik:** part 3 resolves `F-2026-08-PLT-ios-02`. `F-2026-08-PLT-ios-03` (contact) is already met by the Settings support row; resolving it is the maintainer's call.

## Out of scope

- Android (#839) reuses `report_person`; its UI is a separate stack.
- Web (#840) is untouched, including its signed-out reporting decision.
- No reporting of one's own moments, no block from the invite sheet, no admin UI change (`via` is visible in the snapshot JSON).
