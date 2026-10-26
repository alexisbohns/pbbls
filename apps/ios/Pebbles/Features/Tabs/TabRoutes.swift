import Foundation

/// Path pushes nothing yet. The stack exists for reselect and for future
/// pushes. The enum is uninhabited, so its conformances are written by hand:
/// no value of it can exist, and decoding one always fails (a snapshot that
/// somehow holds one falls back to an empty Path stack).
enum PathRoute {}

extension PathRoute: Hashable {
    static func == (lhs: PathRoute, rhs: PathRoute) -> Bool {
        switch lhs {}
    }

    func hash(into hasher: inout Hasher) {
        switch self {}
    }
}

extension PathRoute: Codable {
    init(from decoder: Decoder) throws {
        throw DecodingError.dataCorrupted(
            .init(codingPath: decoder.codingPath, debugDescription: "PathRoute has no cases")
        )
    }

    func encode(to encoder: Encoder) throws {
        switch self {}
    }
}

/// The Home tab's pushed destinations: a guide, by slug (the catalog key the
/// server owns; never the row id, so a snapshot survives a reseed).
enum HomeRoute: Codable, Hashable {
    case guide(String)
}

/// What a root does once on arrival when another tab sent the user over
/// (Home's checklist, spec §1.4): open its add sheet, or start a carve. Handed
/// over through `TabNavigation.go(_:intent:)`, consumed with
/// `takeIntent(for:)`, never persisted.
enum TabIntent: Equatable {
    case createSoul, createCollection, carveGlyph
}

/// Routes carry ids, never models (#903 §3): `SoulWithGlyph` and `Collection`
/// are Decodable-only and change under edits. `SoulRoute` / `CollectionRoute`
/// resolve the id when the route is shown.
enum PeopleRoute: Codable, Hashable {
    case soul(UUID)
    case connections
}

enum CollectionsRoute: Codable, Hashable {
    case collection(UUID)
}

enum YouRoute: Codable, Hashable {
    case glyphs
    case achievements
    case lab
}

/// What a resolver view found for its id.
enum RouteResolution<Value> {
    case loading
    case found(Value)
    case missing
}

extension RouteResolution: Equatable where Value: Equatable {}
