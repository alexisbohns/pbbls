import Foundation
import Testing
@testable import Pebbles

/// #903 §3: a restored id that no longer resolves pops its route instead of
/// showing a broken screen.
@Suite("Tab route resolution")
@MainActor
struct TabRouteResolutionTests {
    private let soulId = UUID(uuidString: "33333333-3333-3333-3333-333333333333")!

    private func soul(_ id: UUID, name: String = "Ada") -> SoulWithGlyph {
        SoulWithGlyph(
            id: id,
            name: name,
            glyphId: SystemGlyph.default,
            glyph: Glyph(id: SystemGlyph.default, name: nil, strokes: [], viewBox: "0 0 200 200", userId: nil),
            pebblesCount: 0
        )
    }

    private func refs(loadedWith souls: [SoulWithGlyph]?) -> ReferenceDataService {
        let service = ReferenceDataService(client: SupabaseService().client)
        if let souls { service.replace(domains: [], souls: souls, collections: []) }
        return service
    }

    @Test("a soul missing from loaded reference data is not found locally (fetches)")
    func soulMissingLocally() {
        #expect(SoulRoute.local(soulId, known: [], refs: refs(loadedWith: [soul(UUID())])) == nil)
    }

    @Test("before reference data loads, an unknown soul is not found locally (fetches)")
    func soulBeforeRefsLoad() {
        #expect(SoulRoute.local(soulId, known: [], refs: refs(loadedWith: nil)) == nil)
    }

    @Test("the list's own rows win over reference data")
    func soulFromKnown() {
        let fresh = soul(soulId, name: "Fresh")
        let result = SoulRoute.local(soulId, known: [fresh], refs: refs(loadedWith: [soul(soulId, name: "Stale")]))
        #expect(result == fresh)
    }

    @Test("a restored soul resolves from reference data")
    func soulFromRefs() {
        let stored = soul(soulId)
        #expect(SoulRoute.local(soulId, known: [], refs: refs(loadedWith: [stored])) == stored)
    }

    @Test("no fetched soul row means missing (pops)")
    func soulFetchedMissing() {
        #expect(SoulRoute.resolution(fetched: []) == .missing)
    }

    @Test("a fetched soul row resolves, decoded from the souls select payload")
    func soulFetchedFound() throws {
        let json = """
        {"id":"33333333-3333-3333-3333-333333333333","name":"Ada",\
        "glyph_id":"4759c37c-68a6-46a6-b4fc-046bd0316752",\
        "glyphs":{"id":"4759c37c-68a6-46a6-b4fc-046bd0316752","name":null,"strokes":[],"view_box":"0 0 200 200"},\
        "pebbles_count":[{"count":2}]}
        """
        let row = try JSONDecoder().decode(SoulWithGlyph.self, from: Data(json.utf8))
        #expect(SoulRoute.resolution(fetched: [row]) == .found(row))
        #expect(row.pebblesCount == 2)
    }

    @Test("no collection row means missing (pops)")
    func collectionMissing() {
        #expect(CollectionRoute.resolution(fetched: []) == .missing)
    }

    @Test("a fetched collection row resolves")
    func collectionFound() throws {
        let json = """
        {"id":"44444444-4444-4444-4444-444444444444","name":"Trips","mode":null,\
        "glyph_id":null,"glyph":null,"pebble_count":[{"count":3}]}
        """
        let row = try JSONDecoder().decode(Collection.self, from: Data(json.utf8))
        #expect(CollectionRoute.resolution(fetched: [row]) == .found(row))
    }
}
