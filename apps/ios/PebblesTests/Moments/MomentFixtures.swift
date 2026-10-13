import Foundation
@testable import Pebbles

/// Hand-written payloads shaped from the SQL of the moments split readers
/// (#1047); the live captures live in `MomentFixtures.Captured`. Shared by
/// every decoder suite so a contract change is edited in one place. The
/// Android plan (#1048) decodes the same strings.
enum MomentFixtures {

    static let ownerId = "5f0e1d2c-3b4a-4958-8776-655443322110"
    static let joyId = "22222222-2222-4222-8222-222222222222"
    static let domainId = "33333333-3333-4333-8333-333333333333"
    static let soulId = "44444444-4444-4444-8444-444444444444"
    static let collectionId = "55555555-5555-4555-8555-555555555555"
    static let glyphId = "4759c37c-68a6-46a6-b4fc-046bd0316752"

    /// `rpc("path_moments")` — four rows, newest first. The fourth row (legacy
    /// pebble with `"emotion": null`) is a tolerance probe, not a real payload:
    /// `pebbles.emotion_id` is NOT NULL.
    static let pathMoments = Data("""
    [
      {
        "id": "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a6b",
        "name": "Coffee with Ana",
        "happened_at": "2026-10-12T08:30:00+00:00",
        "created_at": "2026-10-12T08:31:12.517203+00:00",
        "first_snap_path": "5f0e1d2c-3b4a-4958-8776-655443322110/a1b2c3d4-e5f6-4789-8abc-def012345678",
        "pebble": {
          "id": "7e6d5c4b-3a29-4817-8f6e-5d4c3b2a1908",
          "intensity": 2,
          "positiveness": 1,
          "visibility": "private",
          "emotion": {"id": "22222222-2222-4222-8222-222222222222", "slug": "joy", "name": "Joy"},
          "glyph": {
            "id": "4759c37c-68a6-46a6-b4fc-046bd0316752",
            "name": null,
            "strokes": [{"d": "M 40,40 L 160,160", "width": 6}, {"d": "M 160,40 Q 100,100 40,160", "width": 6}],
            "view_box": "0 0 200 200",
            "source": "domain"
          }
        }
      },
      {
        "id": "1c8d4a6f-3e52-4b9f-a07c-2d3e4f5a6b7c",
        "name": null,
        "happened_at": "2026-10-11T19:04:51+00:00",
        "created_at": "2026-10-11T19:05:02.004+00:00",
        "first_snap_path": "5f0e1d2c-3b4a-4958-8776-655443322110/b2c3d4e5-f6a7-4890-9bcd-ef0123456789",
        "pebble": null
      },
      {
        "id": "2d9e5b70-4f63-4ca0-b18d-3e4f5a6b7c8d",
        "name": "Quiet evening",
        "happened_at": "2026-09-30T21:15:07.123456+00:00",
        "created_at": "2026-09-30T21:15:07.123456+00:00",
        "first_snap_path": null,
        "pebble": null
      },
      {
        "id": "3eaf6c81-5074-4db1-82e9-4f5a6b7c8d9e",
        "name": "Shipped the thing",
        "happened_at": "2026-09-29T16:00:00+00:00",
        "created_at": "2026-09-29T16:02:44.9+00:00",
        "first_snap_path": null,
        "pebble": {
          "id": "3eaf6c81-5074-4db1-82e9-4f5a6b7c8d9e",
          "intensity": 3,
          "positiveness": -1,
          "visibility": "secret",
          "emotion": null,
          "glyph": null
        }
      }
    ]
    """.utf8)

