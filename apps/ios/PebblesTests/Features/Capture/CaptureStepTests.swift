import Testing
@testable import Pebbles

@Suite("CaptureStep")
struct CaptureStepTests {
    @Test("two counted steps: photo then the moment page")
    func order() {
        #expect(CaptureStep.allCases == [.photo, .moment])
        #expect(CaptureStep.photo.next == .moment)
        #expect(CaptureStep.moment.next == nil)
        #expect(CaptureStep.moment.previous == .photo)
        #expect(CaptureStep.photo.dotIndex == 0)
        #expect(CaptureStep.moment.dotIndex == 1)
    }
}
