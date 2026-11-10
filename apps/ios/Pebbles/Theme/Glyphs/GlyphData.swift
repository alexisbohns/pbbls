/// A path as a form draws it: filled (non-zero or even-odd), stroked, or both.
struct RenderedPathData: Decodable, Hashable, Sendable {
    let d: String
    let fill: Bool
    let evenOdd: Bool
    let stroke: Bool
}

/// One path of a glyph with its role (spec D2), as `glyphs.json` stores it.
struct GlyphPathData: Decodable, Sendable {
    enum Role: String, Decodable, Sendable {
        case body, mark, step
    }

    let d: String
    let role: Role?
    let part: String?
    let step: Int?
    let hidden: Bool?
    /// A mark or step with lines inside it: its carved fill, drawn instead of `d` when it fills.
    let fill: String?
    /// The paths `fill` is carved through, left out while it fills.
    let cuts: [Int]?
}

/// A solid or parts fill worked out at build time: indices are into `paths`.
struct CarvedFillData: Decodable, Sendable {
    let d: String
    let keep: [Int]
    let lit: [Int]
}

/// A glyph as `pbbls-glyphs.json` stores it (spec §2), the contract the generator writes.
struct GlyphData: Decodable, Sendable {
    let paths: [GlyphPathData]
    let parts: [String]
    let steps: Int
    let carved: [String: CarvedFillData]
    let dotted: [RenderedPathData]?
    let overrides: [String: [RenderedPathData]]

    static let empty = GlyphData(paths: [], parts: [], steps: 0, carved: [:], dotted: nil, overrides: [:])

    var hasMark: Bool { paths.contains { $0.role == .mark } }
    var hasBody: Bool { paths.contains { $0.role == .body } }

    /// The forms this glyph has, as keys, in the generator's order (`formKeysOf` in `forms.ts`).
    var formKeys: [String] {
        var fills: [GlyphFill] = [.regular]
        if hasBody { fills.append(.solid) }
        fills += Self.partCombinations(parts).map { GlyphFill.parts($0) }
        if steps > 0 { fills += (1...steps).map { GlyphFill.progress($0) } }
        var keys = fills.flatMap { fill in
            hasMark ? [GlyphForm(fill).key, GlyphForm(fill, marked: true).key] : [GlyphForm(fill).key]
        }
        if dotted != nil { keys.insert("dotted", at: hasMark ? 2 : 1) }
        return keys
    }

    /// Every non-empty combination of parts short of all of them, smallest first,
    /// in source order (`partCombinations` in `roles.ts`).
    static func partCombinations(_ names: [String]) -> [[String]] {
        let n = names.count
        guard n >= 2 else { return [] }
        let all = (1..<((1 << n) - 1)).map { bits in
            names.enumerated().filter { bits & (1 << $0.offset) != 0 }.map(\.element)
        }
        // JavaScript's sort is stable: keep the bit order within a size.
        return all.enumerated()
            .sorted { ($0.element.count, $0.offset) < ($1.element.count, $1.offset) }
            .map(\.element)
    }
}

/// The resource as a whole.
struct GlyphFile: Decodable, Sendable {
    let version: Int
    let states: [String: [String]]
    let glyphs: [String: GlyphData]

    static let empty = GlyphFile(version: 1, states: [:], glyphs: [:])
}
