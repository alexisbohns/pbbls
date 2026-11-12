import Foundation
import Testing
@testable import Pebbles

@MainActor
@Suite("Report sheet model")
struct ReportSheetModelTests {
    private let receipt = ReportReceipt(
        id: UUID(),
        status: "open",
        createdAt: Date(timeIntervalSince1970: 1_800_000_000)
    )

    @Test func cannotSendWithoutAReason() {
        let model = ReportSheetModel(target: .glyph(UUID()))
        #expect(!model.canSend)
        model.reason = .spam
        #expect(model.canSend)
    }

    @Test func aSuccessfulSendLandsInSent() async {
        let model = ReportSheetModel(target: .glyph(UUID()))
        model.reason = .hate
        await model.submit { _, _, _, _ in receipt }
        #expect(model.phase == .sent)
        #expect(!model.canSend)
        #expect(!model.didBlock)
    }

    @Test func aDuplicateIsStillASuccess() async {
        let model = ReportSheetModel(target: .invite(token: "t", name: nil))
        model.reason = .spam
        await model.submit { _, _, _, _ in receipt }
        let again = ReportSheetModel(target: .invite(token: "t", name: nil))
        again.reason = .spam
        await again.submit { _, _, _, _ in receipt }   // the server returns the standing report
        #expect(again.phase == .sent)
    }

    @Test func passesTrimmedDetailOrNil() async {
        let model = ReportSheetModel(target: .glyph(UUID()))
        model.reason = .other
        model.detail = "   "
        var sent: String?? = .none
        await model.submit { _, _, detail, _ in sent = .some(detail); return receipt }
        #expect(sent == .some(nil))

        let second = ReportSheetModel(target: .glyph(UUID()))
        second.reason = .other
        second.detail = "  look at the name  "
        await second.submit { _, _, detail, _ in sent = .some(detail); return receipt }
        #expect(sent == .some("look at the name"))
    }

    @Test func theBlockFlagIsSentOnlyForAConnection() async {
        let connection = ReportSheetModel(target: .connection(id: UUID(), name: "Ana"))
        connection.reason = .harassment
        #expect(connection.removeAndBlock)   // on by default
        var flag: Bool?
        await connection.submit { _, _, _, block in flag = block; return receipt }
        #expect(flag == true)
        #expect(connection.didBlock)

        let invite = ReportSheetModel(target: .invite(token: "t", name: "Ana"))
        invite.reason = .harassment
        await invite.submit { _, _, _, block in flag = block; return receipt }
        #expect(flag == false)
        #expect(!invite.didBlock)
    }

    @Test func turningTheToggleOffDoesNotBlock() async {
        let model = ReportSheetModel(target: .connection(id: UUID(), name: nil))
        model.reason = .spam
        model.removeAndBlock = false
        var flag: Bool?
        await model.submit { _, _, _, block in flag = block; return receipt }
        #expect(flag == false)
        #expect(!model.didBlock)
    }

    @Test func aFailureShowsItsMessageAndAllowsRetry() async {
        let model = ReportSheetModel(target: .glyph(UUID()))
        model.reason = .spam
        await model.submit { _, _, _, _ in
            throw NSError(domain: "t", code: 0, userInfo: [NSLocalizedDescriptionKey: "not_found"])
        }
        #expect(model.phase == .failed(ReportError.notFound.message))
        #expect(model.canSend)
    }

    @Test func detailIsCappedAtTheServerLimit() {
        let model = ReportSheetModel(target: .glyph(UUID()))
        model.detail = String(repeating: "x", count: 1_200)
        model.clampDetail()
        #expect(model.detail.unicodeScalars.count == ReportSheetModel.detailLimit)
    }

    @Test func detailIsMeasuredInCodePointsLikePostgres() {
        // One family emoji is one grapheme but five scalars: 200 of them are exactly 1,000 scalars.
        let family = "👨‍👩‍👧"
        let model = ReportSheetModel(target: .glyph(UUID()))
        model.detail = String(repeating: family, count: 200)
        #expect(model.detail.count == 200)
        #expect(model.detail.unicodeScalars.count == ReportSheetModel.detailLimit)
        model.clampDetail()
        #expect(model.detail == String(repeating: family, count: 200))

        model.detail += family
        model.clampDetail()
        #expect(model.detail.unicodeScalars.count <= ReportSheetModel.detailLimit)
    }
}
