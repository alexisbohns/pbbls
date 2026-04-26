# iOS Pebbles Pictures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users attach one photo to a pebble on iOS, end-to-end functional, using only iOS-native image APIs and the existing `snaps` table.

**Architecture:** iOS picks a photo, validates the format, converts HEIC/HEIF→JPEG, strips EXIF, resizes to two JPEGs (1024px / 420px), uploads both to a private Supabase Storage bucket `pebbles-media` at path `{user_id}/{snap_id}/{original|thumb}.jpg` while the user fills the form. On save, the iOS-generated `snap_id` is included in the `snaps` payload to the existing `create_pebble` RPC, which inserts the snap row in the same transaction as the pebble. Quota is enforced server-side via a new `profiles.max_media_per_pebble` column. On RPC failure iOS deletes the orphan files; on success, the pebble detail view fetches signed URLs (1 h TTL) to render the image.

**Tech Stack:** Swift 5.9 / SwiftUI / iOS 17+, `ImageIO` + `CoreGraphics` for image processing (no third-party libs), `PHPickerViewController` for selection, Supabase Swift SDK 2.x for Storage and RPC, Supabase Postgres + Storage on the backend, Swift Testing for unit tests.

**Spec:** [`docs/superpowers/specs/2026-04-26-ios-pebbles-pictures-design.md`](../specs/2026-04-26-ios-pebbles-pictures-design.md). The spec describes a new `pebble_media` table; this plan supersedes that with the existing `snaps` table (same shape, already wired into karma calculations and the `create_pebble` RPC). The bucket layout, iOS pipeline, and error UX are unchanged from the spec.

---

## Phase 1 — Database (Supabase Studio, no Docker)

All SQL is meant to be pasted into Supabase Studio's SQL editor against the linked remote project. After every Phase 1 task that runs SQL, also drop the same SQL into a new migration file under `packages/supabase/supabase/migrations/` for repo history. The migration filename uses today's date and a numeric suffix (e.g. `20260426000000_pebbles_media_setup.sql`); commits in this plan will reference one consolidated migration file added in **Task 1.5**.

### Task 1.1: Add `profiles.max_media_per_pebble` column

**Files:**
- Run in Supabase Studio: SQL below
- Will add to migration file in Task 1.5

- [ ] **Step 1: Run in Supabase Studio**

```sql
alter table public.profiles
  add column if not exists max_media_per_pebble integer not null default 1;
```

- [ ] **Step 2: Verify column exists**

In Studio, run:

```sql
select column_name, data_type, column_default, is_nullable
  from information_schema.columns
 where table_schema = 'public'
   and table_name = 'profiles'
   and column_name = 'max_media_per_pebble';
```

Expected output: 1 row, `data_type = integer`, `column_default = 1`, `is_nullable = NO`.

- [ ] **Step 3: Verify existing rows received the default**

```sql
select count(*) as total,
       count(*) filter (where max_media_per_pebble = 1) as defaulted
  from public.profiles;
```

Expected: `total = defaulted` (every existing profile has the default 1).

### Task 1.2: Create `pebbles-media` Storage bucket

**Files:**
- Run in Supabase Studio
- Will add to migration file in Task 1.5

- [ ] **Step 1: Run in Supabase Studio**

```sql
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'pebbles-media',
  'pebbles-media',
  false,
  1572864,                      -- 1.5 MB hard cap
  array['image/jpeg']
)
on conflict (id) do update
  set public             = excluded.public,
      file_size_limit    = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;
```

- [ ] **Step 2: Verify bucket settings**

```sql
select id, public, file_size_limit, allowed_mime_types
  from storage.buckets
 where id = 'pebbles-media';
```

Expected: 1 row, `public = false`, `file_size_limit = 1572864`, `allowed_mime_types = {image/jpeg}`.

### Task 1.3: Create owner-scoped Storage object policies

**Files:**
- Run in Supabase Studio
- Will add to migration file in Task 1.5

- [ ] **Step 1: Run in Supabase Studio**

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

(No UPDATE policy — replacing means delete + re-upload with a new `snap_id`.)

- [ ] **Step 2: Verify policies exist**

```sql
select policyname, cmd
  from pg_policies
 where schemaname = 'storage'
   and tablename  = 'objects'
   and policyname like 'pebbles_media_%'
 order by policyname;
```

Expected: 3 rows — `pebbles_media_owner_delete (DELETE)`, `pebbles_media_owner_insert (INSERT)`, `pebbles_media_owner_select (SELECT)`.

### Task 1.4: Update `create_pebble` to accept `snaps[].id` and enforce quota

The current `create_pebble` already inserts into `public.snaps` from `payload->'snaps'`, but does two things wrong for our needs: (a) ignores `id` from the payload (always generates a fresh UUID), and (b) does not enforce a per-pebble quota. We change both, leaving the rest of the function body untouched.

**Files:**
- Run in Supabase Studio
- Will add to migration file in Task 1.5
- Reference: current body lives in `packages/supabase/supabase/migrations/20260415000000_pebble_rpc_collections.sql:12-187`

- [ ] **Step 1: Run in Supabase Studio**

Paste this complete function. It is identical to the current version *except*:
- new variable `v_max_media int;` in the `declare` block
- the `Insert snaps` block now reads `id` from each snap and gates on `max_media_per_pebble`

```sql
create or replace function public.create_pebble(payload jsonb)
returns uuid as $$
declare
  v_user_id uuid := auth.uid();
  v_pebble_id uuid;
  v_glyph_id uuid;
  v_soul_ids uuid[];
  v_collection_ids uuid[];
  v_new_soul record;
  v_new_soul_id uuid;
  v_new_collection record;
  v_new_collection_id uuid;
  v_card record;
  v_snap record;
  v_karma int;
  v_cards_count int;
  v_souls_count int;
  v_domains_count int;
  v_snaps_count int;
  v_unauthorized_collection uuid;
  v_max_media int;
begin
  -- Inline glyph creation
  if payload ? 'new_glyph' then
    insert into public.glyphs (user_id, name, shape_id, strokes, view_box)
    values (
      v_user_id,
      (payload->'new_glyph'->>'name'),
      (payload->'new_glyph'->>'shape_id')::uuid,
      coalesce(payload->'new_glyph'->'strokes', '[]'::jsonb),
      (payload->'new_glyph'->>'view_box')
    )
    returning id into v_glyph_id;
  else
    v_glyph_id := (payload->>'glyph_id')::uuid;
  end if;

  -- Collect existing soul IDs
  select array_agg(val::uuid)
  into v_soul_ids
  from jsonb_array_elements_text(coalesce(payload->'soul_ids', '[]'::jsonb)) val;

  -- Inline soul creation
  if payload ? 'new_souls' then
    for v_new_soul in select * from jsonb_array_elements(payload->'new_souls')
    loop
      insert into public.souls (user_id, name)
      values (v_user_id, v_new_soul.value->>'name')
      returning id into v_new_soul_id;

      v_soul_ids := array_append(v_soul_ids, v_new_soul_id);
    end loop;
  end if;

  -- Collect existing collection IDs
  select array_agg(val::uuid)
  into v_collection_ids
  from jsonb_array_elements_text(coalesce(payload->'collection_ids', '[]'::jsonb)) val;

  -- Inline collection creation
  if payload ? 'new_collections' then
    for v_new_collection in select * from jsonb_array_elements(payload->'new_collections')
    loop
      insert into public.collections (user_id, name)
      values (v_user_id, v_new_collection.value->>'name')
      returning id into v_new_collection_id;

      v_collection_ids := array_append(v_collection_ids, v_new_collection_id);
    end loop;
  end if;

  -- Collection ownership check
  if v_collection_ids is not null then
    select c_id into v_unauthorized_collection
    from unnest(v_collection_ids) as c_id
    where not exists (
      select 1 from public.collections
      where id = c_id and user_id = v_user_id
    )
    limit 1;

    if v_unauthorized_collection is not null then
      raise exception 'Collection not owned by user: %', v_unauthorized_collection;
    end if;
  end if;

  -- Create the pebble
  insert into public.pebbles (
    user_id, name, description, happened_at,
    intensity, positiveness, visibility,
    emotion_id, glyph_id
  )
  values (
    v_user_id,
    payload->>'name',
    payload->>'description',
    (payload->>'happened_at')::timestamptz,
    (payload->>'intensity')::smallint,
    (payload->>'positiveness')::smallint,
    coalesce(payload->>'visibility', 'private'),
    (payload->>'emotion_id')::uuid,
    v_glyph_id
  )
  returning id into v_pebble_id;

  -- Insert cards
  v_cards_count := 0;
  if payload ? 'cards' then
    for v_card in select * from jsonb_array_elements(payload->'cards')
    loop
      insert into public.pebble_cards (pebble_id, species_id, value, sort_order)
      values (
        v_pebble_id,
        (v_card.value->>'species_id')::uuid,
        v_card.value->>'value',
        coalesce((v_card.value->>'sort_order')::smallint, 0)
      );
      v_cards_count := v_cards_count + 1;
    end loop;
  end if;

  -- Insert pebble_souls
  v_souls_count := 0;
  if v_soul_ids is not null then
    insert into public.pebble_souls (pebble_id, soul_id)
    select v_pebble_id, unnest(v_soul_ids);
    v_souls_count := array_length(v_soul_ids, 1);
  end if;

  -- Insert pebble_domains
  v_domains_count := 0;
  if payload ? 'domain_ids' then
    insert into public.pebble_domains (pebble_id, domain_id)
    select v_pebble_id, (val::text)::uuid
    from jsonb_array_elements_text(payload->'domain_ids') val;
    v_domains_count := jsonb_array_length(payload->'domain_ids');
  end if;

  -- Insert collection_pebbles
  if v_collection_ids is not null then
    insert into public.collection_pebbles (collection_id, pebble_id)
    select unnest(v_collection_ids), v_pebble_id;
  end if;

  -- Insert snaps (accepts iOS-generated id; enforces per-pebble quota)
  v_snaps_count := 0;
  if payload ? 'snaps' then
    select coalesce(max_media_per_pebble, 1) into v_max_media
      from public.profiles where id = v_user_id;

    if jsonb_array_length(payload->'snaps') > v_max_media then
      raise exception 'media_quota_exceeded' using errcode = 'P0001';
    end if;

    for v_snap in select * from jsonb_array_elements(payload->'snaps')
    loop
      insert into public.snaps (id, pebble_id, user_id, storage_path, sort_order)
      values (
        coalesce((v_snap.value->>'id')::uuid, gen_random_uuid()),
        v_pebble_id,
        v_user_id,
        v_snap.value->>'storage_path',
        coalesce((v_snap.value->>'sort_order')::smallint, 0)
      );
      v_snaps_count := v_snaps_count + 1;
    end loop;
  end if;

  -- Compute and insert karma
  v_karma := public.compute_karma_delta(
    payload->>'description',
    v_cards_count,
    v_souls_count,
    v_domains_count,
    v_glyph_id is not null,
    v_snaps_count
  );

  insert into public.karma_events (user_id, delta, reason, ref_id)
  values (v_user_id, v_karma, 'pebble_created', v_pebble_id);

  return v_pebble_id;
end;
$$ language plpgsql security definer set search_path = public;
```

