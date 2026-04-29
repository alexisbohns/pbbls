-- Drop the render_manifest column on pebbles.
--
-- Animation timing has moved off the server entirely: the iOS client owns
-- a versioned phase-timing table keyed by pebbles.render_version, and uses
-- the composed render_svg as the only source of stroke geometry. The
-- render_manifest column is no longer written or read by any client.

alter table public.pebbles
  drop column if exists render_manifest;
