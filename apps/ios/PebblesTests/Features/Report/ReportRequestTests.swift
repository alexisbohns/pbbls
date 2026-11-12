import Foundation
import Testing
@testable import Pebbles

@Suite("Report request")
struct ReportRequestTests {
    private func json(_ value: some Encodable) throws -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(value)
        return try #require(String(bytes: data, encoding: .utf8))
    }

    private let id = UUID(uuidString: "6F1C2A4E-8A39-4B52-9A0E-3F1D2C4B5A69")!

    @Test func aGlyphGoesThroughReportContent() throws {
        let request = ReportRequest.make(target: .glyph(id), reason: .hate, detail: nil, removeAndBlock: true)
        guard case .content(let params) = request else { Issue.record("expected report_content"); return }
        #expect(request.function == "report_content")
        #expect(try json(params) ==
            #"{"p_reason":"hate","p_target_id":"6F1C2A4E-8A39-4B52-9A0E-3F1D2C4B5A69","p_target_kind":"glyph"}"#)
    }

    @Test func aConnectionCarriesTheBlockFlag() throws {
        let request = ReportRequest.make(
            target: .connection(id: id, name: "Ana"), reason: .harassment, detail: "rude", removeAndBlock: true)
        guard case .person(let params) = request else { Issue.record("expected report_person"); return }
        #expect(request.function == "report_person")
        #expect(try json(params) ==
            #"{"p_connection_id":"6F1C2A4E-8A39-4B52-9A0E-3F1D2C4B5A69","p_detail":"rude","#
            + #""p_reason":"harassment","p_remove_and_block":true}"#)
    }

    @Test func anInviteNeverBlocks() throws {
        let request = ReportRequest.make(
            target: .invite(token: "tok", name: nil), reason: .spam, detail: nil, removeAndBlock: true)
        guard case .person(let params) = request else { Issue.record("expected report_person"); return }
        #expect(try json(params) ==
            #"{"p_invite_token":"tok","p_reason":"spam","p_remove_and_block":false}"#)
    }
}
