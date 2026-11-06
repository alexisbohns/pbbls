import Foundation
import Testing
@testable import Pebbles

/// `complete_onboarding`'s params against the bytes the database harness uses
/// (`packages/supabase/scripts/oracles/onboarding/payloads/complete_onboarding.json`).
@Suite("OnboardingPayload")
struct OnboardingPayloadTests {
    private static let fixtures = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()
        .deletingLastPathComponent()
        .deletingLastPathComponent()
        .appendingPathComponent("Fixtures")

    private func object(_ data: Data) throws -> NSDictionary {
        try #require(try JSONSerialization.jsonObject(with: data) as? NSDictionary)
    }

    @Test func encodesTheOraclePayload() throws {
        let oracle = try Data(contentsOf: Self.fixtures.appendingPathComponent("complete-onboarding-payload.json"))
        let glyph = try #require(UUID(uuidString: "c5b3d7ea-8618-4e4c-a3b0-ac88f8a97d68"))
        let lea = try #require(UUID(uuidString: "003196cd-38c3-410c-a59e-1883cebb630e"))
        let jo = try #require(UUID(uuidString: "3d0b2951-54f4-4f4f-92f9-66d81dc2a936"))
        let answers = OnboardingAnswers(
            displayName: "  Maya  ",
            glyphId: glyph,
            reasons: [.noticeFeelings, .keepPeople, .keepRecord],
            pace: .weeklyWrap,
            souls: [
                OnboardingSoulDraft(name: "Noé", glyphId: glyph),
                OnboardingSoulDraft(name: " Léa ", glyphId: lea),
                OnboardingSoulDraft(name: "Jo", glyphId: jo)
            ]
        )
        let encoded = try JSONEncoder().encode(answers.params)
        #expect(try object(encoded) == object(oracle))
    }

    @Test func everythingSkippedSendsOnlyEmptySouls() throws {
        let encoded = try JSONEncoder().encode(OnboardingAnswers().params)
        #expect(try object(encoded) == ["p_souls": [Any]()] as NSDictionary)
    }

    @Test func wireValuesMatchTheServerLists() {
        #expect(OnboardingReason.allCases.map(\.rawValue) == [
            "notice-feelings", "keep-people", "hard-season", "good-days", "own-patterns", "keep-record"
        ])
        #expect(OnboardingPace.allCases.map(\.rawValue) == [
            "daily", "few-a-week", "when-it-matters", "weekly-wrap"
        ])
    }

    @Test func letterFallsBackToEnglishAndSplitsParagraphs() {
        let body = "One\n\n  Two  \n\n\n\nThree"
        let letter = PromiseLetter(bodyEn: body, bodyFr: nil, signedByEn: "A", signedByFr: "  ")
        #expect(letter.body(french: true) == letter.bodyEn)
        #expect(letter.signedBy(french: true) == "A")
        #expect(letter.paragraphs(french: false) == ["One", "Two", "Three"])
        let fr = PromiseLetter(bodyEn: "x", bodyFr: "Un\n\nDeux", signedByEn: "A", signedByFr: "B")
        #expect(fr.paragraphs(french: true) == ["Un", "Deux"])
        #expect(fr.signedBy(french: true) == "B")
    }

    @Test func paragraphsTolerateCRLFFromAFormSubmission() {
        let letter = PromiseLetter(bodyEn: "One\r\n\r\n Two \r\n\r\nThree\r\nstill three", bodyFr: nil,
                                   signedByEn: "A", signedByFr: nil)
        #expect(letter.paragraphs(french: false) == ["One", "Two", "Three\nstill three"])
    }
}
