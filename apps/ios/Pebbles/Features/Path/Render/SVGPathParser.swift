import CoreGraphics
import Foundation

/// Parses an SVG path `d` attribute string into a CGPath.
///
/// Supports the standard SVG path commands `M m L l H h V v C c S s Q q T t A a Z z`
/// with implicit-command continuation (e.g. `M 0 0 10 10 20 20` is an
/// implicit `L` after the first `M`). Whitespace, commas, and signed
/// numbers are accepted. Returns `nil` when the string contains no
/// recognizable command or cannot be tokenized.
enum SVGPathParser {

    static func parse(_ d: String) -> CGPath? {
        let tokens = tokenize(d)
        guard !tokens.isEmpty else { return nil }

        let path = CGMutablePath()

        var current = CGPoint.zero
        var subpathStart = CGPoint.zero
        var lastControl: CGPoint? = nil   // for S/T smoothing
        var lastCommand: Character = "M"

        var i = 0
        while i < tokens.count {
            guard case let .command(cmdChar) = tokens[i] else { return nil }
            i += 1

            // Pull all numeric tokens after this command.
            var args: [CGFloat] = []
            while i < tokens.count, case let .number(value) = tokens[i] {
                args.append(value)
                i += 1
            }

            // Step through args repeating the command as needed (implicit continuation).
            var argIndex = 0
            var firstIteration = true

            repeat {
                let activeCmd: Character
                if firstIteration {
                    activeCmd = cmdChar
                    firstIteration = false
                } else {
                    // Implicit M continues as L; m continues as l. Others repeat themselves.
                    switch cmdChar {
                    case "M": activeCmd = "L"
                    case "m": activeCmd = "l"
                    default: activeCmd = cmdChar
                    }
                }

                switch activeCmd {
                case "M":
                    guard argIndex + 1 < args.count else { return nil }
                    current = CGPoint(x: args[argIndex], y: args[argIndex + 1])
                    subpathStart = current
                    path.move(to: current)
                    argIndex += 2
                case "m":
                    guard argIndex + 1 < args.count else { return nil }
                    current = CGPoint(x: current.x + args[argIndex], y: current.y + args[argIndex + 1])
                    subpathStart = current
                    path.move(to: current)
                    argIndex += 2
                case "L":
                    guard argIndex + 1 < args.count else { return nil }
                    current = CGPoint(x: args[argIndex], y: args[argIndex + 1])
                    path.addLine(to: current)
                    argIndex += 2
                case "l":
                    guard argIndex + 1 < args.count else { return nil }
                    current = CGPoint(x: current.x + args[argIndex], y: current.y + args[argIndex + 1])
                    path.addLine(to: current)
                    argIndex += 2
                case "H":
                    guard argIndex < args.count else { return nil }
                    current = CGPoint(x: args[argIndex], y: current.y)
                    path.addLine(to: current)
                    argIndex += 1
                case "h":
                    guard argIndex < args.count else { return nil }
                    current = CGPoint(x: current.x + args[argIndex], y: current.y)
                    path.addLine(to: current)
                    argIndex += 1
                case "V":
                    guard argIndex < args.count else { return nil }
                    current = CGPoint(x: current.x, y: args[argIndex])
                    path.addLine(to: current)
                    argIndex += 1
                case "v":
                    guard argIndex < args.count else { return nil }
                    current = CGPoint(x: current.x, y: current.y + args[argIndex])
                    path.addLine(to: current)
                    argIndex += 1
                case "C":
                    guard argIndex + 5 < args.count else { return nil }
                    let c1 = CGPoint(x: args[argIndex],     y: args[argIndex + 1])
                    let c2 = CGPoint(x: args[argIndex + 2], y: args[argIndex + 3])
                    let end = CGPoint(x: args[argIndex + 4], y: args[argIndex + 5])
                    path.addCurve(to: end, control1: c1, control2: c2)
                    lastControl = c2
                    current = end
                    argIndex += 6
                case "c":
                    guard argIndex + 5 < args.count else { return nil }
                    let c1 = CGPoint(x: current.x + args[argIndex],     y: current.y + args[argIndex + 1])
                    let c2 = CGPoint(x: current.x + args[argIndex + 2], y: current.y + args[argIndex + 3])
                    let end = CGPoint(x: current.x + args[argIndex + 4], y: current.y + args[argIndex + 5])
                    path.addCurve(to: end, control1: c1, control2: c2)
                    lastControl = c2
                    current = end
                    argIndex += 6
                case "S":
                    guard argIndex + 3 < args.count else { return nil }
                    let c1 = reflectedControl(current: current, lastControl: lastControl, lastCommand: lastCommand, isCubicLike: true)
                    let c2 = CGPoint(x: args[argIndex],     y: args[argIndex + 1])
                    let end = CGPoint(x: args[argIndex + 2], y: args[argIndex + 3])
                    path.addCurve(to: end, control1: c1, control2: c2)
                    lastControl = c2
                    current = end
                    argIndex += 4
                case "s":
                    guard argIndex + 3 < args.count else { return nil }
                    let c1 = reflectedControl(current: current, lastControl: lastControl, lastCommand: lastCommand, isCubicLike: true)
                    let c2 = CGPoint(x: current.x + args[argIndex],     y: current.y + args[argIndex + 1])
                    let end = CGPoint(x: current.x + args[argIndex + 2], y: current.y + args[argIndex + 3])
                    path.addCurve(to: end, control1: c1, control2: c2)
                    lastControl = c2
                    current = end
                    argIndex += 4
                case "Q":
                    guard argIndex + 3 < args.count else { return nil }
                    let c = CGPoint(x: args[argIndex],     y: args[argIndex + 1])
                    let end = CGPoint(x: args[argIndex + 2], y: args[argIndex + 3])
                    path.addQuadCurve(to: end, control: c)
                    lastControl = c
                    current = end
                    argIndex += 4
                case "q":
                    guard argIndex + 3 < args.count else { return nil }
                    let c = CGPoint(x: current.x + args[argIndex],     y: current.y + args[argIndex + 1])
                    let end = CGPoint(x: current.x + args[argIndex + 2], y: current.y + args[argIndex + 3])
                    path.addQuadCurve(to: end, control: c)
                    lastControl = c
                    current = end
                    argIndex += 4
                case "T":
                    guard argIndex + 1 < args.count else { return nil }
                    let c = reflectedControl(current: current, lastControl: lastControl, lastCommand: lastCommand, isCubicLike: false)
                    let end = CGPoint(x: args[argIndex], y: args[argIndex + 1])
                    path.addQuadCurve(to: end, control: c)
                    lastControl = c
                    current = end
                    argIndex += 2
                case "t":
                    guard argIndex + 1 < args.count else { return nil }
                    let c = reflectedControl(current: current, lastControl: lastControl, lastCommand: lastCommand, isCubicLike: false)
                    let end = CGPoint(x: current.x + args[argIndex], y: current.y + args[argIndex + 1])
                    path.addQuadCurve(to: end, control: c)
                    lastControl = c
                    current = end
                    argIndex += 2
                case "A":
                    guard argIndex + 6 < args.count else { return nil }
                    let rx = args[argIndex]
                    let ry = args[argIndex + 1]
                    let xAxisRot = args[argIndex + 2]
                    let largeArc = args[argIndex + 3] != 0
                    let sweep = args[argIndex + 4] != 0
                    let end = CGPoint(x: args[argIndex + 5], y: args[argIndex + 6])
                    addArc(to: path, from: current, to: end, rx: rx, ry: ry, xAxisRotationDeg: xAxisRot, largeArc: largeArc, sweep: sweep)
                    current = end
                    lastControl = nil
                    argIndex += 7
                case "a":
                    guard argIndex + 6 < args.count else { return nil }
                    let rx = args[argIndex]
                    let ry = args[argIndex + 1]
                    let xAxisRot = args[argIndex + 2]
                    let largeArc = args[argIndex + 3] != 0
                    let sweep = args[argIndex + 4] != 0
                    let end = CGPoint(x: current.x + args[argIndex + 5], y: current.y + args[argIndex + 6])
                    addArc(to: path, from: current, to: end, rx: rx, ry: ry, xAxisRotationDeg: xAxisRot, largeArc: largeArc, sweep: sweep)
                    current = end
                    lastControl = nil
                    argIndex += 7
                case "Z", "z":
                    path.closeSubpath()
                    current = subpathStart
                    lastControl = nil
                default:
                    return nil
                }

                lastCommand = activeCmd

                // Z/z take no arguments; bail out of the inner loop.
                if activeCmd == "Z" || activeCmd == "z" { break }
            } while argIndex < args.count
        }

        return path.copy()
    }

