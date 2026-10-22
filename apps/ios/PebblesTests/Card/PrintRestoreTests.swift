import Foundation
import Testing
@testable import Pebbles

/// #1146 D6–D7: `printingFor` is set when a print starts and cleared only by the
/// frame where it ends; a restore that still finds it cuts the print short.
@Suite("Print restore")
@MainActor
struct PrintRestoreTests {
    private let id = UUID(uuidString: "1c8d4a6f-3e52-4b9f-a07c-2d3e4f5a6b7c") ?? UUID()

    @Test("a print under way is saved with the card, verso up")
    func saved() {
        let host = HeldCardHostModel()
        host.open(HeldCardRequest(momentId: id, origin: .path))
        host.startPrinting(id)
        #expect(host.printingFor == id)
        #expect(host.snapshot == HeldCardSnapshot(momentId: id, face: .verso, printingFor: id))
    }

    @Test("only the print's end clears it; a landing does not")
    func onlyTheEnd() {
        let host = HeldCardHostModel()
        host.open(HeldCardRequest(momentId: id, origin: .path))
        host.startPrinting(id)
        host.land(.recto)
        #expect(host.printingFor == id)
        host.endPrinting()
        #expect(host.printingFor == nil)
    }

    @Test("a restore with a print under way is cut short on load")
    func restore() {
        let host = HeldCardHostModel()
        host.restore(HeldCardSnapshot(momentId: id, face: .verso, printingFor: id))
        #expect(host.request?.face == .verso)
        #expect(PrintStart.cutsShort(printingFor: host.printingFor, momentId: id, hasPebble: true))
    }

    @Test("cutting short shows the completion and leaves the card verso up")
    func cutShort() async throws {
        let model = HeldCardModel(momentId: id, origin: .you, onChanged: {})
        let bare = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.photoOnlyMoment)
        let completed = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        await model.load { bare }
        await model.load { completed }
        model.cutShort()
        #expect(model.completion == nil && model.printedOver && !model.announcing)
    }
}
