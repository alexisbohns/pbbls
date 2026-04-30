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
        /// All descendant `<path>`s parsed and concatenated into one path.
        /// Any non-layer nested-group transforms are already baked in.
        let combinedPath: CGPath
    }

    init?(svg: String) {
        guard let data = svg.data(using: .utf8) else { return nil }
        let parser = XMLParser(data: data)
        let delegate = ParserDelegate()
        parser.delegate = delegate
        guard parser.parse(),
              !delegate.parseFailed,
              let viewBox = delegate.viewBox else {
            Logger(subsystem: "app.pbbls.ios", category: "pebble-svg")
                .info("PebbleSVGModel parse failed — falling back to static render")
            return nil
        }

        var layers: [Layer] = []
        for raw in delegate.rawLayers {
            guard !raw.paths.isEmpty else { continue }
            let combined = CGMutablePath()
            for path in raw.paths {
                combined.addPath(path)
            }
            guard !combined.boundingBoxOfPath.isNull else { continue }
            layers.append(Layer(
                kind: raw.kind,
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

    fileprivate struct RawLayer {
        let kind: Layer.Kind
        let transform: CGAffineTransform
        let opacity: Double
        let paths: [CGPath]
    }

    fileprivate struct RawGroup {
        var kind: Layer.Kind?
        var transform: CGAffineTransform = .identity
        var opacity: Double = 1.0
        // Paths attached to this <g>. Already parsed to CGPath. Inner-group
        // transforms are baked in as those groups close.
        var paths: [CGPath] = []
    }

    private final class ParserDelegate: NSObject, XMLParserDelegate {
        var viewBox: CGRect?
        var rawLayers: [RawLayer] = []
        var parseFailed = false
        private var stack: [RawGroup] = []

        func parser(
            _ parser: XMLParser,
            didStartElement elementName: String,
            namespaceURI: String?,
            qualifiedName qName: String?,
            attributes attributeDict: [String: String] = [:]
        ) {
            switch elementName {
            case "svg":
                if let viewBoxAttr = attributeDict["viewBox"] {
                    let parts = viewBoxAttr
                        .split(whereSeparator: { $0 == " " || $0 == "," })
                        .compactMap { Double($0) }
                    if parts.count == 4 {
                        viewBox = CGRect(x: parts[0], y: parts[1], width: parts[2], height: parts[3])
                    }
                }
            case "g":
                var group = RawGroup()
                if let id = attributeDict["id"] {
                    switch id {
                    case "layer:shape":  group.kind = .shape
                    case "layer:fossil": group.kind = .fossil
                    case "layer:glyph":  group.kind = .glyph
                    default: break
                    }
                }
                if let transformAttr = attributeDict["transform"] {
                    group.transform = parseTransform(transformAttr)
                }
                if let opacityAttr = attributeDict["opacity"], let value = Double(opacityAttr) {
                    group.opacity = value
                }
                stack.append(group)
            case "path":
                guard let dString = attributeDict["d"], !stack.isEmpty else { return }
                if let parsedPath = SVGPathParser.parse(dString) {
                    stack[stack.count - 1].paths.append(parsedPath)
                } else {
                    parseFailed = true
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
            guard elementName == "g", let group = stack.popLast() else { return }

            if let kind = group.kind {
                // Register the layer. Its paths already have any inner-group
                // transforms baked in; the layer's own `transform` is applied
                // by the renderer (LayerShape).
                rawLayers.append(RawLayer(
                    kind: kind,
                    transform: group.transform,
                    opacity: group.opacity,
                    paths: group.paths
                ))
            } else if !stack.isEmpty {
                // Non-layer nested group: bake this group's transform into
                // each of its paths and propagate them upward to the parent.
                var transform = group.transform
                for path in group.paths {
                    if let transformed = path.copy(using: &transform) {
                        stack[stack.count - 1].paths.append(transformed)
                    } else {
                        stack[stack.count - 1].paths.append(path)
                    }
                }
            }
            // Else: orphan non-layer group at the root with no parent — discard.
        }

        /// Parses the limited transform forms emitted by the engine:
        /// `translate(x, y) scale(s)` (commas optional), `translate(x, y)`,
        /// `scale(s)`. Other forms collapse to identity.
        ///
        /// Composition matches SVG semantics: the transform list applies
        /// left-to-right to a point, i.e. for `translate(40,40) scale(0.8)`
        /// the point is first scaled and then translated. In CG row-vector
        /// math (`p' = p * M`), that means each subsequent op is post-
        /// multiplied onto the running matrix via `op.concatenating(result)`.
        private func parseTransform(_ string: String) -> CGAffineTransform {
            var result = CGAffineTransform.identity
            let pattern = #"(translate|scale)\s*\(([^)]*)\)"#
            guard let regex = try? NSRegularExpression(pattern: pattern) else { return .identity }
            let range = NSRange(string.startIndex..<string.endIndex, in: string)
            regex.enumerateMatches(in: string, range: range) { match, _, _ in
                guard
                    let match,
                    let nameRange = Range(match.range(at: 1), in: string),
                    let argsRange = Range(match.range(at: 2), in: string)
                else { return }
                let name = String(string[nameRange])
                let args = String(string[argsRange])
                    .split(whereSeparator: { $0 == "," || $0 == " " })
                    .compactMap { Double($0) }
                switch name {
                case "translate":
                    let tx = args.first ?? 0
                    let ty = args.count > 1 ? args[1] : 0
                    result = CGAffineTransform(translationX: tx, y: ty).concatenating(result)
                case "scale":
                    let sx = args.first ?? 1
                    let sy = args.count > 1 ? args[1] : sx
                    result = CGAffineTransform(scaleX: sx, y: sy).concatenating(result)
                default:
                    break
                }
            }
            return result
        }
    }
}
