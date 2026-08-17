import Foundation
import Testing
@testable import Pebbles

/// The draft payload is the M47 cross-surface contract: web, iOS and Android all
/// read and write this exact shape. These tests pin the parts that would break
/// silently — omitted keys, the ISO-8601 convention, and the sanitizing rules.
@Suite("PebbleDraftPayload")
struct PebbleDraftPayloadTests {

    private let soulA = UUID()
    private let collectionA = UUID()
    private let emotionA = UUID()
    private let domainA = UUID()
    private let glyphA = UUID()

    private func encoded(_ payload: PebbleDraftPayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        let object = try JSONSerialization.jsonObject(with: data)
        return object as? [String: Any] ?? [:]
    }

    // MARK: - omitted keys

    @Test("An empty draft encodes only the always-defaulted keys")
    func emptyDraftOmitsUnsetKeys() throws {
        let payload = PebbleDraftPayload(from: PebbleDraft(), formSnap: nil, userId: nil)
        let json = try encoded(payload)

        // Present because the composer always holds a real value for them.
        #expect(json["happened_at"] != nil)
        #expect(json["visibility"] as? String == "secret")

        // Absent, not null: nothing has been set.
        for key in ["name", "description", "emotion_id", "domain_ids",
                    "soul_ids", "collection_ids", "glyph_id", "snaps",
                    "intensity", "positiveness"] {
            #expect(json[key] == nil, "\(key) should be omitted, not encoded as null")
        }
    }

    @Test("A quick capture with only a name encodes just that name")
    func quickCaptureEncodesName() throws {
        var draft = PebbleDraft()
        draft.name = "  Argument with Léa  "
        let json = try encoded(PebbleDraftPayload(from: draft, formSnap: nil, userId: nil))

        #expect(json["name"] as? String == "Argument with Léa", "name should be trimmed")
        #expect(json["emotion_id"] == nil)
    }

    @Test("Whitespace-only name and description are omitted entirely")
    func whitespaceOnlyFieldsAreOmitted() throws {
        var draft = PebbleDraft()
        draft.name = "   "
        draft.description = "\n\t "
        let payload = PebbleDraftPayload(from: draft, formSnap: nil, userId: nil)

        #expect(payload.name == nil)
        #expect(payload.description == nil)
        #expect(payload.isEmpty, "a whitespace-only draft is not worth saving")
    }

    // MARK: - valence decomposition

    @Test("Valence is stored decomposed, and both keys are omitted when unset")
    func valenceDecomposes() throws {
        var draft = PebbleDraft()
        draft.name = "x"
        #expect(try encoded(PebbleDraftPayload(from: draft, formSnap: nil, userId: nil))["intensity"] == nil)

        draft.valence = .highlightLarge
        let json = try encoded(PebbleDraftPayload(from: draft, formSnap: nil, userId: nil))
        #expect(json["intensity"] as? Int == 3)
        #expect(json["positiveness"] as? Int == 1)
    }

    @Test("Every valence round-trips through the decomposed pair")
    func everyValenceRoundTrips() {
        for valence in Valence.allCases {
            let rebuilt = Valence.from(positiveness: valence.positiveness, intensity: valence.intensity)
            #expect(rebuilt == valence)
        }
    }

    @Test("An out-of-grid pair leaves the valence unset rather than guessing")
    func invalidValencePairIsRejected() {
        #expect(Valence.from(positiveness: 7, intensity: 2) == nil)
        #expect(Valence.from(positiveness: 0, intensity: 0) == nil)
    }

    // MARK: - round trip

    @Test("A fully populated payload survives an encode/decode round trip")
    func fullRoundTrip() throws {
        var draft = PebbleDraft()
        draft.name = "Full"
        draft.description = "Everything set"
        draft.happenedAt = Date(timeIntervalSince1970: 1_785_000_000)
        draft.valence = .lowlightSmall
        draft.emotionId = emotionA
        draft.domainId = domainA
        draft.soulIds = [soulA]
        draft.collectionId = collectionA
        draft.glyphId = glyphA
        draft.visibility = .public

        let original = PebbleDraftPayload(from: draft, formSnap: nil, userId: nil)
        let decoded = try JSONDecoder().decode(
            PebbleDraftPayload.self, from: try JSONEncoder().encode(original)
        )

        #expect(decoded == original)
        // Second-precision: the wire format carries no sub-second component, so a
        // round trip must not drift by more than that.
        #expect(abs(decoded.happenedAt!.timeIntervalSince(draft.happenedAt)) < 1)
    }

