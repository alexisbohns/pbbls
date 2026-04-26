# iOS Pebbles Pictures — Design

**Issue:** [#321](https://github.com/Bohns/pbbls/issues/321) · `[iOS] Introduce pebbles pictures`
**Milestone:** M25 · Improved core UX
**Date:** 2026-04-26
**Status:** Approved (pending spec review)

## 1. Intention

Let users attach one photo to a pebble on iOS. The schema and storage layout must accommodate raising the limit later (premium plan) without restructuring. End-to-end functional implementation; UI polish is out of scope.

## 2. Constraints

- **Storage cost:** only two resized JPEGs are ever stored per attachment. The source original never lands on Supabase.
- **No Docker locally.** All SQL is delivered as Supabase Studio-pasteable scripts in this spec.
- **No edge functions.** All image processing happens on iOS.
- **RPCs for multi-table writes** (`AGENTS.md` convention).
- **iOS-native image stack only.** No third-party image libraries.

## 3. Architecture

```
[PHPicker] → format check → HEIC/HEIF → JPEG → EXIF strip
           → resize 1024px (original.jpg, ≤1MB target, ≤1.5MB hard)
           → resize 420px  (thumb.jpg,    ≤300KB target)
           → background upload to {user_id}/{media_id}/...
           → on form save: create_pebble(..., p_media_id) RPC
                           ↳ atomic: pebble row + pebble_media row
           → on RPC failure: iOS deletes the two Storage files
```

Key decisions:

- **Single bucket** `pebbles-media`, **private** (no public read), **JPEG only**, 1.5 MB hard size cap.
- **`media_id` (UUIDv4) generated on iOS** before upload, used as the Storage folder name *and* as `pebble_media.id`.
- Path: `{user_id}/{media_id}/original.jpg` and `{user_id}/{media_id}/thumb.jpg`.
- Reads via **signed URLs**, 1 h TTL, batched in one round-trip per media.
- **Quota** lives in `profiles.max_media_per_pebble` (default 1), enforced by RPCs (not the client).
- **Orphan files accepted in V1** — periodic cleanup deferred to a follow-up issue.

## 4. Database schema

### 4.1 `pebble_media` table

```sql
create table public.pebble_media (
  id              uuid primary key default gen_random_uuid(),
  pebble_id       uuid not null references public.pebbles(id) on delete cascade,
  owner_id        uuid not null references auth.users(id)     on delete cascade,
  storage_prefix  text not null,                  -- "{owner_id}/{id}"
  created_at      timestamptz not null default now()
);

create index pebble_media_pebble_id_idx on public.pebble_media(pebble_id);
create index pebble_media_owner_id_idx  on public.pebble_media(owner_id);

alter table public.pebble_media enable row level security;
```

Notes:
- `owner_id` is denormalized from `pebbles.owner_id` so RLS doesn't have to join. Filled by the RPC, never trusted from the client.
- `storage_prefix` stored explicitly so reads don't need to recompute and so a future path-format change is easy.
- No `width` / `height` / `bytes` columns in V1. Add later if useful.
- Cascade deletes the row when the parent pebble is removed; Storage files are then orphaned (handled by the V2 sweep).

### 4.2 `profiles.max_media_per_pebble`

```sql
alter table public.profiles
  add column max_media_per_pebble integer not null default 1;
```

### 4.3 RLS

Read-only owner policy. All writes go through `security definer` RPCs.

```sql
create policy "pebble_media_owner_select" on public.pebble_media
  for select using (auth.uid() = owner_id);
```

## 5. RPCs

All `security definer`, all enforce ownership + quota. Error codes:
- `'P0001'` (custom) for `media_quota_exceeded`
- `'42501'` (insufficient privilege) for `forbidden`

iOS `switch`es on these to render the right user-facing message.

### 5.1 Extend `create_pebble`

Add an optional trailing parameter. Existing callers are unaffected.

```sql
-- Extended signature only — preserve the existing body and append the media branch
create or replace function public.create_pebble(
  -- ...existing params unchanged...
  p_media_id uuid default null
) returns uuid
language plpgsql security definer
as $$
declare
  v_pebble_id uuid;
  v_max int;
begin
  -- ...existing pebble insert, returning into v_pebble_id...

  if p_media_id is not null then
    select coalesce(max_media_per_pebble, 1) into v_max
      from public.profiles where id = auth.uid();

    if 1 > v_max then
      raise exception 'media_quota_exceeded' using errcode = 'P0001';
    end if;

    insert into public.pebble_media (id, pebble_id, owner_id, storage_prefix)
    values (
      p_media_id,
      v_pebble_id,
      auth.uid(),
      auth.uid()::text || '/' || p_media_id::text
    );
  end if;

  return v_pebble_id;
end;
$$;
```

### 5.2 New `add_pebble_media`

For attaching to an existing pebble (future-facing — V1 always uses `create_pebble` since the photo is picked during creation, but the RPC is symmetric and cheap to add now).

```sql
create or replace function public.add_pebble_media(
  p_pebble_id uuid,
  p_media_id  uuid
) returns uuid
language plpgsql security definer
as $$
declare
  v_owner uuid;
  v_max int;
  v_count int;
begin
  select owner_id into v_owner from public.pebbles where id = p_pebble_id;
  if v_owner is null or v_owner <> auth.uid() then
    raise exception 'forbidden' using errcode = '42501';
  end if;

  select coalesce(max_media_per_pebble, 1) into v_max
    from public.profiles where id = auth.uid() for update;

  select count(*) into v_count
    from public.pebble_media where pebble_id = p_pebble_id;

  if v_count >= v_max then
    raise exception 'media_quota_exceeded' using errcode = 'P0001';
  end if;

  insert into public.pebble_media (id, pebble_id, owner_id, storage_prefix)
  values (
    p_media_id,
    p_pebble_id,
    auth.uid(),
    auth.uid()::text || '/' || p_media_id::text
  );

  return p_media_id;
end;
$$;
```

`select … for update` on the profile row serializes concurrent uploads from the same user, preventing the quota race.

### 5.3 New `delete_pebble_media`

Returns `storage_prefix` so iOS knows what to clean up in Storage (Postgres can't reach Storage from a trigger).

```sql
create or replace function public.delete_pebble_media(p_media_id uuid)
returns text
language plpgsql security definer
as $$
declare
  v_prefix text;
begin
  delete from public.pebble_media
   where id = p_media_id and owner_id = auth.uid()
   returning storage_prefix into v_prefix;

  if v_prefix is null then
    raise exception 'forbidden' using errcode = '42501';
  end if;

  return v_prefix;
end;
$$;
```

### 5.4 Grants

```sql
grant execute on function public.create_pebble(/* …full signature… */) to authenticated;
grant execute on function public.add_pebble_media(uuid, uuid)          to authenticated;
grant execute on function public.delete_pebble_media(uuid)             to authenticated;
```

## 6. Storage bucket & policies

### 6.1 Bucket

```sql
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'pebbles-media',
  'pebbles-media',
  false,
  1572864,                          -- 1.5 MB hard cap
  array['image/jpeg']
)
on conflict (id) do update
  set public             = excluded.public,
      file_size_limit    = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;
```

### 6.2 Object policies

Path is `{user_id}/{media_id}/{file}`; first folder segment must equal `auth.uid()`.

```sql
create policy "pebbles_media_owner_select" on storage.objects
  for select using (
    bucket_id = 'pebbles-media'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

create policy "pebbles_media_owner_insert" on storage.objects
  for insert with check (
    bucket_id = 'pebbles-media'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

create policy "pebbles_media_owner_delete" on storage.objects
  for delete using (
    bucket_id = 'pebbles-media'
    and (storage.foldername(name))[1] = auth.uid()::text
  );
```

No UPDATE policy — replacing means delete + re-upload with a new `media_id`.

### 6.3 Reads

iOS always renders via short-lived signed URLs.

- TTL: **3600 s (1 h)**.
- Batch both files for one media in a single `createSignedURLs` call.
- In-memory cache keyed by `media_id`, evicted at expiry.
- List view shows `thumb.jpg`. Detail view shows `original.jpg`.

## 7. iOS implementation

### 7.1 Module layout

New feature folder `apps/ios/Pebbles/Features/PebbleMedia/`:

```
PebbleMedia/
  ImageFormatValidator.swift   // pure: UTI → supported / unsupported
  ImagePipeline.swift          // pure: Data → ProcessedImage or error
  PebbleMediaUploader.swift    // orchestrator: pick → process → upload → bind
  PebbleMediaRepository.swift  // Supabase calls (signed URLs, RPC wrappers)
  PhotoPickerView.swift        // PHPickerViewController wrapper
  AttachedPhotoView.swift      // thumbnail in form, retry/remove affordances
```

Hooks into the existing pebble create form via a single `@Binding var attachedMedia: AttachedMedia?`. `AttachedMedia` carries `media_id`, upload state (`uploading | uploaded | failed`), and local thumbnail data for instant in-form preview.

### 7.2 Image pipeline

Pure, no I/O. Native iOS only (`ImageIO`, `CoreGraphics`).

```swift
enum ImagePipelineError: Error {
  case unsupportedFormat
  case decodeFailed
  case encodeFailed
  case tooLargeAfterResize
}

struct ProcessedImage {
  let original: Data    // JPEG, max edge 1024, ≤1MB target
  let thumb:    Data    // JPEG, max edge 420,  ≤300KB target
}

func processImage(_ source: Data, uti: String) throws -> ProcessedImage
```

Implementation notes:
- Format gate via `ImageFormatValidator.isSupported(uti)`: accept `public.jpeg`, `public.png`, `public.heic`, `public.heif`. Anything else → `.unsupportedFormat`.
- Decode via `CGImageSourceCreateWithData`.
- Resize via `CGImageSourceCreateThumbnailAtIndex` with `kCGImageSourceThumbnailMaxPixelSize` (hardware-accelerated; faster and lower-memory than `UIImage.draw`).
- Encode via `CGImageDestinationAddImage` with `kUTTypeJPEG` and `kCGImageDestinationLossyCompressionQuality` (start 0.85 for original, 0.75 for thumb; step down by 0.1 if size cap exceeded, abort with `.tooLargeAfterResize` after 3 attempts).
- **EXIF stripping is implicit**: only the lossy-compression quality property is passed. No `kCGImagePropertyExifDictionary`, no `kCGImagePropertyGPSDictionary`, no orientation metadata beyond what the resize already baked in.

### 7.3 Upload sequence

```swift
// On photo pick (synchronous part)
let mediaId = UUID()
let processed = try ImagePipeline.processImage(rawData, uti: pickedUTI)
attachedMedia = .init(id: mediaId, state: .uploading, localThumb: processed.thumb)

// Background upload (Task), parallel
async let originalUpload = supabase.storage.from("pebbles-media")
  .upload(path: "\(userId)/\(mediaId)/original.jpg",
          file: processed.original,
          options: .init(contentType: "image/jpeg"))
async let thumbUpload    = supabase.storage.from("pebbles-media")
  .upload(path: "\(userId)/\(mediaId)/thumb.jpg",
          file: processed.thumb,
          options: .init(contentType: "image/jpeg"))

do {
  _ = try await (originalUpload, thumbUpload)
  attachedMedia?.state = .uploaded
} catch {
  // 1 auto-retry after 2s; on second failure, .failed (user can retry or remove)
}

// On form save — separate, in the create-pebble flow
try await supabase.rpc("create_pebble", params: [..., "p_media_id": mediaId])
// On RPC failure: fire-and-forget delete of {userId}/{mediaId}/original.jpg + thumb.jpg
```

### 7.4 Read path

`PebbleMediaRepository.signedUrls(prefix: String) -> (originalURL, thumbURL)`:

```swift
let urls = try await supabase.storage.from("pebbles-media")
  .createSignedURLs(paths: ["\(prefix)/original.jpg", "\(prefix)/thumb.jpg"],
                    expiresIn: 3600)
```

Cache in memory keyed by `media_id`, evict at TTL.

### 7.5 Error → UI mapping

| Error                                            | Where                          | Affordance                |
|--------------------------------------------------|--------------------------------|---------------------------|
| `unsupportedFormat`, `decodeFailed`, `encodeFailed`, `tooLargeAfterResize` | Inline alert in picker         | Pick another              |
| Upload network / 5xx (after 1 retry)             | Badge on attached thumbnail    | Retry, or remove          |
| RPC `media_quota_exceeded` (P0001)               | Toast on save                  | "Photo limit reached on this pebble" |
| RPC `forbidden` (42501)                          | Toast on save                  | "Couldn't save, try again" |
| Compensating Storage `delete` failure            | `console.error`-equivalent log | Ignore (orphan sweep)     |

Quota text shown to user reflects `profiles.max_media_per_pebble` for forward compatibility with premium plans.

## 8. Security model

- **Bucket-level guardrails**: private bucket, MIME `image/jpeg` only, 1.5 MB hard size cap. A modified client cannot upload non-images, oversize files, or to other users' folders.
- **RLS** on `pebble_media`: owner-only SELECT. All writes via `security definer` RPCs that re-check ownership against `pebbles.owner_id` and quota against `profiles.max_media_per_pebble`.
- **EXIF**: stripped by re-encoding through `CGImageDestination` without metadata dictionaries. GPS data never leaves the device.
- **Signed URLs**: 1 h TTL, minted server-side per session. No public reads.
- **User deletion**: path encodes `user_id`, so a future user-deletion flow can list-and-purge by prefix.

Open hardening items (not blocking V1):
- Magic-byte sniffing on iOS as an extra check beyond UTI (UTI can lie if the file extension is wrong but data is valid). Low impact since the bucket gate is `image/jpeg`.
- Rate-limiting upload calls per user. Currently relies on the storage size cap and per-pebble quota.

## 9. Testing strategy

V1 has no test suite, but the design is structured to be test-ready:

- `ImageFormatValidator` and `ImagePipeline` are pure functions over `Data` — trivially unit-testable with fixture images (HEIC sample, EXIF-laden JPEG, oversize PNG, malformed bytes).
- `PebbleMediaRepository` is the only Supabase-touching module — mockable behind a protocol when tests arrive.
- RPCs can be exercised via `psql` fixtures (insert two profiles, attempt cross-owner `add_pebble_media`, expect `forbidden`).

Manual smoke test before shipping:
1. Pick HEIC → confirm JPEG arrives in bucket, EXIF dict empty (`exiftool`).
2. Pick a 12 MP photo → confirm both files ≤ targets.
3. Force quota = 0 in `profiles` → attempt save → expect quota toast, no Storage residue.
4. Kill network mid-upload → confirm retry → confirm inline failure UI after second failure.
5. Delete pebble → confirm `pebble_media` row gone (and Storage files become orphans, expected).

## 10. Out of scope for V1

- Multiple media per pebble (column already supports it).
- Premium plan UI or upgrade flow (column ready, no surface yet).
- Public/shareable pebbles (RLS already structured to extend).
- Storage orphan cleanup (see follow-up).
- WebP encoding (skipped — JPEG only for native simplicity).
- Web app surface (issue is iOS-only).

## 11. Follow-up issues to file after V1 ships

1. **Orphan Storage cleanup sweep.** Detect files under `{user_id}/{folder}/` whose folder name does not appear as `pebble_media.id`. Run periodically. Two sources: abandoned form uploads, and pebble deletions.
2. **Server-side magic-byte validation** (optional hardening). Edge function or Storage trigger that re-checks the JPEG header on uploaded files; deletes non-conforming.
3. **Multiple media per pebble.** UI for picking multiple photos, gallery view, reordering. Schema already supports.
4. **Premium quota raise.** Upgrade flow + admin tool to flip `max_media_per_pebble`.
5. **Public pebble read access.** Add a SELECT policy variant when public/shareable pebbles ship.

## 12. Operational steps (paste into Supabase Studio)

Run in this order:

1. `pebble_media` table + indexes + RLS enable + SELECT policy (§4.1, §4.3).
2. `profiles.max_media_per_pebble` column (§4.2).
3. Extend `create_pebble` (§5.1).
4. Create `add_pebble_media` (§5.2) and `delete_pebble_media` (§5.3).
5. Grants (§5.4).
6. Bucket creation (§6.1).
7. Object policies (§6.2).
8. Regenerate types: `npm run db:types --workspace=packages/supabase` and commit `packages/supabase/types/database.ts` (per `AGENTS.md`).

Once Studio runs are confirmed, drop the same SQL into a migration file at `packages/supabase/supabase/migrations/` for repo history (your call on timing).
