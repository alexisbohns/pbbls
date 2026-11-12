import Foundation
import Testing
@testable import Pebbles

@Suite("Report models")
struct ReportModelsTests {
    /// Read through `#filePath`, like the other suites' fixtures, never from the bundle.
    private static let fixtures = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()   // Report
        .deletingLastPathComponent()   // Features
        .deletingLastPathComponent()   // PebblesTests
        .appendingPathComponent("Fixtures")

    @Test func reasonRawValuesAreTheServerSlugs() {
        #expect(ReportReason.allCases.map(\.rawValue) == [
            "harassment", "hate", "impersonation", "sexual", "violence",
            "self_harm", "illegal", "spam", "other"
        ])
    }

    @Test func receiptDecodesTheHarnessPayload() throws {
        let data = try Data(contentsOf: Self.fixtures.appendingPathComponent("report-person-receipt.json"))
        let receipt = try JSONDecoder().decode(ReportReceipt.self, from: data)
        #expect(receipt.id == UUID(uuidString: "8ce2b8e9-1a29-456c-86a1-eb0d3b27c10a"))
        #expect(receipt.status == "open")
        #expect(receipt.createdAt == Date(timeIntervalSince1970: 1_794_516_883))   // 2026-11-12T20:54:43Z
    }

    @Test func receiptAcceptsFractionalSeconds() throws {
        let json = #"{"id":"6f1c2a4e-8a39-4b52-9a0e-3f1d2c4b5a69","status":"open","#
            + #""created_at":"2026-11-12T09:00:00.123456+00:00"}"#
        let receipt = try JSONDecoder().decode(ReportReceipt.self, from: Data(json.utf8))
        #expect(receipt.createdAt == ISO8601Flexible.parse("2026-11-12T09:00:00.123456+00:00"))
    }

    @Test func receiptRejectsANonDate() {
        let json = #"{"id":"6f1c2a4e-8a39-4b52-9a0e-3f1d2c4b5a69","status":"open","created_at":"yesterday"}"#
        #expect(throws: DecodingError.self) {
            try JSONDecoder().decode(ReportReceipt.self, from: Data(json.utf8))
        }
    }

    @Test(arguments: [
        ("not_found", ReportError.notFound),
        ("P0001: cannot_report_own", ReportError.ownContent)
    ])
    func slugsMapToErrors(message: String, expected: ReportError) {
        let error = NSError(domain: "test", code: 0, userInfo: [NSLocalizedDescriptionKey: message])
        #expect(ReportError.from(error) == expected)
    }

    @Test func anythingElseIsOther() {
        let error = NSError(domain: "test", code: 0, userInfo: [NSLocalizedDescriptionKey: "timeout"])
        #expect(ReportError.from(error) == .other("timeout"))
    }

    @Test func onlyAConnectionOffersTheBlock() {
        #expect(ReportTarget.connection(id: UUID(), name: "Ana").offersBlock)
        #expect(!ReportTarget.invite(token: "t", name: "Ana").offersBlock)
        #expect(!ReportTarget.glyph(UUID()).offersBlock)
    }

    @Test func titlesNameThePersonWhenKnown() {
        #expect(ReportTarget.connection(id: UUID(), name: "Ana").title == "Report Ana")
        #expect(ReportTarget.invite(token: "t", name: nil).title == "Report this person")
        #expect(ReportTarget.connection(id: UUID(), name: "").title == "Report this person")
        #expect(ReportTarget.glyph(UUID()).title == "Report this glyph")
    }
}