- [ ] **Step 2: Verify the function still compiles and is callable**

```sql
select pg_get_function_arguments(oid), pg_get_function_result(oid)
  from pg_proc
 where proname = 'create_pebble' and pronamespace = 'public'::regnamespace;
```

Expected: 1 row, arguments `payload jsonb`, result `uuid`.

- [ ] **Step 3: Smoke-test quota with the SQL editor (read-only check, no real insert)**

```sql
-- Confirm the new branch parses by issuing an obvious quota violation
-- against your own profile. Wrap in a savepoint so nothing is committed.
begin;
savepoint s;

-- Set yourself a quota of 0 for this transaction only
update public.profiles set max_media_per_pebble = 0 where id = auth.uid();

-- Attempt a create_pebble call with 1 snap; expect SQLSTATE P0001
do $$
begin
  perform public.create_pebble(jsonb_build_object(
    'name', 'quota probe',
    'happened_at', now(),
    'intensity', 1,
    'positiveness', 1,
    'visibility', 'private',
    'emotion_id', (select id from public.emotions limit 1),
    'domain_ids', jsonb_build_array((select id from public.domains limit 1)),
    'snaps', jsonb_build_array(jsonb_build_object(
      'id', gen_random_uuid(),
      'storage_path', 'probe'
    ))
  ));
exception when sqlstate 'P0001' then
  raise notice 'OK: P0001 raised as expected';
end$$;

rollback to savepoint s;
rollback;
```

Expected: notice `OK: P0001 raised as expected`. If anything else surfaces, the function is broken — re-paste Step 1.

### Task 1.5: Capture Phase 1 SQL as a migration file (for repo history)

**Files:**
- Create: `packages/supabase/supabase/migrations/20260426000000_pebbles_media_setup.sql`

- [ ] **Step 1: Create the migration file**

Paste the complete contents below. This is the union of Tasks 1.1 → 1.4, in the same order.

```sql
-- Migration: pebbles media setup
-- Adds the per-pebble photo quota column, the pebbles-media Storage bucket
-- and its owner-scoped object policies, and updates create_pebble to (a) accept
-- iOS-generated snap ids and (b) enforce profiles.max_media_per_pebble.

-- ============================================================
-- profiles: per-pebble media quota
-- ============================================================

alter table public.profiles
  add column if not exists max_media_per_pebble integer not null default 1;

-- ============================================================
-- Storage bucket
-- ============================================================

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'pebbles-media',
  'pebbles-media',
  false,
  1572864,
  array['image/jpeg']
)
on conflict (id) do update
  set public             = excluded.public,
      file_size_limit    = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

-- Owner-scoped object policies
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

-- ============================================================
-- create_pebble — accepts snaps[].id, enforces quota
-- ============================================================

-- (Paste the full create_pebble body from Task 1.4 Step 1 here verbatim.)
```

- [ ] **Step 2: Replace the placeholder comment with the function body from Task 1.4 Step 1**

Open the file in an editor and replace the final `-- (Paste the full create_pebble body from Task 1.4 Step 1 here verbatim.)` line with the complete `create or replace function public.create_pebble(payload jsonb) ...` block from Task 1.4 Step 1.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/migrations/20260426000000_pebbles_media_setup.sql
git commit -m "feat(db): add pebbles-media bucket, profile quota, snaps id+quota in create_pebble"
```

### Task 1.6: Regenerate TypeScript types

The web/admin apps consume `packages/supabase/types/database.ts`. The `profiles.max_media_per_pebble` column changed the schema.

**Files:**
- Modify: `packages/supabase/types/database.ts`

- [ ] **Step 1: Generate types from the linked remote project (no Docker required)**

Run from the repo root:

```bash
npx supabase gen types typescript --linked > packages/supabase/types/database.ts
```

If the CLI prompts for a project link, follow the prompt. If a Supabase MCP server is available (per `packages/supabase/CLAUDE.md`), `generate_typescript_types` is an equivalent fallback.

- [ ] **Step 2: Verify the new column appears**

```bash
grep -n "max_media_per_pebble" packages/supabase/types/database.ts
```

Expected: at least one match inside the `profiles` table type (Row, Insert, and Update variants).

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/types/database.ts
git commit -m "chore(db): regenerate types after pebbles-media setup"
```

---

## Phase 2 — iOS image pipeline (pure functions, TDD)

These two files contain no I/O. They are unit-tested with fixture image data using Swift Testing.

### Task 2.1: Image format validator

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/ImageFormatValidator.swift`
- Create: `apps/ios/PebblesTests/ImageFormatValidatorTests.swift`

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/ImageFormatValidatorTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("ImageFormatValidator")
struct ImageFormatValidatorTests {

    @Test("accepts JPEG, PNG, HEIC, HEIF UTIs")
    func acceptsSupportedUTIs() {
        #expect(ImageFormatValidator.isSupported("public.jpeg"))
        #expect(ImageFormatValidator.isSupported("public.png"))
        #expect(ImageFormatValidator.isSupported("public.heic"))
        #expect(ImageFormatValidator.isSupported("public.heif"))
    }

    @Test("rejects video, gif, webp, and arbitrary UTIs")
    func rejectsUnsupportedUTIs() {
        #expect(!ImageFormatValidator.isSupported("public.movie"))
        #expect(!ImageFormatValidator.isSupported("com.compuserve.gif"))
        #expect(!ImageFormatValidator.isSupported("org.webmproject.webp"))
        #expect(!ImageFormatValidator.isSupported(""))
        #expect(!ImageFormatValidator.isSupported("anything-else"))
    }
}
```

- [ ] **Step 2: Generate Xcode project and run the test, verify it fails**

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/ImageFormatValidatorTests test
```

Expected: build failure — `ImageFormatValidator` not found.

- [ ] **Step 3: Implement the minimal code to make the test pass**

Create `apps/ios/Pebbles/Features/PebbleMedia/ImageFormatValidator.swift`:

```swift
import Foundation

/// Pure UTI gate for `PHPickerResult.itemProvider.registeredTypeIdentifiers`.
/// We accept only formats that `ImageIO`'s JPEG encoder can ingest natively
/// on iOS 17 (HEIC/HEIF decode is built into the OS).
enum ImageFormatValidator {

    static let supportedUTIs: Set<String> = [
        "public.jpeg",
        "public.png",
        "public.heic",
        "public.heif"
    ]

