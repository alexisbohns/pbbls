import Foundation

/// FNV-1a, 32-bit, the web's `hashUUID` (`apps/web/lib/engine/seed.ts`) and
/// Android's `UuidHash`: each pebble's crust varies the same way everywhere.
enum StableHash {
    static func fnv1a(_ text: String) -> UInt32 {
        var hash: UInt32 = 0x811C_9DC5
        for unit in text.utf16 {
            hash ^= UInt32(unit)
            hash = hash &* 0x0100_0193
        }
        return hash
    }

    /// Over the lowercase id: `UUID.uuidString` is uppercase.
    static func seed(_ id: UUID) -> UInt32 {
        fnv1a(id.uuidString.lowercased())
    }
}
