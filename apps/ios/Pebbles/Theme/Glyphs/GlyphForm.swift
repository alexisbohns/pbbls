/// What a form fills (spec D3): nothing, the body, some of its parts, the
/// first k steps, or the drawn dotted line.
enum GlyphFill: Hashable, Sendable {
    case regular
    case solid
    case parts([String])
    case progress(Int)
    case dotted
}

/// A form (spec D3): a fill, plus the mark when the glyph has one. Its `key`
/// is the generator's form key (`solid-marked`, `parts-moon`, `progress-2`).
struct GlyphForm: Hashable, Sendable {
    var fill: GlyphFill
    var marked: Bool

    init(_ fill: GlyphFill = .regular, marked: Bool = false) {
        self.fill = fill
        self.marked = marked
    }

    static let regular = GlyphForm(.regular)
    static let marked = GlyphForm(.regular, marked: true)
    static let solid = GlyphForm(.solid)
    static let dotted = GlyphForm(.dotted)

    var fillKey: String {
        switch fill {
        case .regular: "regular"
        case .solid: "solid"
        case .dotted: "dotted"
        case .parts(let names): "parts-" + names.joined(separator: "+")
        case .progress(let k): "progress-\(k)"
        }
    }

    /// `regular` marked is `marked`; any other fill takes a `-marked` suffix.
    var key: String {
        guard marked else { return fillKey }
        return fillKey == "regular" ? "marked" : fillKey + "-marked"
    }

    /// The form a key names, or nil when it is not a form key (`parseFormKey` in `forms.ts`).
    init?(key: String) {
        if key == "marked" {
            self.init(.regular, marked: true)
            return
        }
        let marked = key.hasSuffix("-marked")
        let head = marked ? String(key.dropLast("-marked".count)) : key
        switch head {
        case "regular" where !marked: self.init(.regular)
        case "dotted" where !marked: self.init(.dotted)
        case "solid": self.init(.solid, marked: marked)
        default:
            if head.hasPrefix("parts-") {
                let list = head.dropFirst("parts-".count)
                let names = list.split(separator: "+", omittingEmptySubsequences: false).map(String.init)
                let word = { (s: String) in !s.isEmpty && s.allSatisfy { ("a"..."z").contains($0) } }
                guard names.allSatisfy(word) else { return nil }
                self.init(.parts(names), marked: marked)
            } else if head.hasPrefix("progress-") {
                let digits = head.dropFirst("progress-".count)
                guard let k = Int(digits), k >= 1, !digits.hasPrefix("0") else { return nil }
                self.init(.progress(k), marked: marked)
            } else {
                return nil
            }
        }
    }
}