    @Test("happened_at uses the same internet-date-time format as the create payload")
    func happenedAtFormatMatchesCreatePayload() throws {
        var draft = PebbleDraft()
        draft.name = "x"
        draft.happenedAt = Date(timeIntervalSince1970: 1_785_000_000)
        let value = try encoded(PebbleDraftPayload(from: draft, formSnap: nil, userId: nil))["happened_at"] as? String

        // No fractional seconds, explicit offset — .withInternetDateTime.
        #expect(value == "2026-07-25T17:20:00Z")
    }

    @Test("Unknown and malformed keys decode without throwing")
    func decodingIsTolerant() throws {
        let json = """
        {"name":"From another surface","happened_at":"not-a-date","unknown_key":42}
        """
        let decoded = try JSONDecoder().decode(PebbleDraftPayload.self, from: Data(json.utf8))

        #expect(decoded.name == "From another surface")
        #expect(decoded.happenedAt == nil, "an unparseable date is dropped, not thrown")
    }

    // MARK: - hydration and sanitizing (D7)

    @Test("Hydration drops souls and collections the user no longer owns")
    func hydrationDropsStaleReferences() {
        let goneSoul = UUID()
        var payload = PebbleDraftPayload()
        payload.name = "Stale"
        payload.soulIds = [soulA, goneSoul]
        payload.collectionIds = [UUID()]
        payload.emotionId = emotionA
        payload.domainIds = [domainA]

        let hydrated = PebbleDraft(
            payload: payload,
            known: .init(soulIds: [soulA], collectionIds: [collectionA])
        )

        #expect(hydrated.soulIds == [soulA], "the deleted soul is dropped")
        #expect(hydrated.collectionId == nil, "a collection the user lost is dropped")
        // Reference data is never sanitized — it is seeded, not user-owned.
        #expect(hydrated.emotionId == emotionA)
        #expect(hydrated.domainId == domainA)
    }

    @Test("Hydration restores happened_at verbatim rather than re-stamping it")
    func hydrationKeepsCapturedTimestamp() {
        let captured = Date(timeIntervalSince1970: 1_700_000_000)
        var payload = PebbleDraftPayload()
        payload.name = "Captured hours ago"
        payload.happenedAt = captured

        let hydrated = PebbleDraft(payload: payload, known: .init(soulIds: [], collectionIds: []))
        #expect(hydrated.happenedAt == captured)
    }

    @Test("An empty payload hydrates to the composer's defaults")
    func emptyPayloadHydratesToDefaults() {
        let hydrated = PebbleDraft(
            payload: PebbleDraftPayload(), known: .init(soulIds: [], collectionIds: [])
        )
        #expect(hydrated.name.isEmpty)
        #expect(hydrated.valence == nil)
        #expect(hydrated.visibility == .secret)
        #expect(!hydrated.isValid)
    }

    // MARK: - the draft/publish gate split (D5)

    @Test("isSavableAsDraft is looser than isValid")
    func draftGateIsLooserThanPublishGate() {
        var draft = PebbleDraft()
        #expect(!draft.isSavableAsDraft(formSnap: nil, userId: nil), "nothing typed yet")
        #expect(!draft.isValid)

        draft.name = "Just a name"
        #expect(draft.isSavableAsDraft(formSnap: nil, userId: nil), "a name alone is a valid draft")
        #expect(!draft.isValid, "but not enough to publish")

        draft.emotionId = emotionA
        draft.domainId = domainA
        draft.valence = .neutralMedium
        #expect(draft.isValid)
    }

    @Test("A photo alone makes a draft savable")
    func photoOnlyDraftIsSavable() {
        let draft = PebbleDraft()
        let snap = FormSnap.existing(id: UUID(), storagePath: "user/snap")
        #expect(draft.isSavableAsDraft(formSnap: snap, userId: UUID()))
    }

    @Test("An existing snap survives a payload round trip so a draft keeps its photo")
    func existingSnapRoundTrips() throws {
        let snapId = UUID()
        var draft = PebbleDraft()
        draft.name = "With a photo"
        let payload = PebbleDraftPayload(
            from: draft,
            formSnap: .existing(id: snapId, storagePath: "user-1/\(snapId)"),
            userId: UUID()
        )
        let decoded = try JSONDecoder().decode(
            PebbleDraftPayload.self, from: try JSONEncoder().encode(payload)
        )

        #expect(decoded.existingSnap?.id == snapId)
        #expect(decoded.existingSnap?.storagePath == "user-1/\(snapId)")
    }
}
