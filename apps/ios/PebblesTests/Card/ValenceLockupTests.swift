import Foundation
import Testing
@testable import Pebbles

// Ported from apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/logic/ValenceLockupTest.kt
// at d53be9cf: Android's word scale (1, 44/56, 34/56) is iOS's rank (large, medium,
// small), stack B's `SpineType.lockupWord`; its string resources are `Valence.headline`.
@Suite("ValenceLockup (Android ValenceLockupTest.kt)")
struct ValenceLockupTests {
    private func english(_ resource: LocalizedStringResource) -> String {
        var copy = resource
        copy.locale = Locale(identifier: "en")
        return String(localized: copy)
    }

    // swiftlint:disable:next large_tuple
    static let expected: [(Valence, String, Bool, LockupRank, String, LockupInk)] = [
        (.highlightLarge, "Highlight", false, .large, "OF MY MONTH", .stoneRamp),
        (.highlightMedium, "Highlight", false, .medium, "OF MY WEEK", .stoneRamp),
        (.highlightSmall, "Highlight", true, .small, "OF MY DAY", .stoneRamp),
        (.neutralLarge, "Moment", false, .large, "OF MY MONTH", .primary),
        (.neutralMedium, "Moment", false, .medium, "OF MY WEEK", .primary),
        (.neutralSmall, "Moment", true, .small, "OF MY DAY", .primary),
        (.lowlightLarge, "Lowlight", false, .large, "OF MY MONTH", .ink),
        (.lowlightMedium, "Lowlight", false, .medium, "OF MY WEEK", .ink),
        (.lowlightSmall, "Lowlight", true, .small, "OF MY DAY", .ink)
    ]

    @Test("every valence maps to the record flow's lockup")
    func everyValence() {
        #expect(Self.expected.count == Valence.allCases.count)
        for (valence, word, lowercase, rank, span, ink) in Self.expected {
            let lockup = ValenceLockup.of(valence)
            #expect(english(lockup.word) == word, "\(valence) word")
            #expect(lockup.lowercase == lowercase, "\(valence) case")
            #expect(lockup.rank == rank, "\(valence) rank")
            #expect(english(lockup.span) == span, "\(valence) span")
            #expect(lockup.ink == ink, "\(valence) ink")
        }
    }

    @Test("the word and span are the record flow's own resources")
    func sameResources() {
        for valence in Valence.allCases {
            #expect(ValenceLockup.of(valence).word == valence.headline.word)
            #expect(ValenceLockup.of(valence).span == valence.headline.span)
        }
    }

    @Test("the rank is the size group stack B's lockup word takes")
    func rank() {
        #expect(LockupRank.small.sizeGroup == .small)
        #expect(LockupRank.medium.sizeGroup == .medium)
        #expect(LockupRank.large.sizeGroup == .large)
    }
}
