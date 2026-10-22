import Foundation
import Testing
@testable import Pebbles

// The seeds of apps/web/lib/utils/spine/stone-recipe.test.ts at a04aeec5 and of
// Android's core/common/UuidHashTest.kt: FNV-1a over the lowercase id.
@Suite("StableHash")
struct StableHashTests {
    @Test("FNV-1a matches web and Android")
    func vectors() {
        #expect(StableHash.fnv1a("0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b") == 3_118_864_542)
        #expect(StableHash.fnv1a("8d1f6c3e-2b7a-4c1e-9f0a-5e6d7c8b9a01") == 3_303_075_303)
    }

    @Test("a UUID parsed from the uppercase string seeds the same as the lowercase one")
    func uppercaseUUID() throws {
        let id = try #require(UUID(uuidString: "0E9D8C7B-6A5F-4E3D-8C2B-1A0F9E8D7C6B"))
        #expect(StableHash.seed(id) == 3_118_864_542)
    }
}