    static func isSupported(_ uti: String) -> Bool {
        supportedUTIs.contains(uti)
    }
}
```

- [ ] **Step 4: Re-run the test, verify it passes**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/ImageFormatValidatorTests test
```

Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/ImageFormatValidator.swift \
        apps/ios/PebblesTests/ImageFormatValidatorTests.swift
git commit -m "feat(ios): add ImageFormatValidator for picker UTIs"
```

### Task 2.2: Image pipeline error type and result struct

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/ImagePipeline.swift`

- [ ] **Step 1: Create the file with the type definitions only**

```swift
import Foundation
import ImageIO
import CoreGraphics
import UniformTypeIdentifiers

/// Output of `ImagePipeline.process`. The two `Data` blobs are JPEG bytes
/// ready to upload to Supabase Storage; no further processing required.
struct ProcessedImage: Equatable {
    /// JPEG, max 1024 px on the long edge, target ≤1 MB.
    let original: Data
    /// JPEG, max 420 px on the long edge, target ≤300 KB.
    let thumb: Data
}

enum ImagePipelineError: Error, Equatable {
    /// UTI not in `ImageFormatValidator.supportedUTIs`.
    case unsupportedFormat
    /// `CGImageSourceCreateWithData` returned nil or no image at index 0.
    case decodeFailed
    /// `CGImageDestinationFinalize` returned false, or no `Data` produced.
    case encodeFailed
    /// Encoded result still exceeds the size cap after the configured number
    /// of quality reductions.
    case tooLargeAfterResize
}
```

(No tests yet — this is just the type. The `process` function in Task 2.3 will exercise both.)

- [ ] **Step 2: Build to confirm it compiles**

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build
```

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/ImagePipeline.swift
git commit -m "feat(ios): add ProcessedImage and ImagePipelineError types"
```

### Task 2.3: Image pipeline — `process(_:uti:)`

This is the heart of the feature. It validates, decodes via `ImageIO`, generates two thumbnails using `kCGImageSourceCreateThumbnailFromImageAlways` (hardware-accelerated, low-memory), and re-encodes both as JPEG without metadata dictionaries (which silently strips EXIF and GPS).

**Files:**
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/ImagePipeline.swift`
- Create: `apps/ios/PebblesTests/ImagePipelineTests.swift`
- Create: `apps/ios/PebblesTests/Fixtures/sample-jpeg-with-exif.jpg` (real fixture, see Step 1)
- Create: `apps/ios/PebblesTests/Fixtures/sample-large-png.png` (real fixture, see Step 1)

- [ ] **Step 1: Add fixture images and wire them into the test target**

Generate two fixture files locally:

```bash
mkdir -p apps/ios/PebblesTests/Fixtures

# A 4000x3000 JPEG with embedded EXIF GPS coords. Use any photo from your
# library that has EXIF; verify with `exiftool` that GPSLatitude is present.
cp ~/path/to/some-photo-with-gps.jpg apps/ios/PebblesTests/Fixtures/sample-jpeg-with-exif.jpg

# A 4000x3000 PNG generated synthetically:
sips -s format png -z 3000 4000 apps/ios/PebblesTests/Fixtures/sample-jpeg-with-exif.jpg \
  --out apps/ios/PebblesTests/Fixtures/sample-large-png.png

# Verify EXIF GPS presence in the JPEG fixture
exiftool apps/ios/PebblesTests/Fixtures/sample-jpeg-with-exif.jpg | grep -i gps
```

Expected: at least one `GPS Latitude` / `GPS Longitude` line. If none, find another photo.

Add to `apps/ios/project.yml` under the `PebblesTests` target so they ship with the test bundle. Edit `apps/ios/project.yml`:

```yaml
  PebblesTests:
    type: bundle.unit-test
    platform: iOS
    deploymentTarget: "17.0"
    sources:
      - path: PebblesTests
        excludes:
          - "Fixtures/**"
      - path: PebblesTests/Fixtures
        type: folder         # copies the folder as-is into the bundle
        buildPhase: resources
```

Then regenerate:

```bash
npm run generate --workspace=@pbbls/ios
```

- [ ] **Step 2: Write the failing tests**

Create `apps/ios/PebblesTests/ImagePipelineTests.swift`:

```swift
import Foundation
import ImageIO
import Testing
@testable import Pebbles

@Suite("ImagePipeline")
struct ImagePipelineTests {

    private func fixture(_ name: String) throws -> Data {
        let url = try #require(Bundle(for: BundleAnchor.self)
            .url(forResource: name, withExtension: nil, subdirectory: "Fixtures"))
        return try Data(contentsOf: url)
    }

    @Test("rejects unsupported UTI without touching the bytes")
    func unsupportedFormat() {
        let bogus = Data([0x00, 0x01, 0x02])
        #expect(throws: ImagePipelineError.unsupportedFormat) {
            try ImagePipeline.process(bogus, uti: "public.movie")
        }
    }

    @Test("processes a large JPEG into two JPEGs under their size caps")
    func processesJPEG() throws {
        let data = try fixture("sample-jpeg-with-exif.jpg")
        let result = try ImagePipeline.process(data, uti: "public.jpeg")
        #expect(result.original.count <= 1_048_576, "original must be <= 1 MB")
        #expect(result.thumb.count    <=   307_200, "thumb must be <= 300 KB")
        try assertJPEG(result.original)
        try assertJPEG(result.thumb)
        try assertMaxEdge(result.original, atMost: 1024)
        try assertMaxEdge(result.thumb,    atMost: 420)
    }

    @Test("processes a PNG into JPEGs (format normalization)")
    func processesPNG() throws {
        let data = try fixture("sample-large-png.png")
        let result = try ImagePipeline.process(data, uti: "public.png")
        try assertJPEG(result.original)
        try assertJPEG(result.thumb)
    }

    @Test("strips EXIF GPS metadata from the encoded outputs")
    func stripsEXIF() throws {
        let data = try fixture("sample-jpeg-with-exif.jpg")
        let result = try ImagePipeline.process(data, uti: "public.jpeg")
        #expect(!hasGPSDictionary(in: result.original))
        #expect(!hasGPSDictionary(in: result.thumb))
    }

    // MARK: - helpers

    private func assertJPEG(_ data: Data) throws {
        // JPEG SOI marker
        #expect(data.starts(with: [0xFF, 0xD8, 0xFF]))
    }

    private func assertMaxEdge(_ data: Data, atMost limit: Int) throws {
        let source = try #require(CGImageSourceCreateWithData(data as CFData, nil))
        let props = try #require(CGImageSourceCopyPropertiesAtIndex(source, 0, nil)
                                 as? [CFString: Any])
        let w = props[kCGImagePropertyPixelWidth]  as? Int ?? 0
        let h = props[kCGImagePropertyPixelHeight] as? Int ?? 0
        #expect(max(w, h) <= limit)
    }

    private func hasGPSDictionary(in data: Data) -> Bool {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let props  = CGImageSourceCopyPropertiesAtIndex(source, 0, nil)
                           as? [CFString: Any] else { return false }
        return props[kCGImagePropertyGPSDictionary] != nil
    }
}

/// Empty class used solely as an anchor for `Bundle(for:)` so the tests can
/// resolve the test bundle's resources at runtime.
private final class BundleAnchor {}
```

- [ ] **Step 3: Run the tests, verify they fail**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/ImagePipelineTests test
```

Expected: build failure — `ImagePipeline.process` not found.

- [ ] **Step 4: Implement `ImagePipeline.process`**

Append to `apps/ios/Pebbles/Features/PebbleMedia/ImagePipeline.swift`:

