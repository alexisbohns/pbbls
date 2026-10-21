import Foundation

/// One run of a people line (#1130 R7).
enum PeopleLinePart: Equatable {
    /// "with", "&", "& twelve others": the quiet italic.
    case aside(String)
    /// A soul's glyph, or the dashed mark of a soul without one.
    case mark(String)
    /// A named soul, in small caps.
    case name(String)
    /// ", " between names.
    case separator(String)
    /// "+7", lining tabular.
    case more(String)
}

/// The people line as parts, so the line and the card's strip are each one
/// wrapping `Text` (`PeopleLineView`). The line reads "with [g] MARA, [g] ELIO
/// & twelve others [g]×5 +7"; the card's strip is the same without "with", the
/// commas and the "&".
enum PeopleLineParts {
    enum Style { case line, card }

    /// - Parameters:
    ///   - with: the localised "with", spoken on the line only.
    ///   - others: the localised "& twelve others" (`SpineCopy.others`).
    static func parts(
        _ line: PeopleLine, names: [String: String], with: String, others: String, style: Style
    ) -> [PeopleLinePart] {
        guard !line.named.isEmpty else { return [] }
        var parts: [PeopleLinePart] = style == .line ? [.aside(with)] : []
        // "A", "A & B", "A, B & C", or "A, B" when "& N others" follows.
        let ampersandAt = line.others > 0 ? -1 : line.named.count - 1
        for (index, id) in line.named.enumerated() {
            if index > 0, style == .line {
                parts.append(index == ampersandAt ? .aside("&") : .separator(","))
            }
            parts.append(.mark(id))
            parts.append(.name(names[id] ?? ""))
        }
        if line.others > 0 { parts.append(.aside(others)) }
        parts += line.marks.map(PeopleLinePart.mark)
        if line.overflow > 0 { parts.append(.more("+\(line.overflow)")) }
        return parts
    }
}
