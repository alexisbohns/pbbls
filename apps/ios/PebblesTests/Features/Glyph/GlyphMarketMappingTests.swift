import Foundation
import Testing
@testable import Pebbles

@Suite("Glyph market mapping")
struct GlyphMarketMappingTests {

    @Test("mine price picks the approved+listed submission, else 0")
    func minePrice() {
        let approved = MineGlyphRow(
            id: UUID(), userId: UUID(), name: "Molly",
            strokes: [], viewBox: "0 0 200 200", createdAt: "2026-01-01T00:00:00+00:00",
            submissions: [SubmissionPriceRow(price: 12, status: "approved", listed: true)]
        )
        #expect(approved.listedPrice == 12)

        let pendingOnly = MineGlyphRow(
            id: UUID(), userId: UUID(), name: "Sauron",
            strokes: [], viewBox: "0 0 200 200", createdAt: "2026-01-01T00:00:00+00:00",
            submissions: [SubmissionPriceRow(price: 30, status: "pending", listed: true)]
        )
        #expect(pendingOnly.listedPrice == 0)

        let delisted = MineGlyphRow(
            id: UUID(), userId: UUID(), name: "Gollum",
            strokes: [], viewBox: "0 0 200 200", createdAt: "2026-01-01T00:00:00+00:00",
            submissions: [SubmissionPriceRow(price: 9, status: "approved", listed: false)]
        )
        #expect(delisted.listedPrice == 0)

        let none = MineGlyphRow(
            id: UUID(), userId: UUID(), name: nil,
            strokes: [], viewBox: "0 0 200 200", createdAt: "2026-01-01T00:00:00+00:00",
            submissions: []
        )
        #expect(none.listedPrice == 0)
    }

    @Test("market row maps to an owned-aware grid item")
    func marketMapping() {
        let id = UUID()
        let row = MarketGlyphRow(
            id: id, userId: UUID(), name: "Creature",
            strokes: [GlyphStroke(d: "M0,0 L1,1", width: 6)],
            viewBox: "0 0 200 200", createdAt: "2026-07-01T12:00:00+00:00",
            price: 7, owned: true
        )
        let item = row.toGridItem()
        #expect(item.id == id)
        #expect(item.price == 7)
        #expect(item.owned == true)
        #expect(item.createdAt != nil)
        #expect(item.acquiredAt == nil)
    }

    @Test("entitlement row maps to an owned grid item with acquired date")
    func entitlementMapping() {
        let gid = UUID()
        let row = EntitlementRow(
            pricePaid: 25, acquiredAt: "2026-07-01T09:30:00.123456+00:00",
            glyph: EntitlementGlyphRow(
                id: gid, userId: UUID(), name: "Creature",
                strokes: [], viewBox: "0 0 200 200", createdAt: "2026-06-01T00:00:00+00:00"
            )
        )
        let item = row.toGridItem()
        #expect(item.glyph.id == gid)
        #expect(item.price == 25)
        #expect(item.owned == true)
        #expect(item.acquiredAt != nil)
    }

    @Test("timestamp parser handles Z, offset, and microsecond precision")
    func timestampParsing() {
        #expect(GlyphTimestamp.parse("2026-07-01T12:00:00Z") != nil)
        #expect(GlyphTimestamp.parse("2026-07-01T12:00:00+00:00") != nil)
        #expect(GlyphTimestamp.parse("2026-07-01T12:00:00.123+00:00") != nil)
        #expect(GlyphTimestamp.parse("2026-07-01T12:00:00.123456+00:00") != nil)
        // Postgres trims trailing zeros, so 1–2 fractional digits reach us; must still parse.
        #expect(GlyphTimestamp.parse("2026-07-01T12:00:00.5+00:00") != nil)
        #expect(GlyphTimestamp.parse("2026-07-01T12:00:00.12Z") != nil)
        #expect(GlyphTimestamp.parse(nil) == nil)
        #expect(GlyphTimestamp.parse("not-a-date") == nil)
    }
}