```swift
enum ImagePipeline {

    /// Maximum long-edge in pixels for each output.
    private static let originalMaxEdge: CGFloat = 1024
    private static let thumbMaxEdge:    CGFloat = 420

    /// Soft byte caps. We step down JPEG quality up to `qualitySteps` times
    /// trying to fit, then give up with `.tooLargeAfterResize`.
    private static let originalMaxBytes = 1_048_576   // 1 MB
    private static let thumbMaxBytes    =   307_200   // 300 KB
    private static let qualitySteps = 3

    /// Initial JPEG quality per output. Steps down by 0.1 each retry.
    private static let originalStartQuality: CGFloat = 0.85
    private static let thumbStartQuality:    CGFloat = 0.75

    /// Validate, decode, resize, re-encode as two JPEGs without metadata.
    /// Pure: no I/O, no logging, no global state.
    static func process(_ source: Data, uti: String) throws -> ProcessedImage {
        guard ImageFormatValidator.isSupported(uti) else {
            throw ImagePipelineError.unsupportedFormat
        }

        let imageSource = try makeImageSource(source)

        let original = try renderJPEG(
            from: imageSource,
            maxEdge: originalMaxEdge,
            startQuality: originalStartQuality,
            byteCap: originalMaxBytes
        )
        let thumb = try renderJPEG(
            from: imageSource,
            maxEdge: thumbMaxEdge,
            startQuality: thumbStartQuality,
            byteCap: thumbMaxBytes
        )

        return ProcessedImage(original: original, thumb: thumb)
    }

    // MARK: - private helpers

    private static func makeImageSource(_ data: Data) throws -> CGImageSource {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              CGImageSourceGetCount(source) > 0 else {
            throw ImagePipelineError.decodeFailed
        }
        return source
    }

    private static func renderJPEG(
        from source: CGImageSource,
        maxEdge: CGFloat,
        startQuality: CGFloat,
        byteCap: Int
    ) throws -> Data {
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform:   true,    // bake EXIF orientation
            kCGImageSourceShouldCacheImmediately:         true,
            kCGImageSourceThumbnailMaxPixelSize:          maxEdge
        ]
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            throw ImagePipelineError.decodeFailed
        }

        var quality = startQuality
        for _ in 0...qualitySteps {
            let data = try encodeJPEG(cgImage, quality: quality)
            if data.count <= byteCap {
                return data
            }
            quality -= 0.1
            if quality <= 0.1 { break }
        }
        throw ImagePipelineError.tooLargeAfterResize
    }

    private static func encodeJPEG(_ image: CGImage, quality: CGFloat) throws -> Data {
        let buffer = NSMutableData()
        // Note: passing only the lossy-compression key ensures NO EXIF / GPS /
        // TIFF dictionaries are written into the output.
        let options: [CFString: Any] = [
            kCGImageDestinationLossyCompressionQuality: quality
        ]
        guard let destination = CGImageDestinationCreateWithData(
            buffer as CFMutableData,
            UTType.jpeg.identifier as CFString,
            1,
            nil
        ) else {
            throw ImagePipelineError.encodeFailed
        }
        CGImageDestinationAddImage(destination, image, options as CFDictionary)
        guard CGImageDestinationFinalize(destination) else {
            throw ImagePipelineError.encodeFailed
        }
        return buffer as Data
    }
}
```

- [ ] **Step 5: Re-run the tests, verify they pass**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/ImagePipelineTests test
```

Expected: 4 tests pass. If `processesJPEG` fails on the size cap, your fixture is too small/large — pick a different photo until it fits in 1 MB after the pipeline.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/ImagePipeline.swift \
        apps/ios/PebblesTests/ImagePipelineTests.swift \
        apps/ios/PebblesTests/Fixtures/ \
        apps/ios/project.yml
git commit -m "feat(ios): add ImagePipeline (HEIC->JPEG, resize, EXIF strip)"
```

---

## Phase 3 — iOS upload + repository layer

### Task 3.1: Attached snap model

The form needs a small value type representing "the photo the user picked, plus its upload state."

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Models/AttachedSnap.swift`

- [ ] **Step 1: Create the model**

```swift
import Foundation

/// One photo attached to an in-progress pebble, including upload state.
/// Held inside `PebbleDraft.attachedSnap`. Value type — immutable updates.
struct AttachedSnap: Equatable {

    enum UploadState: Equatable {
        case uploading
        case uploaded
        case failed
    }

    /// UUID generated client-side. Becomes both the Storage folder name and
    /// `snaps.id` in Postgres.
    let id: UUID

    /// JPEG bytes for the 420 px thumbnail, kept in memory so the form can
    /// render an instant preview without a Storage round-trip.
    let localThumb: Data

    var state: UploadState

    /// Storage folder shared by both files: `{user_id}/{id}`.
    func storagePrefix(userId: UUID) -> String {
        "\(userId.uuidString)/\(id.uuidString)"
    }

    /// Full Storage path of the 1024 px JPEG.
    func originalPath(userId: UUID) -> String {
        "\(storagePrefix(userId: userId))/original.jpg"
    }

    /// Full Storage path of the 420 px JPEG.
    func thumbPath(userId: UUID) -> String {
        "\(storagePrefix(userId: userId))/thumb.jpg"
    }
}
```

- [ ] **Step 2: Build to confirm it compiles**

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build
```

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Models/AttachedSnap.swift
git commit -m "feat(ios): add AttachedSnap model"
```

### Task 3.2: Snap repository (Storage upload, delete, signed URLs)

This is the only file that touches the Supabase SDK for media. Other code (form, picker) goes through it.

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift`

- [ ] **Step 1: Create the repository**

```swift
import Foundation
import Supabase
import os

/// Storage operations for `pebbles-media`. Stateless except for the injected
/// `SupabaseClient`. Errors propagate; callers decide whether to retry, surface
/// to the user, or fire-and-forget.
@MainActor
struct PebbleSnapRepository {

    private static let bucketId = "pebbles-media"
    private static let signedUrlTTL: Int = 3600    // 1 h
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "snap-repo")

    let client: SupabaseClient

    /// Upload original + thumb in parallel. Returns when both succeed; throws
    /// if either fails. The two `Data` blobs come from `ImagePipeline.process`.
    func uploadProcessed(
        _ processed: ProcessedImage,
        snapId: UUID,
        userId: UUID
    ) async throws {
        let originalPath = "\(userId.uuidString)/\(snapId.uuidString)/original.jpg"
        let thumbPath    = "\(userId.uuidString)/\(snapId.uuidString)/thumb.jpg"
        let bucket = client.storage.from(Self.bucketId)
        let options = FileOptions(contentType: "image/jpeg")

        async let original: Void = bucket.upload(originalPath, data: processed.original, options: options)
        async let thumb:    Void = bucket.upload(thumbPath,    data: processed.thumb,    options: options)
        _ = try await (original, thumb)
    }

    /// Best-effort cleanup of a snap's Storage files. Logs failures but does
    /// not throw — the orphan-sweep follow-up will catch any residue.
    func deleteFiles(snapId: UUID, userId: UUID) async {
        let originalPath = "\(userId.uuidString)/\(snapId.uuidString)/original.jpg"
        let thumbPath    = "\(userId.uuidString)/\(snapId.uuidString)/thumb.jpg"
        do {
            _ = try await client.storage.from(Self.bucketId)
                .remove(paths: [originalPath, thumbPath])
        } catch {
            Self.logger.error(
                "snap delete failed for \(snapId.uuidString, privacy: .public): \(error.localizedDescription, privacy: .private)"
            )
        }
    }

    /// One round-trip for both URLs of a snap. Caller is responsible for
    /// caching by `snapId` until expiry.
    struct SignedURLs {
        let original: URL
        let thumb: URL
    }

    func signedURLs(snapId: UUID, userId: UUID) async throws -> SignedURLs {
        let originalPath = "\(userId.uuidString)/\(snapId.uuidString)/original.jpg"
        let thumbPath    = "\(userId.uuidString)/\(snapId.uuidString)/thumb.jpg"
        let signed = try await client.storage.from(Self.bucketId)
            .createSignedURLs(paths: [originalPath, thumbPath], expiresIn: Self.signedUrlTTL)
        guard signed.count == 2 else { throw URLError(.badServerResponse) }
        return SignedURLs(
            original: signed[0].signedURL,
            thumb:    signed[1].signedURL
        )
    }
}
```

> **Note:** the exact Supabase Swift SDK shape for `upload`/`createSignedURLs`/`remove` may differ slightly from the snippet above between SDK 2.x minor versions. If the build fails, look at the SDK's `StorageFileApi` and adjust the call sites — the parameter names are stable but argument labels (`data:` vs `file:`) have moved. The behavior is unchanged.

- [ ] **Step 2: Build to confirm it compiles**

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build
```

If the build fails on the SDK call signatures, adjust per the note above and rebuild.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift
git commit -m "feat(ios): add PebbleSnapRepository (upload, delete, signed URLs)"
```

---

## Phase 4 — iOS UI integration

### Task 4.1: Photo picker view

A thin SwiftUI wrapper around `PHPickerViewController` configured for single-image selection.

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/PhotoPickerView.swift`

- [ ] **Step 1: Create the picker**

```swift
import PhotosUI
import SwiftUI

/// Presents the system photo picker, configured for a single image, and
/// returns `(Data, UTI)` on selection. The caller wires this to its sheet
/// presentation state.
struct PhotoPickerView: UIViewControllerRepresentable {

    /// Called on the main actor with the selected payload, or `nil` if the
    /// user cancels or selection fails.
    let onPicked: @MainActor (PickedPhoto?) -> Void

    struct PickedPhoto {
        let data: Data
        let uti: String
    }

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration(photoLibrary: .shared())
        config.filter = .images
        config.selectionLimit = 1
        config.preferredAssetRepresentationMode = .current

