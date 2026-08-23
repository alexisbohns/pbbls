import Testing
@testable import Pebbles

@Suite("RecordStep")
struct RecordStepTests {

    @Test("steps run photo → success in the designed order")
    func order() {
        #expect(RecordStep.allCases == [
            .photo, .when, .name, .valence, .emotion, .domain,
            .souls, .collection, .glyph, .privacy, .success
        ])
    }

    @Test("photo, souls, collection and glyph are the optional steps")
    func optionality() {
        let optional = RecordStep.allCases.filter(\.isOptional)
        #expect(optional == [.photo, .souls, .collection, .glyph])
    }

    @Test("ten steps are counted by the dots; success is not")
    func dotCount() {
        #expect(RecordStep.counted.count == 10)
        #expect(RecordStep.counted.contains(.success) == false)
        #expect(RecordStep.success.dotIndex == nil)
        #expect(RecordStep.photo.dotIndex == 0)
        #expect(RecordStep.privacy.dotIndex == 9)
    }

    @Test("neighbors chain the whole flow and terminate at both ends")
    func neighbors() {
        #expect(RecordStep.photo.previous == nil)
        #expect(RecordStep.photo.next == .when)
        #expect(RecordStep.privacy.next == .success)
        #expect(RecordStep.success.next == nil)
        #expect(RecordStep.when.previous == .photo)
    }
}
