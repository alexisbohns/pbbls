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
