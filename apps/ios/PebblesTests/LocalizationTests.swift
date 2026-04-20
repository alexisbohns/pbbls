import Foundation
import Testing
@testable import Pebbles

@Suite("Localization — Pattern C fallback")
struct LocalizationPatternCTests {

    @Test("Emotion.localizedName falls back to name when slug has no catalog entry")
    func emotionFallsBackToName() {
        let emotion = Emotion(
            id: UUID(),
            slug: "not-a-real-slug-xyz",
            name: "Fallback Name",
            color: "#000000"
        )
        #expect(emotion.localizedName == "Fallback Name")
    }

    @Test("Domain.localizedName falls back to name when slug has no catalog entry")
    func domainFallsBackToName() {
        let domain = Domain(
            id: UUID(),
            slug: "not-a-real-slug-xyz",
            name: "Fallback Name",
            label: "ignored label"
        )
        #expect(domain.localizedName == "Fallback Name")
    }

    @Test("EmotionRef.localizedName falls back to name when slug has no catalog entry")
    func emotionRefFallsBackToName() {
        let ref = decodeRef(EmotionRef.self, json: """
        {"id":"00000000-0000-0000-0000-000000000000","slug":"not-a-real-slug-xyz","name":"Ref Fallback","color":"#000000"}
        """)
        #expect(ref.localizedName == "Ref Fallback")
    }

    @Test("DomainRef.localizedName falls back to name when slug has no catalog entry")
    func domainRefFallsBackToName() {
        let ref = decodeRef(DomainRef.self, json: """
        {"id":"00000000-0000-0000-0000-000000000000","slug":"not-a-real-slug-xyz","name":"Ref Fallback"}
        """)
        #expect(ref.localizedName == "Ref Fallback")
    }

    // MARK: helpers

    private func decodeRef<T: Decodable>(_ type: T.Type, json: String) -> T {
        // swiftlint:disable:next force_try
        return try! JSONDecoder().decode(T.self, from: Data(json.utf8))
    }
}
