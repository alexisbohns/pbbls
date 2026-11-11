import CoreGraphics

/// Reads the normalised path data of `pbbls-glyphs.json` (spec D7): absolute
/// `M L C Q Z`, numbers split by spaces, commas or a sign. A command's extra
/// coordinate groups repeat it, an `M`'s as `L` (SVG 1.1 §8.3.2). Any other
/// command is skipped: the generator never writes one
/// (`packages/icons/fixtures/path-contract.json` is the contract).
enum GlyphPathParser {
    enum Segment: Equatable, Sendable {
        case move(CGPoint)
        case line(CGPoint)
        case cubic(CGPoint, CGPoint, CGPoint)
        case quad(CGPoint, CGPoint)
        case close
    }

    static func segments(_ d: String) -> [Segment] {
        var out: [Segment] = []
        for token in tokens(d) {
            let n = token.numbers
            let points = stride(from: 0, to: n.count - 1, by: 2).map { CGPoint(x: n[$0], y: n[$0 + 1]) }
            switch token.command {
            case "M":
                for (i, point) in points.enumerated() {
                    out.append(i == 0 ? Segment.move(point) : Segment.line(point))
                }
            case "L":
                for point in points { out.append(Segment.line(point)) }
            case "C", "Q":
                out += curves(token.command, points)
            case "Z":
                out.append(.close)
            default:
                continue
            }
        }
        return out
    }

    private static func curves(_ command: Character, _ points: [CGPoint]) -> [Segment] {
        var out: [Segment] = []
        if command == "C" {
            for i in stride(from: 0, to: points.count - 2, by: 3) {
                out.append(Segment.cubic(points[i], points[i + 1], points[i + 2]))
            }
        } else {
            for i in stride(from: 0, to: points.count - 1, by: 2) {
                out.append(Segment.quad(points[i], points[i + 1]))
            }
        }
        return out
    }

    static func cgPath(_ d: String) -> CGPath {
        let path = CGMutablePath()
        for segment in segments(d) {
            switch segment {
            case .move(let p): path.move(to: p)
            case .line(let p): path.addLine(to: p)
            case .cubic(let c1, let c2, let p): path.addCurve(to: p, control1: c1, control2: c2)
            case .quad(let c, let p): path.addQuadCurve(to: p, control: c)
            case .close: path.closeSubpath()
            }
        }
        return path
    }

    private struct Token {
        let command: Character
        var numbers: [CGFloat]
    }

    private static func tokens(_ d: String) -> [Token] {
        var out: [Token] = []
        var number = ""
        func flush() {
            defer { number = "" }
            guard !number.isEmpty, let value = Double(number), !out.isEmpty else { return }
            out[out.count - 1].numbers.append(CGFloat(value))
        }
        for ch in d {
            switch ch {
            case "M", "L", "C", "Q", "Z", "m", "l", "c", "q", "z", "A", "a", "H", "h", "V", "v", "S", "s", "T", "t":
                flush()
                out.append(Token(command: ch, numbers: []))
            case "-":
                if !number.isEmpty && !number.hasSuffix("e") { flush() }
                number.append(ch)
            case ".":
                if number.contains(".") && !number.contains("e") { flush() }
                number.append(ch)
            case "e", "0"..."9":
                number.append(ch)
            default:
                flush()
            }
        }
        flush()
        return out
    }
}
