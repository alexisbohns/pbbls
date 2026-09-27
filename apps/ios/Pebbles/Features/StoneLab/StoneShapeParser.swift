#if DEBUG
import CoreGraphics
import Foundation

/// Splits an engine shape seed (`docs/seeds/shape-seeds/*.svg`) into the parts
/// the stone lab carves separately. The engine emits no ids, only order: the
/// first stroked `<path>` is the outline, every later stroked path is a vein,
/// and the single filled path is the fossil.
enum StoneShapeParser {

    struct Stroke: Equatable {
        // swiftlint:disable:next identifier_name
        let d: String
        /// `stroke-width` in viewBox units; the engine always writes 6.
        let width: Double
    }

    struct Fill: Equatable {
        // swiftlint:disable:next identifier_name
        let d: String
        let usesEvenOddFill: Bool
    }

    struct Parsed: Equatable {
        let viewBox: CGRect
        let outline: Stroke
        let veins: [Stroke]
        let fossil: Fill?
    }

    private static let pathPattern = try? NSRegularExpression(pattern: "<path\\s+([^>]*?)/?>")
    private static let attributePattern = try? NSRegularExpression(pattern: "([\\w-]+)=\"([^\"]*)\"")
    private static let viewBoxPattern = try? NSRegularExpression(pattern: "viewBox=\"([^\"]*)\"")

    static func parse(_ svg: String) -> Parsed? {
        guard let viewBox = viewBox(in: svg) else { return nil }
        var strokes: [Stroke] = []
        var fossil: Fill?
        for attributes in pathAttributes(in: svg) {
            guard let d = attributes["d"] else { continue }
            let fill = attributes["fill"]
            if let fill, fill != "none" {
                // Keep the first filled path only: the engine emits one fossil.
                if fossil == nil {
                    fossil = Fill(d: d, usesEvenOddFill: attributes["fill-rule"] == "evenodd")
                }
            } else if attributes["stroke"] != nil {
                strokes.append(Stroke(d: d, width: Double(attributes["stroke-width"] ?? "6") ?? 6))
            }
        }
        guard let outline = strokes.first else { return nil }
        return Parsed(viewBox: viewBox, outline: outline, veins: Array(strokes.dropFirst()), fossil: fossil)
    }

    private static func viewBox(in svg: String) -> CGRect? {
        guard let viewBoxPattern,
              let match = viewBoxPattern.firstMatch(in: svg, range: NSRange(svg.startIndex..., in: svg)),
              let range = Range(match.range(at: 1), in: svg) else { return nil }
        let parts = svg[range].split(whereSeparator: { $0 == " " || $0 == "," }).compactMap { Double($0) }
        guard parts.count == 4 else { return nil }
        return CGRect(x: parts[0], y: parts[1], width: parts[2], height: parts[3])
    }

    private static func pathAttributes(in svg: String) -> [[String: String]] {
        guard let pathPattern, let attributePattern else { return [] }
        let whole = NSRange(svg.startIndex..., in: svg)
        return pathPattern.matches(in: svg, range: whole).compactMap { match in
            guard let range = Range(match.range(at: 1), in: svg) else { return nil }
            let body = String(svg[range])
            var attributes: [String: String] = [:]
            for attr in attributePattern.matches(in: body, range: NSRange(body.startIndex..., in: body)) {
                guard let key = Range(attr.range(at: 1), in: body),
                      let value = Range(attr.range(at: 2), in: body) else { continue }
                attributes[String(body[key])] = String(body[value])
            }
            return attributes
        }
    }
}
#endif
