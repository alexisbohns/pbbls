import CoreGraphics
import Foundation
import os

/// Typed view of a composed pebble SVG: viewBox + ordered layers, each carrying
/// its `transform` chain, opacity, and a single combined `CGPath` of all the
/// descendant `<path d="…">` strings concatenated together.
///
/// Built once on first appearance of `PebbleAnimatedRenderView`. Failure
/// (`init?` returning nil) means the caller falls back to the existing
/// `SVGView`-based static renderer.
struct PebbleSVGModel {
    let viewBox: CGRect
    let layers: [Layer]

    struct Layer {
        enum Kind { case shape, fossil, glyph }
        let kind: Kind
        /// Transform inherited from the layer's `<g transform="...">`. Identity if none.
        let transform: CGAffineTransform
        /// Layer opacity from `<g opacity="...">`. 1.0 if absent.
        let opacity: Double
        /// All descendant `<path>` `d` strings parsed and concatenated into one path.
        let combinedPath: CGPath
    }

    init?(svg: String) {
        guard let data = svg.data(using: .utf8) else { return nil }
        let parser = XMLParser(data: data)
        let delegate = ParserDelegate()
        parser.delegate = delegate
        guard parser.parse(), let viewBox = delegate.viewBox else {
            Logger(subsystem: "app.pbbls.ios", category: "pebble-svg")
                .info("PebbleSVGModel parse failed — falling back to static render")
            return nil
        }

        var layers: [Layer] = []
        for raw in delegate.rawLayers {
            guard let kind = raw.kind else { continue }
            let combined = CGMutablePath()
            for d in raw.pathDStrings {
                guard let p = SVGPathParser.parse(d) else { continue }
                combined.addPath(p)
            }
            // Reject layers with no parseable path so we don't render an empty trim.
            guard !combined.boundingBoxOfPath.isNull else { continue }

            layers.append(Layer(
                kind: kind,
                transform: raw.transform,
                opacity: raw.opacity,
                combinedPath: combined.copy() ?? combined
            ))
        }

        guard !layers.isEmpty else { return nil }
        self.viewBox = viewBox
        self.layers = layers
    }

    // MARK: - XMLParser delegate

    private final class ParserDelegate: NSObject, XMLParserDelegate {
        var viewBox: CGRect?
        var rawLayers: [RawLayer] = []
        private var stack: [RawLayer] = []

        struct RawLayer {
            var kind: Layer.Kind?
            var transform: CGAffineTransform = .identity
            var opacity: Double = 1.0
            var pathDStrings: [String] = []
        }

        func parser(
            _ parser: XMLParser,
            didStartElement elementName: String,
            namespaceURI: String?,
            qualifiedName qName: String?,
            attributes attributeDict: [String: String] = [:]
        ) {
            switch elementName {
            case "svg":
                if let vb = attributeDict["viewBox"] {
                    let parts = vb.split(whereSeparator: { $0 == " " || $0 == "," }).compactMap { Double($0) }
                    if parts.count == 4 {
                        viewBox = CGRect(x: parts[0], y: parts[1], width: parts[2], height: parts[3])
                    }
                }
            case "g":
                var layer = RawLayer()
                if let id = attributeDict["id"] {
                    switch id {
                    case "layer:shape":  layer.kind = .shape
                    case "layer:fossil": layer.kind = .fossil
                    case "layer:glyph":  layer.kind = .glyph
                    default: break
                    }
                }
                if let t = attributeDict["transform"] {
                    layer.transform = parseTransform(t)
                }
                if let o = attributeDict["opacity"], let v = Double(o) {
                    layer.opacity = v
                }
                stack.append(layer)
            case "path":
                if let d = attributeDict["d"], !stack.isEmpty {
                    stack[stack.count - 1].pathDStrings.append(d)
                }
            default:
                break
            }
        }

        func parser(
            _ parser: XMLParser,
            didEndElement elementName: String,
            namespaceURI: String?,
            qualifiedName qName: String?
        ) {
            if elementName == "g", let layer = stack.popLast(), layer.kind != nil {
                rawLayers.append(layer)
            }
        }

        /// Parses the limited transform forms emitted by the engine:
        /// `translate(x, y) scale(s)` (commas optional), `translate(x, y)`,
        /// `scale(s)`. Other forms collapse to identity.
        private func parseTransform(_ s: String) -> CGAffineTransform {
            var t = CGAffineTransform.identity
            let pattern = #"(translate|scale)\s*\(([^)]*)\)"#
            guard let regex = try? NSRegularExpression(pattern: pattern) else { return .identity }
            let range = NSRange(s.startIndex..<s.endIndex, in: s)
            regex.enumerateMatches(in: s, range: range) { match, _, _ in
                guard
                    let match,
                    let nameRange = Range(match.range(at: 1), in: s),
                    let argsRange = Range(match.range(at: 2), in: s)
                else { return }
                let name = String(s[nameRange])
                let args = String(s[argsRange])
                    .split(whereSeparator: { $0 == "," || $0 == " " })
                    .compactMap { Double($0) }
                // SVG transform list applies left-to-right to a point: e.g.
                // `translate(40,40) scale(0.8)` maps (x,y) → (0.8x+40, 0.8y+40).
                // In CG (row-vector) terms this means each subsequent op is
                // prepended (post-multiplied) onto the running transform.
                switch name {
                case "translate":
                    let tx = args.first ?? 0
                    let ty = args.count > 1 ? args[1] : 0
                    t = CGAffineTransform(translationX: tx, y: ty).concatenating(t)
                case "scale":
                    let sx = args.first ?? 1
                    let sy = args.count > 1 ? args[1] : sx
                    t = CGAffineTransform(scaleX: sx, y: sy).concatenating(t)
                default:
                    break
                }
            }
            return t
        }
    }
}
