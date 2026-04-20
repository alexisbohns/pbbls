import Foundation
import Testing
@testable import Pebbles

@Suite("ValenceSizeGroup")
struct ValenceSizeGroupTests {

    @Test("allCases is small, medium, large in that order")
    func allCasesOrder() {
        #expect(ValenceSizeGroup.allCases == [.small, .medium, .large])
    }

    @Test("name copy matches the spec")
    func nameCopy() {
        #expect(String(localized: ValenceSizeGroup.small.name) == "Day event")
        #expect(String(localized: ValenceSizeGroup.medium.name) == "Week event")
        #expect(String(localized: ValenceSizeGroup.large.name) == "Month event")
    }

    @Test("description copy matches the spec")
    func descriptionCopy() {
        #expect(String(localized: ValenceSizeGroup.small.description) ==
            "This moment impacted my day and will be wrapped in my weekly Cairn")
        #expect(String(localized: ValenceSizeGroup.medium.description) ==
            "This moment impacted my whole week and will be wrapped in my monthly Cairn")
        #expect(String(localized: ValenceSizeGroup.large.description) ==
            "This moment impacted my whole month and will be wrapped in my yearly Cairn")
    }

    @Test("id matches rawValue")
    func idMatchesRawValue() {
        for group in ValenceSizeGroup.allCases {
            #expect(group.id == group.rawValue)
        }
    }
}

@Suite("ValencePolarity")
struct ValencePolarityTests {

    @Test("allCases is lowlight, neutral, highlight in that order")
    func allCasesOrder() {
        #expect(ValencePolarity.allCases == [.lowlight, .neutral, .highlight])
    }
}

@Suite("Valence helpers")
struct ValenceHelpersTests {

    @Test("sizeGroup mapping covers all nine cases")
    func sizeGroupMapping() {
        #expect(Valence.lowlightSmall.sizeGroup   == .small)
        #expect(Valence.neutralSmall.sizeGroup    == .small)
        #expect(Valence.highlightSmall.sizeGroup  == .small)
        #expect(Valence.lowlightMedium.sizeGroup  == .medium)
        #expect(Valence.neutralMedium.sizeGroup   == .medium)
        #expect(Valence.highlightMedium.sizeGroup == .medium)
        #expect(Valence.lowlightLarge.sizeGroup   == .large)
        #expect(Valence.neutralLarge.sizeGroup    == .large)
        #expect(Valence.highlightLarge.sizeGroup  == .large)
    }

    @Test("polarity mapping covers all nine cases")
    func polarityMapping() {
        #expect(Valence.lowlightSmall.polarity    == .lowlight)
        #expect(Valence.lowlightMedium.polarity   == .lowlight)
        #expect(Valence.lowlightLarge.polarity    == .lowlight)
        #expect(Valence.neutralSmall.polarity     == .neutral)
        #expect(Valence.neutralMedium.polarity    == .neutral)
        #expect(Valence.neutralLarge.polarity     == .neutral)
        #expect(Valence.highlightSmall.polarity   == .highlight)
        #expect(Valence.highlightMedium.polarity  == .highlight)
        #expect(Valence.highlightLarge.polarity   == .highlight)
    }

    @Test("assetName matches the imagesets in Assets.xcassets/Valence")
    func assetNameMatchesAssets() {
        for valence in Valence.allCases {
            let name = valence.assetName
            #expect(name == "valence-\(valence.rawValue)")
            #expect(!name.isEmpty)
        }
    }

    @Test("shortLabel reflects polarity")
    func shortLabel() {
        #expect(String(localized: Valence.lowlightSmall.shortLabel)  == "Lowlight")
        #expect(String(localized: Valence.neutralMedium.shortLabel)  == "Neutral")
        #expect(String(localized: Valence.highlightLarge.shortLabel) == "Highlight")
    }

    @Test("Lookup by (sizeGroup, polarity) is unique for every cell")
    func lookupIsUnique() {
        for group in ValenceSizeGroup.allCases {
            for polarity in ValencePolarity.allCases {
                let matches = Valence.allCases.filter {
                    $0.sizeGroup == group && $0.polarity == polarity
                }
                #expect(matches.count == 1, "(\(group), \(polarity)) should map to exactly one Valence")
            }
        }
    }
}