        let picker = PHPickerViewController(configuration: config)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onPicked: onPicked)
    }

    @MainActor
    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        private let onPicked: @MainActor (PickedPhoto?) -> Void
        private let logger = Logger(subsystem: "app.pbbls.ios", category: "photo-picker")

        init(onPicked: @escaping @MainActor (PickedPhoto?) -> Void) {
            self.onPicked = onPicked
        }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            picker.dismiss(animated: true)

            guard let result = results.first else {
                onPicked(nil)
                return
            }
            let provider = result.itemProvider
            // Pick the first registered type that we support; PHPicker
            // returns multiple type identifiers per asset.
            let candidate = provider.registeredTypeIdentifiers
                .first(where: ImageFormatValidator.isSupported)

            guard let uti = candidate else {
                logger.warning("no supported UTI in picker result; identifiers: \(provider.registeredTypeIdentifiers, privacy: .public)")
                onPicked(nil)
                return
            }

            provider.loadDataRepresentation(forTypeIdentifier: uti) { [weak self] data, error in
                Task { @MainActor in
                    guard let self else { return }
                    guard let data, error == nil else {
                        self.logger.error("picker load failed: \(error?.localizedDescription ?? "no data", privacy: .private)")
                        self.onPicked(nil)
                        return
                    }
                    self.onPicked(PickedPhoto(data: data, uti: uti))
                }
            }
        }
    }
}
```

Add the `Logger` import at the top — Swift compiler will flag this if missing:

```swift
import os
```

- [ ] **Step 2: Add NSPhotoLibraryUsageDescription to Info.plist**

PHPicker does NOT require `NSPhotoLibraryUsageDescription` (it runs in its own process), but for clarity and to silence App Review feedback, add a description. Edit `apps/ios/Pebbles/Resources/Info.plist`:

```xml
<key>NSPhotoLibraryUsageDescription</key>
<string>Attach a photo to a pebble.</string>
```

- [ ] **Step 3: Build to confirm it compiles**

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build
```

Expected: build succeeds.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/PhotoPickerView.swift \
        apps/ios/Pebbles/Resources/Info.plist
git commit -m "feat(ios): add PhotoPickerView (single-image PHPicker wrapper)"
```

### Task 4.2: Attached photo view (the form chip)

Renders the in-progress thumbnail with an upload-state badge and remove/retry affordances.

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/AttachedPhotoView.swift`

- [ ] **Step 1: Create the view**

```swift
import SwiftUI

/// Inline photo "chip" shown inside `PebbleFormView` once the user has picked
/// an image. Displays the local thumbnail, an upload-state badge, and lets
/// the user remove the attachment or retry a failed upload.
struct AttachedPhotoView: View {

    let snap: AttachedSnap
    let onRemove: () -> Void
    let onRetry: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            thumbnail
            VStack(alignment: .leading, spacing: 4) {
                Text("attached_photo.title")
                    .font(.subheadline)
                stateLabel
            }
            Spacer()
            trailingButton
        }
        .padding(8)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private var thumbnail: some View {
        if let uiImage = UIImage(data: snap.localThumb) {
            Image(uiImage: uiImage)
                .resizable()
                .scaledToFill()
                .frame(width: 56, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: 8))
        } else {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.secondary.opacity(0.2))
                .frame(width: 56, height: 56)
        }
    }

    @ViewBuilder
    private var stateLabel: some View {
        switch snap.state {
        case .uploading:
            Label("attached_photo.state.uploading", systemImage: "arrow.up.circle")
                .labelStyle(.titleAndIcon)
                .font(.caption)
                .foregroundStyle(.secondary)
        case .uploaded:
            Label("attached_photo.state.uploaded", systemImage: "checkmark.circle.fill")
                .labelStyle(.titleAndIcon)
                .font(.caption)
                .foregroundStyle(.green)
        case .failed:
            Label("attached_photo.state.failed", systemImage: "exclamationmark.triangle.fill")
                .labelStyle(.titleAndIcon)
                .font(.caption)
                .foregroundStyle(.red)
        }
    }

    @ViewBuilder
    private var trailingButton: some View {
        switch snap.state {
        case .uploading:
            ProgressView()
        case .uploaded:
            Button(role: .destructive, action: onRemove) {
                Image(systemName: "xmark.circle.fill")
                    .foregroundStyle(.secondary)
            }
            .accessibilityLabel("attached_photo.action.remove")
        case .failed:
            HStack(spacing: 8) {
                Button(action: onRetry) {
                    Image(systemName: "arrow.clockwise.circle.fill")
                }
                .accessibilityLabel("attached_photo.action.retry")
                Button(role: .destructive, action: onRemove) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.secondary)
                }
                .accessibilityLabel("attached_photo.action.remove")
            }
        }
    }
}
```

- [ ] **Step 2: Add the localized strings**

Open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode and add entries for these keys, with both `en` and `fr` columns filled. Per `apps/ios/CLAUDE.md`, every entry must leave `New` / `Stale` state before the PR.

| Key | en | fr |
|---|---|---|
| `attached_photo.title` | Photo | Photo |
| `attached_photo.state.uploading` | Uploading… | Envoi en cours… |
| `attached_photo.state.uploaded` | Ready | Prête |
| `attached_photo.state.failed` | Upload failed | Échec de l'envoi |
| `attached_photo.action.remove` | Remove photo | Retirer la photo |
| `attached_photo.action.retry` | Retry | Réessayer |

- [ ] **Step 3: Build to confirm it compiles**

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build
```

Expected: build succeeds. Open `Localizable.xcstrings` and confirm no entry is in `New` state.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/AttachedPhotoView.swift \
        apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): add AttachedPhotoView form chip with upload state"
```

### Task 4.3: Add `attachedSnap` to `PebbleDraft`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift`

- [ ] **Step 1: Add the property**

In `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift`, after the existing `glyphId` line (current line 15), add:

```swift
    var attachedSnap: AttachedSnap?       // optional — set by PhotoPicker, cleared on remove
```

The new field is intentionally not part of `isValid` — a pebble can be saved with no photo. Final draft for context:

```swift
struct PebbleDraft {
    var happenedAt: Date = Date()
    var name: String = ""
    var description: String = ""
    var emotionId: UUID?
    var domainId: UUID?
    var valence: Valence?
    var soulId: UUID?
    var collectionId: UUID?
    var glyphId: UUID?
    var attachedSnap: AttachedSnap?
    var visibility: Visibility = .private

    var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
        && emotionId != nil
        && domainId != nil
        && valence != nil
    }
}
```

The `init(from detail: PebbleDetail)` initializer below stays unchanged for now — edit-pebble photo support is a follow-up issue (V1 is create-only).

- [ ] **Step 2: Build to confirm it compiles**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build
```

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift
git commit -m "feat(ios): add attachedSnap to PebbleDraft"
```

### Task 4.4: Include `snaps` in `PebbleCreatePayload`

The payload's encoder needs to emit a `snaps` array when a snap is attached. The shape mirrors what `create_pebble` reads in Task 1.4: `[{ id, storage_path, sort_order }]`.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift`
- Modify: `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift`

- [ ] **Step 1: Write the failing test**

Append to `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift`, inside the existing `@Suite` struct:

```swift
    @Test("emits snaps array when attachedSnap present")
    func encodesAttachedSnap() throws {
        let snapId = UUID()
        let userId = UUID()
        var draft = makeValidDraft()
        draft.attachedSnap = AttachedSnap(
            id: snapId,
            localThumb: Data(),
            state: .uploaded
        )

        let payload = PebbleCreatePayload(from: draft, userId: userId)
        let json = try encode(payload)

        let snaps = try #require(json["snaps"] as? [[String: Any]])
        try #require(snaps.count == 1)
        #expect(snaps[0]["id"] as? String == snapId.uuidString.lowercased())
        #expect(snaps[0]["storage_path"] as? String == "\(userId.uuidString)/\(snapId.uuidString)")
        #expect(snaps[0]["sort_order"] as? Int == 0)
    }

    @Test("omits snaps key when attachedSnap is nil")
    func omitsSnapsWhenAbsent() throws {
        let draft = makeValidDraft()
        let payload = PebbleCreatePayload(from: draft, userId: UUID())
        let json = try encode(payload)
        #expect(json["snaps"] == nil)
    }
```

- [ ] **Step 2: Run the tests, verify they fail**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/PebbleCreatePayloadEncodingTests test
```

Expected: build failure — `init(from:userId:)` does not exist.

- [ ] **Step 3: Update `PebbleCreatePayload`**

Modify `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift`:

a) Add a `snaps` property and coding key:

```swift
struct PebbleCreatePayload: Encodable {
    // ...existing properties unchanged...
    let snaps: [SnapPayload]?

    struct SnapPayload: Encodable {
        let id: UUID
        let storagePath: String
        let sortOrder: Int

        enum CodingKeys: String, CodingKey {
            case id
            case storagePath = "storage_path"
            case sortOrder   = "sort_order"
        }
    }