    // MARK: - Tokenizer

    private enum Token { case command(Character), number(CGFloat) }

    private static func tokenize(_ s: String) -> [Token] {
        var tokens: [Token] = []
        let scalars = Array(s.unicodeScalars)
        var i = 0
        while i < scalars.count {
            let c = scalars[i]
            if isCommandChar(c) {
                tokens.append(.command(Character(c)))
                i += 1
            } else if c == " " || c == "\t" || c == "\n" || c == "\r" || c == "," {
                i += 1
            } else if c == "+" || c == "-" || c == "." || (c.value >= 48 && c.value <= 57) {
                // Number — read until the next non-numeric character.
                let start = i
                if c == "+" || c == "-" { i += 1 }
                var seenDot = false
                var seenExp = false
                while i < scalars.count {
                    let ch = scalars[i]
                    if ch.value >= 48 && ch.value <= 57 {
                        i += 1
                    } else if ch == "." && !seenDot && !seenExp {
                        seenDot = true; i += 1
                    } else if (ch == "e" || ch == "E") && !seenExp {
                        seenExp = true; i += 1
                        if i < scalars.count, scalars[i] == "+" || scalars[i] == "-" { i += 1 }
                    } else {
                        break
                    }
                }
                let str = String(String.UnicodeScalarView(scalars[start..<i]))
                if let n = Double(str) {
                    tokens.append(.number(CGFloat(n)))
                } else {
                    return []
                }
            } else {
                // Unknown character — abort.
                return []
            }
        }
        return tokens
    }

