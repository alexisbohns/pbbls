import Testing
@testable import Pebbles

@Suite("CaptureStep")
struct CaptureStepTests {
    @Test("photo · when · name, three dots, photo optional")
    func order() {
        #expect(CaptureStep.allCases == [.photo, .when, .name])
        #expect(CaptureStep.allCases.map(\.dotIndex) == [0, 1, 2])
        #expect(CaptureStep.allCases.filter(\.isOptional) == [.photo])
        #expect(CaptureStep.name.next == nil)
        #expect(CaptureStep.photo.previous == nil)
    }
}
