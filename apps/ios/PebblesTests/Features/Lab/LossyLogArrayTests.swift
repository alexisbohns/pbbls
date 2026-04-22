import Foundation
import Testing
@testable import Pebbles

/// Tests the lossy Decodable wrapper that `LogsService` uses so one bad row
/// in a `v_logs_with_counts` response can never break an entire feed.
@Suite("LossyLogArray")
struct LossyLogArrayTests {

    // MARK: - Fixtures

    /// A PostgREST decoder configured the same way supabase-swift configures
    /// its internal one — custom date strategy that accepts ISO8601 with or
    /// without fractional seconds. Keeps these tests decoupled from the SDK
    /// while matching what production decodes against.
    private func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let string = try container.decode(String.self)
            let withFractional: Set<Character> = [".", ","]
            let formatterWith = ISO8601DateFormatter()
            formatterWith.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            let formatterWithout = ISO8601DateFormatter()
            formatterWithout.formatOptions = [.withInternetDateTime]
            if string.contains(where: { withFractional.contains($0) }),
               let date = formatterWith.date(from: string) {
                return date
            }
            if let date = formatterWithout.date(from: string) { return date }
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Invalid ISO8601: \(string)"
            )
        }
        return decoder
    }

    /// A well-formed `v_logs_with_counts` row. All required Log fields present.
    private func validRow(
        id: String = "11111111-1111-1111-1111-111111111111",
        titleEn: String = "Shipped it"
    ) -> String {
        """
        {
          "id": "\(id)",
          "species": "feature",
          "platform": "ios",
          "status": "shipped",
          "title_en": "\(titleEn)",
          "title_fr": null,
          "summary_en": "One line.",
          "summary_fr": null,
          "body_md_en": null,
          "body_md_fr": null,
          "cover_image_path": null,
          "external_url": null,
          "published": true,
          "published_at": "2026-04-20T12:00:00Z",
          "created_at": "2026-04-20T12:00:00Z",
          "reaction_count": 0
        }
        """
    }

    // MARK: - Tests

    @Test("decodes an all-valid array unchanged")
    func decodesAllValid() throws {
        let json = Data("""
        [\(validRow(id: "11111111-1111-1111-1111-111111111111", titleEn: "A")),
         \(validRow(id: "22222222-2222-2222-2222-222222222222", titleEn: "B"))]
        """.utf8)

        let wrapper = try makeDecoder().decode(LossyLogArray.self, from: json)

        #expect(wrapper.logs.count == 2)
        #expect(wrapper.logs.map(\.titleEn) == ["A", "B"])
    }

    @Test("empty array decodes to empty logs")
    func decodesEmptyArray() throws {
        let json = Data("[]".utf8)
        let wrapper = try makeDecoder().decode(LossyLogArray.self, from: json)
        #expect(wrapper.logs.isEmpty)
    }

    @Test("single bad row is skipped; surrounding rows still decode")
    func skipsSingleBadRow() throws {
        // Middle row has `status: "retired"` which is not in LogStatus's
        // allowed raw values, so it fails Log's decode. Wrapper must skip
        // it and decode the siblings.
        let bad = """
        {
          "id": "33333333-3333-3333-3333-333333333333",
          "species": "feature",
          "platform": "ios",
          "status": "retired",
          "title_en": "Bad",
          "title_fr": null,
          "summary_en": "…",
          "summary_fr": null,
          "body_md_en": null,
          "body_md_fr": null,
          "cover_image_path": null,
          "external_url": null,
          "published": true,
          "published_at": "2026-04-20T12:00:00Z",
          "created_at": "2026-04-20T12:00:00Z",
          "reaction_count": 0
        }
        """

        let json = Data("""
        [\(validRow(id: "11111111-1111-1111-1111-111111111111", titleEn: "A")),
         \(bad),
         \(validRow(id: "22222222-2222-2222-2222-222222222222", titleEn: "B"))]
        """.utf8)

        let wrapper = try makeDecoder().decode(LossyLogArray.self, from: json)

        #expect(wrapper.logs.count == 2)
        #expect(wrapper.logs.map(\.titleEn) == ["A", "B"])
    }

    @Test("multiple bad rows in a row are all skipped")
    func skipsConsecutiveBadRows() throws {
        // Two adjacent bad rows exercise the AnyDecodable swallow — if the
        // container didn't advance between bad elements, the second bad
        // row would be re-read and the loop would spin.
        let bad1 = """
        {
          "id": "not-a-uuid",
          "species": "feature",
          "platform": "ios",
          "status": "shipped",
          "title_en": "x",
          "summary_en": "x",
          "published": true,
          "created_at": "2026-04-20T12:00:00Z",
          "reaction_count": 0
        }
        """
        let bad2 = """
        {
          "id": "55555555-5555-5555-5555-555555555555",
          "species": "mystery",
          "platform": "ios",
          "status": "shipped",
          "title_en": "x",
          "summary_en": "x",
          "published": true,
          "created_at": "2026-04-20T12:00:00Z",
          "reaction_count": 0
        }
        """

        let json = Data("""
        [\(bad1),
         \(bad2),
         \(validRow(id: "66666666-6666-6666-6666-666666666666", titleEn: "last"))]
        """.utf8)

        let wrapper = try makeDecoder().decode(LossyLogArray.self, from: json)

        #expect(wrapper.logs.count == 1)
        #expect(wrapper.logs.first?.titleEn == "last")
    }

    @Test("all rows bad yields empty logs, not a thrown error")
    func allBadRowsYieldEmpty() throws {
        let bad = """
        {
          "id": "not-a-uuid",
          "species": "feature",
          "platform": "ios",
          "status": "shipped",
          "title_en": "x",
          "summary_en": "x",
          "published": true,
          "created_at": "2026-04-20T12:00:00Z",
          "reaction_count": 0
        }
        """
        let json = Data("[\(bad),\(bad)]".utf8)

        let wrapper = try makeDecoder().decode(LossyLogArray.self, from: json)

        #expect(wrapper.logs.isEmpty)
    }

    @Test("non-array top-level JSON still throws")
    func nonArrayThrows() {
        // A bare object at the top is malformed for this endpoint. The
        // wrapper intentionally does NOT swallow this — whole-response
        // failures should still propagate so LogListView can show its
        // "Couldn't load the list." error.
        let json = Data("""
        { "error": "oops" }
        """.utf8)

        #expect(throws: DecodingError.self) {
            try self.makeDecoder().decode(LossyLogArray.self, from: json)
        }
    }
}