    enum CodingKeys: String, CodingKey {
        case name
        case description
        case happenedAt = "happened_at"
        case intensity
        case positiveness
        case visibility
        case emotionId     = "emotion_id"
        case domainIds     = "domain_ids"
        case soulIds       = "soul_ids"
        case collectionIds = "collection_ids"
        case glyphId       = "glyph_id"
        case snaps
    }
```

b) Update the custom `encode(to:)` to emit `snaps` only when non-nil:

```swift
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(name, forKey: .name)
        try container.encode(description, forKey: .description)
        try container.encode(Self.iso8601.string(from: happenedAt), forKey: .happenedAt)
        try container.encode(intensity, forKey: .intensity)
        try container.encode(positiveness, forKey: .positiveness)
        try container.encode(visibility, forKey: .visibility)
        try container.encode(emotionId, forKey: .emotionId)
        try container.encode(domainIds, forKey: .domainIds)
        try container.encode(soulIds, forKey: .soulIds)
        try container.encode(collectionIds, forKey: .collectionIds)
        try container.encode(glyphId, forKey: .glyphId)
        if let snaps {
            try container.encode(snaps, forKey: .snaps)
        }
    }
```

c) Replace the existing convenience initializer with one that takes `userId`:

```swift
extension PebbleCreatePayload {
    /// Build a payload from a validated draft.
    /// `userId` is the current authenticated user's id; it is needed only to
    /// derive the snap's `storage_path` (the RPC re-derives ownership from
    /// `auth.uid()` server-side, so this value is not security-sensitive).
    /// Precondition: `draft.isValid == true`.
    init(from draft: PebbleDraft, userId: UUID) {
        precondition(draft.isValid, "PebbleCreatePayload(from:userId:) called with invalid draft")
        self.name = draft.name.trimmingCharacters(in: .whitespaces)
        let trimmedDescription = draft.description.trimmingCharacters(in: .whitespaces)
        self.description = trimmedDescription.isEmpty ? nil : trimmedDescription
        self.happenedAt = draft.happenedAt
        self.intensity = draft.valence!.intensity
        self.positiveness = draft.valence!.positiveness
        self.visibility = draft.visibility.rawValue
        self.emotionId = draft.emotionId!
        self.domainIds = [draft.domainId!]
        self.soulIds = draft.soulId.map { [$0] } ?? []
        self.collectionIds = draft.collectionId.map { [$0] } ?? []
        self.glyphId = draft.glyphId
        self.snaps = draft.attachedSnap.map { snap in
            [SnapPayload(
                id: snap.id,
                storagePath: snap.storagePrefix(userId: userId),
                sortOrder: 0
            )]
        }
    }
}
```

- [ ] **Step 4: Run all encoding tests, verify they pass**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/PebbleCreatePayloadEncodingTests test
```

Expected: all existing tests still pass, plus the two new ones.

> Existing call sites of `PebbleCreatePayload(from:)` (in `CreatePebbleSheet.save()`) will fail to compile — that's intentional and gets fixed in Task 4.6.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift \
        apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift
git commit -m "feat(ios): include snaps in PebbleCreatePayload when attached"
```

### Task 4.5: Show the picker section in `PebbleFormView`

The form gets a new section with one of two states: a "Add photo" button (no snap attached) or the `AttachedPhotoView` chip (snap attached, in any state).

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`

- [ ] **Step 1: Read the current PebbleFormView to find the right insertion point**

```bash
sed -n '1,40p' apps/ios/Pebbles/Features/Path/PebbleFormView.swift
```

The existing form is a SwiftUI `Form { Section { ... } }` structure. Add a new Section *just before* the visibility/save controls (or wherever feels structurally cleanest — keep it the last content section).

- [ ] **Step 2: Add the photo section**

Add to `PebbleFormView`:

a) New `@Binding` (or `@State` lifted up) for the picker presentation:

```swift
    @State private var isPhotoPickerPresented = false
```

b) New Section in the form body, immediately before the visibility section (or wherever the form pattern dictates):

```swift
            Section {
                if let snap = draft.attachedSnap {
                    AttachedPhotoView(
                        snap: snap,
                        onRemove: { draft.attachedSnap = nil; onSnapRemoved?() },
                        onRetry:  { onSnapRetry?() }
                    )
                } else {
                    Button {
                        isPhotoPickerPresented = true
                    } label: {
                        Label("pebble_form.add_photo", systemImage: "photo.badge.plus")
                    }
                }
            } header: {
                Text("pebble_form.photo_section")
            }
            .sheet(isPresented: $isPhotoPickerPresented) {
                PhotoPickerView { picked in
                    isPhotoPickerPresented = false
                    if let picked { onPhotoPicked?(picked) }
                }
            }
```

c) New optional callbacks on `PebbleFormView`:

```swift
    var onPhotoPicked: ((PhotoPickerView.PickedPhoto) -> Void)? = nil
    var onSnapRetry:   (() -> Void)?                            = nil
    var onSnapRemoved: (() -> Void)?                            = nil
```

These are optional so existing callers (`EditPebbleSheet`) continue to compile without changes — they simply won't see the photo section's interactions wired.

- [ ] **Step 3: Add the localized strings**

In `Localizable.xcstrings`:

| Key | en | fr |
|---|---|---|
| `pebble_form.photo_section` | Photo | Photo |
| `pebble_form.add_photo` | Add a photo | Ajouter une photo |

- [ ] **Step 4: Build to confirm it compiles**

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build
```

Expected: build succeeds. (`CreatePebbleSheet.save()` is still broken from Task 4.4 — fixed next.)

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleFormView.swift \
        apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): show photo section in PebbleFormView"
```

### Task 4.6: Wire upload + create_pebble + compensating delete in `CreatePebbleSheet`

This is where it all comes together: the picker callback kicks off the pipeline and upload, the save action passes the snap into the payload, and a failed RPC fires the compensating Storage delete.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`

- [ ] **Step 1: Add the upload orchestration helpers and rewire `PebbleFormView`**

Replace the entire body of `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` with the following. Annotated with `// NEW:` comments where logic is new vs the current file.

