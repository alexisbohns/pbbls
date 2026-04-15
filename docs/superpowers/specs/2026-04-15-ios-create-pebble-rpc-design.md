# iOS — Migrate CreatePebbleSheet to create_pebble RPC

**Issue:** #257 · **Labels:** `quality`, `ios`, `core`

## Intention

`CreatePebbleSheet` currently writes a new pebble via four direct `.insert()` calls (`pebbles`, `pebble_domains`, `pebble_souls`, `collection_pebbles`). A partial failure mid-save leaves the database inconsistent. Migrate it to call the `create_pebble` RPC instead — one atomic Postgres transaction, with karma computation happening server-side.

This closes the asymmetry introduced by #258: edit uses an RPC, create does not.

## Decisions

1. **`onCreated` callback signature changes from `(Pebble) -> Void` to `() -> Void`.** `PathView` refetches the full list in the callback, mirroring how `EditPebbleSheet.onSaved` already works. The `create_pebble` RPC returns only a `uuid`, and introducing a round trip to then fetch the full `Pebble` row is wasted work when `PathView` already has a `load()` method. Symmetry with edit is worth more than a single-item append.
2. **`PebbleCreatePayload` mirrors `PebbleUpdatePayload`'s shape exactly.** Same snake_case keys, same single-into-array wrapping for domain/soul/collection, same "trim description, encode nil when empty" behavior. New file; the two types do not share code because the payloads the RPCs accept are subtly different (create does not accept pebble_id; update coalesces missing scalars against the existing row). Symmetric but independent.
3. **Delete dead code after the refactor.** `PebbleInsert.swift` and the private `PebbleDomainRow` / `PebbleSoulRow` / `CollectionPebbleRow` structs inside `CreatePebbleSheet.swift` have no other callers and go away.

## File changes

### Created

| Path | Responsibility |
|---|---|
| `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift` | Encodable payload for the `create_pebble` RPC. |
| `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift` | Tests mirroring `PebbleUpdatePayloadEncodingTests`. |

### Modified

| Path | Change |
|---|---|
| `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` | `save()` calls `create_pebble` RPC; `onCreated` signature becomes `() -> Void`; private helper row structs deleted. |
| `apps/ios/Pebbles/Features/Path/PathView.swift` | `onCreated` closure calls `Task { await load() }`; `handleCreated(_:)` deleted. |

### Deleted

| Path | Reason |
|---|---|
| `apps/ios/Pebbles/Features/Path/Models/PebbleInsert.swift` | No callers after the refactor. |

## Data flow

```
User taps Save in CreatePebbleSheet
  → CreatePebbleSheet.save()
  → PebbleCreatePayload(from: draft)
  → supabase.client.rpc("create_pebble", params: CreatePebbleParams(payload: ...)).execute().value → UUID
  → onCreated()
  → PathView reloads list
  → dismiss()
```

The RPC `public.create_pebble(payload jsonb) returns uuid` does all insertions (pebble row + join rows) and karma computation inside a single Postgres transaction.

## Payload shape

`PebbleCreatePayload` fields, matching the RPC's `payload jsonb`:

- `name: String`
- `description: String?` (nil when empty after trim)
- `happened_at: Date`
- `intensity: Int`
- `positiveness: Int`
- `visibility: String` (`"private"` / `"public"`)
- `emotion_id: UUID`
- `domain_ids: [UUID]` (always one element for current single-select UI)
- `soul_ids: [UUID]` (zero or one)
- `collection_ids: [UUID]` (zero or one)

Not sent (out of scope for current UI): `glyph_id`, `new_glyph`, `new_souls`, `new_collections`, `cards`, `snaps`. The RPC treats absent keys as "no-op" so we do not need to send empty arrays for these.

## RPC call wrapper

The SDK's `.rpc(_ fn:, params:)` signature encodes `params` as the JSON body. The RPC takes `(payload jsonb)` as its only argument, so the wrapper is a one-field struct:

```swift
private struct CreatePebbleParams: Encodable {
    let payload: PebbleCreatePayload
}
```

(Unlike `EditPebbleSheet`'s `UpdatePebbleParams` which also carries `p_pebble_id`.)

The return type is `UUID` — decoded from the response body via `.execute().value`. The sheet does not currently need the returned UUID (`onCreated` is now parameterless), but we still `try await` the call to surface errors. If the decoded-UUID step is awkward, discard the return with `_ = try await ....execute()` — the side effect is what matters, and we're ignoring the returned id anyway.

## Error handling and logging

Unchanged from today: `isSaving` / `saveError` state, `os.Logger` on error paths, user-facing error string in a Form section. The `create_pebble` RPC's ownership check on `collection_ids` (added in #258) may now raise `'Collection not owned by user'` — which the client surfaces as the generic "Couldn't save your pebble. Please try again." Good enough; we do not parse server error strings to customize the UI.

## Testing

New test suite `PebbleCreatePayloadEncodingTests` mirrors the existing `PebbleUpdatePayloadEncodingTests` one-for-one:

- All scalar fields encode with snake_case keys
- `domain_ids` is a single-element array
- `soul_ids` / `collection_ids` are empty arrays when unset, single-element when set
- `description` encodes as null when empty-trimmed

Existing tests all continue to pass. No new integration tests — the RPC itself is already exercised (added in #258).

## Out of scope

- Inline creation of souls / collections from the sheet (RPC supports it; UI does not, no change here).
- Multi-select for domain/soul/collection.
- Refetching the newly created pebble to restore the old optimistic-append behavior.
