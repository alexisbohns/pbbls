import CoreGraphics
import Foundation
import Observation
import Testing
@testable import Pebbles

/// #1130 §Frame registry, stack E's canonical shape: frames are reported and
/// forgotten without invalidating anyone; `hidden` is observed.
@Suite("CardFrameRegistry")
@MainActor
struct CardFrameRegistryTests {
    final class Flag: @unchecked Sendable {
        var raised = false
    }

    private let id = UUID(uuidString: "00000000-0000-4000-8000-000000000001") ?? UUID()

    @Test("report keeps the latest frame, forget drops it")
    func reportAndForget() {
        let registry = CardFrameRegistry()
        registry.report(id, frame: CGRect(x: 0, y: 0, width: 10, height: 10))
        registry.report(id, frame: CGRect(x: 0, y: 40, width: 10, height: 10))
        #expect(registry.frames[id]?.minY == 40)
        registry.forget(id)
        #expect(registry.frames[id] == nil)
    }

    @Test("a reported frame does not invalidate a reader; hiding a slot does")
    func observation() {
        let registry = CardFrameRegistry()
        let framesChanged = Flag()
        withObservationTracking { _ = registry.frames } onChange: { framesChanged.raised = true }
        registry.report(id, frame: .zero)
        #expect(!framesChanged.raised)

        let hiddenChanged = Flag()
        withObservationTracking { _ = registry.hidden } onChange: { hiddenChanged.raised = true }
        registry.hidden = id
        #expect(hiddenChanged.raised)
    }
}