    private static func isCommandChar(_ c: Unicode.Scalar) -> Bool {
        switch Character(c) {
        case "M", "m", "L", "l", "H", "h", "V", "v",
             "C", "c", "S", "s", "Q", "q", "T", "t",
             "A", "a", "Z", "z":
            return true
        default:
            return false
        }
    }

    // MARK: - Smooth-curve control reflection

    private static func reflectedControl(
        current: CGPoint,
        lastControl: CGPoint?,
        lastCommand: Character,
        isCubicLike: Bool
    ) -> CGPoint {
        // S/s reflects only after C/c/S/s; T/t reflects only after Q/q/T/t.
        let qualifies: Bool
        if isCubicLike {
            qualifies = "CcSs".contains(lastCommand)
        } else {
            qualifies = "QqTt".contains(lastCommand)
        }
        guard qualifies, let last = lastControl else { return current }
        return CGPoint(x: 2 * current.x - last.x, y: 2 * current.y - last.y)
    }

    // MARK: - Arc

    /// Adds an SVG-style elliptical arc to the path. Implements the endpoint-to-center
    /// parameterization from the SVG 1.1 spec, then emits the arc as a CGPath arc.
    private static func addArc(
        to path: CGMutablePath,
        from p0: CGPoint,
        to p1: CGPoint,
        rx rxIn: CGFloat,
        ry ryIn: CGFloat,
        xAxisRotationDeg: CGFloat,
        largeArc: Bool,
        sweep: Bool
    ) {
        // Same point → no arc.
        if abs(p0.x - p1.x) < 1e-6 && abs(p0.y - p1.y) < 1e-6 { return }

        // Zero radius → straight line per spec.
        if rxIn == 0 || ryIn == 0 {
            path.addLine(to: p1)
            return
        }

        let phi = xAxisRotationDeg * .pi / 180
        var rx = abs(rxIn)
        var ry = abs(ryIn)

        // F.6.5 step 1
        let dx2 = (p0.x - p1.x) / 2
        let dy2 = (p0.y - p1.y) / 2
        let cosPhi = cos(phi)
        let sinPhi = sin(phi)
        let x1p =  cosPhi * dx2 + sinPhi * dy2
        let y1p = -sinPhi * dx2 + cosPhi * dy2

        // F.6.6 — ensure radii large enough.
        let lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if lambda > 1 {
            let s = sqrt(lambda)
            rx *= s
            ry *= s
        }

        // F.6.5 step 2 — center in primed coords.
        let signFactor: CGFloat = largeArc == sweep ? -1 : 1
        let num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        let denom = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        let coeff = signFactor * sqrt(max(0, num / denom))
        let cxp =  coeff * (rx * y1p / ry)
        let cyp = -coeff * (ry * x1p / rx)

        // Center back in user coords.
        let cx = cosPhi * cxp - sinPhi * cyp + (p0.x + p1.x) / 2
        let cy = sinPhi * cxp + cosPhi * cyp + (p0.y + p1.y) / 2

        // Start/end angles.
        func ang(_ ux: CGFloat, _ uy: CGFloat, _ vx: CGFloat, _ vy: CGFloat) -> CGFloat {
            let dot = ux * vx + uy * vy
            let len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
            var v = dot / len
            v = max(-1, min(1, v))
            let s = ux * vy - uy * vx
            return (s < 0 ? -1 : 1) * acos(v)
        }

        let theta1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var deltaTheta = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if !sweep && deltaTheta > 0 { deltaTheta -= 2 * .pi }
        if  sweep && deltaTheta < 0 { deltaTheta += 2 * .pi }

        // Build a transform that places a unit circle at the ellipse center,
        // scales it to (rx, ry), and rotates by phi.
        let t = CGAffineTransform(translationX: cx, y: cy)
            .rotated(by: phi)
            .scaledBy(x: rx, y: ry)

        // Add the arc on the unit circle through the transform.
        path.addArc(
            center: .zero,
            radius: 1,
            startAngle: theta1,
            endAngle: theta1 + deltaTheta,
            clockwise: !sweep,
            transform: t
        )
    }
}

private extension CGPath {
    var isEmpty: Bool { boundingBoxOfPath.isNull || boundingBoxOfPath.isEmpty }
}