```swift
import Supabase
import SwiftUI
import os

struct CreatePebbleSheet: View {
    let onCreated: (UUID) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var draft = PebbleDraft()
    @State private var emotions: [Emotion] = []
    @State private var domains: [Domain] = []
    @State private var souls: [Soul] = []
    @State private var collections: [PebbleCollection] = []

    @State private var isLoadingReferences = true
    @State private var loadError: String?
    @State private var isSaving = false
    @State private var saveError: String?

    // NEW: in-flight processed bytes kept around so retry doesn't re-pick
    @State private var processedForRetry: ProcessedImage?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "create-pebble")

    private var snapRepo: PebbleSnapRepository {
        PebbleSnapRepository(client: supabase.client)
    }

    private var currentUserId: UUID? {
        supabase.session?.user.id
    }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("New pebble")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") {
                            Task { await cancelAndCleanup() }   // NEW
                        }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        if isSaving {
                            ProgressView()
                        } else {
                            Button("Save") {
                                Task { await save() }
                            }
                            .disabled(!draft.isValid)
                        }
                    }
                }
                .pebblesScreen()
        }
        .task { await loadReferences() }
    }

    @ViewBuilder
    private var content: some View {
        if isLoadingReferences {
            ProgressView()
        } else if let loadError {
            VStack(spacing: 12) {
                Text(loadError).foregroundStyle(.secondary)
                Button("Retry") {
                    Task { await loadReferences() }
                }
            }
        } else {
            PebbleFormView(
                draft: $draft,
                emotions: emotions,
                domains: domains,
                souls: souls,
                collections: collections,
                saveError: saveError,
                onPhotoPicked: { picked in
                    Task { await handlePicked(picked) }
                },
                onSnapRetry: {
                    Task { await retryUpload() }
                },
                onSnapRemoved: {
                    Task { await deleteAttachedSnapFiles() }
                }
            )
        }
    }

    // MARK: - upload orchestration (NEW)

    private func handlePicked(_ picked: PhotoPickerView.PickedPhoto) async {
        guard let userId = currentUserId else {
            logger.error("handlePicked: no current user id")
            return
        }

        // Process synchronously on a background priority Task
        let processed: ProcessedImage
        do {
            processed = try await Task.detached(priority: .userInitiated) {
                try ImagePipeline.process(picked.data, uti: picked.uti)
            }.value
        } catch {
            logger.error("image pipeline failed: \(String(describing: error), privacy: .public)")
            saveError = userMessage(for: error)
            return
        }

        let snapId = UUID()
        draft.attachedSnap = AttachedSnap(id: snapId, localThumb: processed.thumb, state: .uploading)
        processedForRetry = processed

        await uploadCurrentSnap(processed: processed, userId: userId)
    }

    private func retryUpload() async {
        guard let userId = currentUserId,
              let processed = processedForRetry,
              var snap = draft.attachedSnap else { return }
        snap.state = .uploading
        draft.attachedSnap = snap
        await uploadCurrentSnap(processed: processed, userId: userId)
    }

    private func uploadCurrentSnap(processed: ProcessedImage, userId: UUID) async {
        guard var snap = draft.attachedSnap else { return }

        do {
            try await snapRepo.uploadProcessed(processed, snapId: snap.id, userId: userId)
            snap.state = .uploaded
            draft.attachedSnap = snap
        } catch {
            logger.warning("snap upload failed (first attempt): \(error.localizedDescription, privacy: .private)")
            // One auto-retry after 2s
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            do {
                try await snapRepo.uploadProcessed(processed, snapId: snap.id, userId: userId)
                snap.state = .uploaded
                draft.attachedSnap = snap
            } catch {
                logger.error("snap upload failed (retry): \(error.localizedDescription, privacy: .private)")
                snap.state = .failed
                draft.attachedSnap = snap
            }
        }
    }

    /// Removes the attached snap from the draft and best-effort deletes its files.
    private func deleteAttachedSnapFiles() async {
        guard let userId = currentUserId,
              let snap = draft.attachedSnap else { return }
        draft.attachedSnap = nil
        processedForRetry = nil
        await snapRepo.deleteFiles(snapId: snap.id, userId: userId)
    }

    private func cancelAndCleanup() async {
        await deleteAttachedSnapFiles()
        dismiss()
    }

    // MARK: - load references (unchanged)

    private func loadReferences() async {
        isLoadingReferences = true
        loadError = nil
        do {
            async let emotionsQuery: [Emotion] = supabase.client
                .from("emotions").select().order("name").execute().value
            async let domainsQuery: [Domain] = supabase.client
                .from("domains").select().order("name").execute().value
            async let soulsQuery: [Soul] = supabase.client
                .from("souls").select("id, name, glyph_id").order("name").execute().value
            async let collectionsQuery: [PebbleCollection] = supabase.client
                .from("collections").select("id, name").order("name").execute().value

            let (loadedEmotions, loadedDomains, loadedSouls, loadedCollections) =
                try await (emotionsQuery, domainsQuery, soulsQuery, collectionsQuery)

            self.emotions = loadedEmotions
            self.domains = loadedDomains
            self.souls = loadedSouls
            self.collections = loadedCollections
            self.isLoadingReferences = false
        } catch {
            logger.error("reference load failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load the form data."
            self.isLoadingReferences = false
        }
    }

    // MARK: - save

    private func save() async {
        guard draft.isValid else { return }

        // NEW: gate save while a snap upload is still in progress
        if let snap = draft.attachedSnap, snap.state == .uploading {
            logger.notice("save blocked: snap still uploading")
            saveError = "Photo is still uploading."
            return
        }
        if let snap = draft.attachedSnap, snap.state == .failed {
            logger.notice("save blocked: snap upload failed")
            saveError = "Photo upload failed. Retry or remove it."
            return
        }

        guard let userId = currentUserId else {
            logger.error("save: no current user id")
            saveError = "You must be signed in to save."
            return
        }

        isSaving = true
        saveError = nil

        let payload = PebbleCreatePayload(from: draft, userId: userId)
        let requestBody = ComposePebbleRequest(payload: payload)

        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let response: ComposePebbleResponse = try await supabase.client.functions
                .invoke(
                    "compose-pebble",
                    options: FunctionInvokeOptions(body: requestBody),
                    decoder: decoder
                )
            onCreated(response.pebbleId)
            dismiss()
        } catch let functionsError as FunctionsError {
            if let pebbleId = softSuccessPebbleId(from: functionsError) {
                logger.warning("compose-pebble returned 5xx but pebble_id found — advancing to detail sheet")
                onCreated(pebbleId)
                dismiss()
            } else {
                logger.error("compose-pebble failed: \(functionsError.localizedDescription, privacy: .private)")
                await handleSaveFailure(functionsError)
            }
        } catch {
            logger.error("create pebble failed: \(error.localizedDescription, privacy: .private)")
            await handleSaveFailure(error)
        }
    }

    /// Save failed and we cannot recover — fire the compensating snap delete
    /// (if a snap was attached) and surface a user-facing message.
    private func handleSaveFailure(_ error: Error) async {
        if let userId = currentUserId, let snap = draft.attachedSnap {
            await snapRepo.deleteFiles(snapId: snap.id, userId: userId)
        }
        saveError = userMessage(for: error)
        isSaving = false
    }

    private func userMessage(for error: Error) -> String {
        // Try to extract Postgres SQLSTATE from FunctionsError body
        if let fnError = error as? FunctionsError, case let .httpError(_, data) = fnError,
           let body = try? JSONDecoder().decode([String: String].self, from: data) {
            let message = body["error"] ?? body["message"] ?? ""
            if message.contains("media_quota_exceeded") || message.contains("P0001") {
                return "Photo limit reached on this pebble."
            }
        }
        if let pipelineError = error as? ImagePipelineError {
            switch pipelineError {
            case .unsupportedFormat:    return "That image format isn't supported."
            case .decodeFailed:         return "Couldn't read the image."
            case .encodeFailed:         return "Couldn't process the image."
            case .tooLargeAfterResize:  return "That image is too large to attach."
            }
        }
        return "Couldn't save your pebble. Please try again."
    }

    private func softSuccessPebbleId(from error: FunctionsError) -> UUID? {
        guard case let .httpError(_, data) = error, !data.isEmpty else { return nil }
        return try? JSONDecoder().decode(PebbleIdPartial.self, from: data).pebbleId
    }
}

private struct ComposePebbleRequest: Encodable {
    let payload: PebbleCreatePayload
}

private struct PebbleIdPartial: Decodable {
    let pebbleId: UUID
    enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
    }
}

#Preview {
    CreatePebbleSheet(onCreated: { _ in })
        .environment(SupabaseService())
}
```

- [ ] **Step 2: Build to confirm everything compiles**

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build
```

Expected: build succeeds.

- [ ] **Step 3: Run the test suite**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift
git commit -m "feat(ios): wire snap upload, retry, and compensating delete in CreatePebbleSheet"
```

### Task 4.7: Render the snap in `PebbleDetailSheet`

After save, we need to actually show the photo somewhere on the pebble's detail view.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift` (add `snaps` if missing)
- Modify: `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`

- [ ] **Step 1: Inspect the current PebbleDetail and detail sheet**

```bash
sed -n '1,80p' apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift
sed -n '1,80p' apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift
```

Identify whether `PebbleDetail` already decodes a `snaps: [...]` array. If yes, skip Step 2. If no, do Step 2.

- [ ] **Step 2: Add the snap decoding to `PebbleDetail`**

Inside `PebbleDetail`, add:

```swift
    let snaps: [SnapRef]

    struct SnapRef: Decodable, Identifiable, Equatable {
        let id: UUID
        let storagePath: String
        let sortOrder: Int

        enum CodingKeys: String, CodingKey {
            case id
            case storagePath = "storage_path"
            case sortOrder   = "sort_order"
        }
    }
```

Update the SELECT shape used to fetch the detail (search for the existing `from("pebbles_detail_view")` or equivalent and add `snaps(id, storage_path, sort_order)` to the projection).

- [ ] **Step 3: Render the photo in the detail sheet**

In `PebbleDetailSheet`, add a section that, when `detail.snaps.first` exists, fetches signed URLs once and displays the original. Add at an appropriate place in the body:

```swift
            if let snap = detail.snaps.first {
                SnapImageView(snap: snap, ownerId: detail.userId)
                    .environment(supabase)
            }
```

Then create an inline view (in the same file or a new sibling file `Features/PebbleMedia/SnapImageView.swift`):

```swift
import SwiftUI

struct SnapImageView: View {
    let snap: PebbleDetail.SnapRef
    let ownerId: UUID

    @Environment(SupabaseService.self) private var supabase

    @State private var urls: PebbleSnapRepository.SignedURLs?
    @State private var loadError = false

    var body: some View {
        Group {
            if let urls {
                AsyncImage(url: urls.original) { phase in
                    switch phase {
                    case .empty:    ProgressView()
                    case .failure:  fallbackPlaceholder
                    case .success(let image):
                        image.resizable().scaledToFit()
                    @unknown default:
                        fallbackPlaceholder
                    }
                }
            } else if loadError {
                fallbackPlaceholder
            } else {
                ProgressView()
            }
        }
        .task {
            do {
                urls = try await PebbleSnapRepository(client: supabase.client)
                    .signedURLs(snapId: snap.id, userId: ownerId)
            } catch {
                loadError = true
            }
        }
    }

    private var fallbackPlaceholder: some View {
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.secondary.opacity(0.1))
            .frame(height: 200)
            .overlay(Image(systemName: "photo.on.rectangle.angled").foregroundStyle(.secondary))
    }
}
```

