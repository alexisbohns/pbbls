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
        #expect(ValenceSizeGroup.small.name == "Day event")
        #expect(ValenceSizeGroup.medium.name == "Week event")
        #expect(ValenceSizeGroup.large.name == "Month event")
    }

    @Test("description copy matches the spec")
    func descriptionCopy() {
        #expect(ValenceSizeGroup.small.description ==
            "This moment impacted my day and will be wrapped in my weekly Cairn")
        #expect(ValenceSizeGroup.medium.description ==
            "This moment impacted my whole week and will be wrapped in my monthly Cairn")
        #expect(ValenceSizeGroup.large.description ==
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
