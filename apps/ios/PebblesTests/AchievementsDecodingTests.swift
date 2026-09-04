import Foundation
import Testing
@testable import Pebbles

/// Decoding contracts for the achievements rows (M48). Fixtures are foreign
/// payloads in the shapes PostgREST actually emits — including the timestamp
/// precision variants that motivated `ISO8601Flexible` (#651) — never
/// round-trips of this surface's own encoder.
@Suite("Achievements decoding")
struct AchievementsDecodingTests {

    @Test("catalog row decodes with nulls and overrides")
    func catalogRow() throws {
        let json = """
        [{
          "id": "044be862-0a82-1cc4-5f47-02552c91eb6e",
          "slug": "pebble-count-10",
          "family": "pebble_count",
          "threshold": 10,
          "emotion_id": null,
          "domain_id": null,
          "sort_order": 110,
          "glyph_id": null,
          "karma_reward": 0,
          "is_active": true,
          "title_en": null,
          "title_fr": null,
          "description_en": null,
          "description_fr": null
        },
        {
          "id": "1f0e2d3c-4b5a-4978-8695-a4b3c2d1e0f9",
          "slug": "emotion-first-joyful",
          "family": "emotion_first",
          "threshold": null,
          "emotion_id": "2a1b3c4d-5e6f-4a8b-9c0d-1e2f3a4b5c6d",
          "domain_id": null,
          "sort_order": 200,
          "glyph_id": null,
          "karma_reward": 5,
          "is_active": true,
          "title_en": "Custom title",
          "title_fr": "Titre perso",
          "description_en": null,
          "description_fr": null
        }]
        """.data(using: .utf8)!

        let rows = try JSONDecoder().decode([AchievementRecord].self, from: json)
        #expect(rows.count == 2)
        #expect(rows[0].slug == "pebble-count-10")
        #expect(rows[0].threshold == 10)
        #expect(rows[0].emotionId == nil)
        #expect(rows[0].titleEn == nil)
        #expect(rows[1].karmaReward == 5)
        #expect(rows[1].titleFr == "Titre perso")
        #expect(rows[1].emotionId != nil)
    }

    @Test("unlock row decodes Postgres microsecond timestamps", arguments: [
        "2026-07-30T09:15:42.123456+00:00",  // PostgREST microseconds
        "2026-07-30T09:15:42.123Z",          // web milliseconds
        "2026-07-30T09:15:42Z",              // whole seconds
    ])
    func unlockRowTimestampPrecisions(raw: String) throws {
        let json = """
        [{"achievement_id": "044be862-0a82-1cc4-5f47-02552c91eb6e", "unlocked_at": "\(raw)"}]
        """.data(using: .utf8)!

        let rows = try JSONDecoder().decode([AchievementUnlockRecord].self, from: json)
        #expect(rows.count == 1)
        let seconds = rows[0].unlockedAt.timeIntervalSince1970
        // All three variants land within the same second.
        #expect(abs(seconds - 1785402942) < 1)
    }

    @Test("check result decodes slug and granted karma")
    func checkResult() throws {
        let json = """
        [{"slug": "first-soul", "karma_granted": 7}, {"slug": "pebble-count-1", "karma_granted": 0}]
        """.data(using: .utf8)!

        let rows = try JSONDecoder().decode([AchievementCheckResult].self, from: json)
        #expect(rows.count == 2)
        #expect(rows[0].slug == "first-soul")
        #expect(rows[0].karmaGranted == 7)
        #expect(rows[1].karmaGranted == 0)
    }

    @Test("unknown family degrades to slug title and trophy symbol")
    func unknownFamilyTolerance() throws {
        let json = """
        [{
          "id": "3c2b1a0d-9e8f-4765-8493-a2b1c0d9e8f7",
          "slug": "mystery-badge",
          "family": "brand_new_family",
          "threshold": null,
          "emotion_id": null,
          "domain_id": null,
          "sort_order": 900,
          "glyph_id": null,
          "karma_reward": 0,
          "is_active": true,
          "title_en": null,
          "title_fr": null,
          "description_en": null,
          "description_fr": null
        }]
        """.data(using: .utf8)!

        let rows = try JSONDecoder().decode([AchievementRecord].self, from: json)
        let record = try #require(rows.first)
        #expect(record.localizedTitle(emotionName: nil, domainName: nil) == "mystery-badge")
        #expect(record.localizedDescription(emotionName: nil, domainName: nil) == nil)
        #expect(record.familySymbol == "trophy")
    }
}