If `PebbleDetail` does not currently expose `userId` (it might be on a parent type), pass it through from the caller.

- [ ] **Step 4: Build, run on simulator, verify the photo appears**

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build
```

Run the app, create a pebble with a photo, open the detail sheet — the photo should render.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift \
        apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift \
        apps/ios/Pebbles/Features/PebbleMedia/SnapImageView.swift
git commit -m "feat(ios): render attached snap in PebbleDetailSheet"
```

---

## Phase 5 — Verification & finishing

### Task 5.1: Full build + full test pass

- [ ] **Step 1: Regenerate the Xcode project and build everything**

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' clean build
```

Expected: build succeeds with no warnings introduced by this PR.

- [ ] **Step 2: Run the full test suite**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test
```

Expected: all tests pass, including the new `ImageFormatValidatorTests`, `ImagePipelineTests`, and the appended `PebbleCreatePayloadEncodingTests`.

- [ ] **Step 3: Confirm `Localizable.xcstrings` is clean**

Open in Xcode, sort by State, confirm no row is in `New` or `Stale`. Confirm both `en` and `fr` columns are filled for every row added in Tasks 4.2 and 4.5.

### Task 5.2: Manual smoke test on simulator

These steps confirm end-to-end behaviour the unit tests cannot.

- [ ] **Step 1: HEIC happy path**

1. Open the create-pebble sheet.
2. Pick a HEIC photo from the simulator's library (or from a real device).
3. Confirm: form shows the chip with state "Uploading…", then "Ready".
4. Fill the rest of the form, save.
5. Open the new pebble's detail sheet — the photo renders.

In Supabase Studio, run:

```sql
select * from public.snaps order by created_at desc limit 1;
select name, metadata->>'mimetype' as mime, metadata->>'size' as bytes
  from storage.objects
 where bucket_id = 'pebbles-media'
 order by created_at desc limit 2;
```

Expected: 1 snap row with `storage_path` of the form `{user_id}/{snap_id}`. 2 storage objects, both `image/jpeg`, original ≤ 1 MB, thumb ≤ 300 KB.

- [ ] **Step 2: EXIF stripping (real-device only — simulator photos don't carry EXIF)**

Take a photo on a physical device with location services enabled, attach to a pebble, save. From Supabase Studio:

```sql
select name from storage.objects where bucket_id = 'pebbles-media' order by created_at desc limit 2;
```

Download the original file (via Studio → Storage → file → Download) and run `exiftool` locally:

```bash
exiftool ~/Downloads/original.jpg | grep -iE "gps|location|maker"
```

Expected: no GPS / location / maker-note rows.

- [ ] **Step 3: Quota enforcement**

In Supabase Studio:

```sql
update public.profiles set max_media_per_pebble = 0 where id = auth.uid();
```

In the app, attach a photo, save. Expected: toast says "Photo limit reached on this pebble." No row in `snaps`. Storage files were created (the upload happens before save), then deleted by the compensating delete — verify in Studio:

```sql
select count(*) from storage.objects
 where bucket_id = 'pebbles-media' and name like (auth.uid()::text || '/%');
```

Expected: count returns to its pre-test value (the failed-save snap is gone).

Restore the quota:

```sql
update public.profiles set max_media_per_pebble = 1 where id = auth.uid();
```

- [ ] **Step 4: Cancel-cleanup**

Attach a photo, wait for "Ready", then tap Cancel. From Studio, confirm the two files are gone:

```sql
select name from storage.objects
 where bucket_id = 'pebbles-media' and name like (auth.uid()::text || '/%')
 order by created_at desc limit 5;
```

Expected: the just-uploaded files are not listed (they were removed by `cancelAndCleanup`).

- [ ] **Step 5: Network failure / retry**

Toggle simulator's network off (Settings → Developer → Network Link Conditioner → 100% Loss), attach a photo. Expected: chip shows "Upload failed" after the auto-retry. Tap Retry — it stays failed (still no network). Toggle network on, tap Retry — chip reaches "Ready".

### Task 5.3: File the follow-up issues

Per the spec's §11 follow-ups list and our discussion. Branch is already pushed, so we can file these from the repo root.

- [ ] **Step 1: File the orphan cleanup issue**

```bash
gh issue create \
  --title "[Chore] Sweep orphaned pebbles-media Storage files" \
  --label chore --label core --label db --milestone "M25 · Improved core UX" \
  --body $'Periodic cleanup of files in the `pebbles-media` bucket whose folder name (the second path segment) does not appear as `public.snaps.id`.\n\nTwo orphan sources:\n- Form abandoned after upload but before pebble save.\n- Pebble deleted (cascade kills the row, Storage files are not reachable from Postgres).\n\nFollow-up to #321.'
```

- [ ] **Step 2: File the edit-pebble photo support issue**

```bash
gh issue create \
  --title "[Feat] Support attaching/replacing/removing photos when editing a pebble" \
  --label feat --label ios --label core \
  --body $'V1 only supports attaching a photo during pebble creation (#321). Extend `EditPebbleSheet` and `update_pebble` RPC to support add/replace/remove for existing pebbles. Mirror the create-pebble flow: client-generated `snap_id`, two-file Storage upload, payload-driven update.'
```

- [ ] **Step 3: File the magic-byte hardening issue (optional)**

```bash
gh issue create \
  --title "[Quality] Server-side magic-byte validation for pebbles-media uploads" \
  --label quality --label db \
  --body $'Defense-in-depth on top of the bucket'\''s `image/jpeg` MIME gate. Validate the JPEG SOI marker server-side (Storage trigger or a periodic job) and delete non-conforming files. Follow-up to #321.'
```

### Task 5.4: Open the PR

- [ ] **Step 1: Confirm branch + remote state**

```bash
git status
git log --oneline -15
git diff main...HEAD --stat
```

Confirm: branch is `feat/321-ios-pebbles-pictures`, no uncommitted changes, the diff stat looks consistent with the tasks above.

- [ ] **Step 2: Push (if needed) and open the PR**

```bash
git push
gh pr create \
  --title "feat(core): iOS pebbles pictures" \
  --body "$(cat <<'EOF'
Resolves #321.

## Summary
- Adds the `pebbles-media` private Storage bucket (JPEG-only, 1.5 MB cap) and owner-scoped object policies.
- Adds `profiles.max_media_per_pebble` (default 1) as the per-pebble photo quota.
- Extends `create_pebble` to accept iOS-generated `snaps[].id` and to enforce the quota (`P0001` on overflow).
- iOS: native HEIC/HEIF→JPEG pipeline using `ImageIO`, two outputs per snap (1024 px / 420 px), EXIF stripped at re-encode time.
- iOS: `PhotoPickerView`, `AttachedPhotoView`, snap section in `PebbleFormView`, upload orchestration + compensating delete in `CreatePebbleSheet`, signed-URL render in `PebbleDetailSheet`.

## Follow-ups (filed)
- Orphan cleanup sweep
- Edit-pebble photo support
- Server-side magic-byte validation

## Test plan
- [ ] HEIC happy path: pick → save → photo renders
- [ ] EXIF stripped (verified with `exiftool` on the uploaded original)
- [ ] Quota = 0 → save fails with quota toast, no Storage residue
- [ ] Cancel after upload removes Storage files
- [ ] Network drop during upload → auto-retry → manual retry recovers

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)" \
  --label feat --label core --label ios --label supabase \
  --milestone "M25 · Improved core UX"
```

- [ ] **Step 3: Note the PR URL in the chat**

Return the PR URL printed by `gh pr create` so the human can review.

---

## Self-review notes

- **Spec coverage:** Every spec section (architecture, schema, RLS, bucket+policies, iOS pipeline, error UX, security, follow-ups) maps to one or more tasks above. The schema deviation (`snaps` vs `pebble_media`) is called out in the plan header and changes Tasks 1.4–1.5 only.
- **Type consistency:** `AttachedSnap.id` flows as `snaps[].id` in the payload (Task 4.4) and matches `snaps.id` in the RPC (Task 1.4) and `PebbleDetail.SnapRef.id` in the read path (Task 4.7). Storage path `{user_id}/{snap_id}` is consistent across `AttachedSnap.storagePrefix(userId:)`, the snap repository, the bucket policies, and the smoke-test SQL.
- **No placeholders:** every code-changing step shows the code; every shell step shows the command and expected output; every SQL step is runnable verbatim except where the file body needs the `create_pebble` paste explicitly called out (Task 1.5 Step 2).
- **TDD discipline:** Tasks 2.1, 2.3, 4.4 all follow the failing-test → implement → passing-test cycle. UI/orchestration code (Tasks 4.5–4.7) is integration-only and verified via the manual smoke test in Task 5.2 — consistent with the iOS testing posture in `apps/ios/CLAUDE.md` ("No UI tests for now").