    /// `from("v_moments_full").select(MomentsService.detailColumns).eq("id", …).single()`
    /// for the owner's completed moment: every relation populated, a glyph
    /// inherited from a collection, one card (ignored by iOS).
    static let completedMoment = Data("""
    {
      "id": "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a6b",
      "user_id": "5f0e1d2c-3b4a-4958-8776-655443322110",
      "name": "Coffee with Ana",
      "description": "First rain of autumn.",
      "happened_at": "2026-10-12T08:30:00+00:00",
      "hidden_at": null,
      "created_at": "2026-10-12T08:31:12.517203+00:00",
      "updated_at": "2026-10-12T09:02:40.004+00:00",
      "snaps": [
        {"id": "a1b2c3d4-e5f6-4789-8abc-def012345678", "storage_path": "5f0e1d2c-3b4a-4958-8776-655443322110/a1b2c3d4-e5f6-4789-8abc-def012345678", "sort_order": 0}
      ],
      "pebble": {
        "id": "7e6d5c4b-3a29-4817-8f6e-5d4c3b2a1908",
        "emotion_id": "22222222-2222-4222-8222-222222222222",
        "intensity": 2,
        "positiveness": 1,
        "visibility": "private",
        "created_at": "2026-10-12T08:40:03.88+00:00",
        "updated_at": "2026-10-12T09:02:40.004+00:00",
        "emotion": {"id": "22222222-2222-4222-8222-222222222222", "slug": "joy", "name": "Joy", "color": "#FFD166"},
        "glyph": {
          "id": "66666666-6666-4666-8666-666666666666",
          "name": "Autumn",
          "strokes": [{"d": "M 20,100 Q 100,20 180,100", "width": 6}],
          "view_box": "0 0 200 200",
          "source": "collection"
        },
        "cards": [
          {"id": "77777777-7777-4777-8777-777777777777", "species_id": "88888888-8888-4888-8888-888888888888", "value": "It rained.", "sort_order": 0}
        ],
        "souls": [{"id": "44444444-4444-4444-8444-444444444444", "name": "Ana"}],
        "domains": [{"id": "33333333-3333-4333-8333-333333333333", "slug": "zoe", "name": "Work", "label": "Your job and your craft"}],
        "collections": [
          {"id": "55555555-5555-4555-8555-555555555555", "name": "Autumn", "glyph_id": "66666666-6666-4666-8666-666666666666"},
          {"id": "99999999-9999-4999-8999-999999999999", "name": "Wins", "glyph_id": null}
        ]
      }
    }
    """.utf8)

    /// The owner's photo-only moment, not completed: explicit null name and
    /// description, one snap, null pebble.
    static let photoOnlyMoment = Data("""
    {
      "id": "1c8d4a6f-3e52-4b9f-a07c-2d3e4f5a6b7c",
      "user_id": "5f0e1d2c-3b4a-4958-8776-655443322110",
      "name": null,
      "description": null,
      "happened_at": "2026-10-11T19:04:51+00:00",
      "hidden_at": null,
      "created_at": "2026-10-11T19:05:02.004+00:00",
      "updated_at": "2026-10-11T19:05:02.004+00:00",
      "snaps": [
        {"id": "b2c3d4e5-f6a7-4890-9bcd-ef0123456789", "storage_path": "5f0e1d2c-3b4a-4958-8776-655443322110/b2c3d4e5-f6a7-4890-9bcd-ef0123456789", "sort_order": 0}
      ],
      "pebble": null
    }
    """.utf8)

    /// A connection's public moment seen by another user: `pebble` is null
    /// (only the caller's own pebble nests) and `snaps` is empty (owner-only).
    static let connectionMoment = Data("""
    {
      "id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      "user_id": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
      "name": "Ana's birthday",
      "description": null,
      "happened_at": "2026-10-03T18:00:00+00:00",
      "hidden_at": null,
      "created_at": "2026-10-03T18:20:11.25+00:00",
      "updated_at": "2026-10-03T18:20:11.25+00:00",
      "snaps": [],
      "pebble": null
    }
    """.utf8)

    /// A pre-split pebble: moment id = pebble id (D9), fractional
    /// happened_at, no domain, no glyph, empty relations.
    static let legacyMoment = Data("""
    {
      "id": "3eaf6c81-5074-4db1-82e9-4f5a6b7c8d9e",
      "user_id": "5f0e1d2c-3b4a-4958-8776-655443322110",
      "name": "Shipped the thing",
      "description": "",
      "happened_at": "2026-09-29T16:00:00.371+00:00",
      "hidden_at": null,
      "created_at": "2026-09-29T16:02:44.9+00:00",
      "updated_at": "2026-10-12T09:00:00+00:00",
      "snaps": [],
      "pebble": {
        "id": "3eaf6c81-5074-4db1-82e9-4f5a6b7c8d9e",
        "emotion_id": "22222222-2222-4222-8222-222222222222",
        "intensity": 3,
        "positiveness": -1,
        "visibility": "secret",
        "created_at": "2026-09-29T16:02:44.9+00:00",
        "updated_at": "2026-09-29T16:02:44.9+00:00",
        "emotion": {"id": "22222222-2222-4222-8222-222222222222", "slug": "joy", "name": "Joy", "color": "#FFD166"},
        "glyph": null,
        "cards": [],
        "souls": [],
        "domains": [],
        "collections": []
      }
    }
    """.utf8)

    static func uuid(_ string: String) -> UUID { UUID(uuidString: string)! }

    static func date(_ iso: String) -> Date { ISO8601Flexible.parse(iso)! }
}
