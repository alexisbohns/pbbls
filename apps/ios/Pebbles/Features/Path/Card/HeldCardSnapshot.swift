import Foundation

/// The held card as the scene keeps it (#1146 D7), under
/// `@SceneStorage("heldCard")` in `RootView`: which moment, the face it will
/// land on, and the print under way (E3). A relaunch after a background kill
/// reopens the card at rest on that face. Decoding is tolerant: an unknown face
/// is the recto, a missing print is none, and data that is not a snapshot is
/// no card. New pattern, named in the E1c PR.
struct HeldCardSnapshot: Codable, Equatable {
    var momentId: UUID
    var face: CardFace
    var printingFor: UUID?

    init(momentId: UUID, face: CardFace, printingFor: UUID? = nil) {
        self.momentId = momentId
        self.face = face
        self.printingFor = printingFor
    }

    private enum CodingKeys: String, CodingKey {
        case momentId, face, printingFor
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        momentId = try container.decode(UUID.self, forKey: .momentId)
        face = (try? container.decode(CardFace.self, forKey: .face)) ?? .recto
        printingFor = (try? container.decodeIfPresent(UUID.self, forKey: .printingFor)) ?? nil
    }

    static func decoded(from data: Data) -> HeldCardSnapshot? {
        try? JSONDecoder().decode(HeldCardSnapshot.self, from: data)
    }

    var encoded: Data? {
        try? JSONEncoder().encode(self)
    }
}
