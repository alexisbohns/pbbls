import CoreGraphics
import Foundation
import os

/// Typed view of the bundled `WelcomeLogo.svg`: viewBox + ordered list of
/// `CGPath`s in document order. Used by `SplashView` to draw the logo
/// stroke-by-stroke via `.trim(from: 0, to: progress).stroke(...)`.
///
/// Unlike `PebbleSVGModel`, there are no layer kinds — the welcome logo is
/// one ordered stream of strokes. Reuses `SVGPathParser`. Returns nil if
/// the SVG is unparseable, in which case `SplashView` falls back to a
/// static asset render.
struct LogoSVGModel {
    let viewBox: CGRect
    let paths: [CGPath]

    init?(svg: String) {
        guard let data = svg.data(using: .utf8) else { return nil }
        let parser = XMLParser(data: data)
        let delegate = ParserDelegate()
        parser.delegate = delegate
        guard parser.parse(),
              !delegate.parseFailed,
              let viewBox = delegate.viewBox,
              !delegate.paths.isEmpty else {
            Logger(subsystem: "app.pbbls.ios", category: "splash")
                .error("LogoSVGModel parse failed — splash will fall back to static asset")
            return nil
        }
        self.viewBox = viewBox
        self.paths = delegate.paths
    }

    /// Loads `WelcomeLogo.svg` from the app bundle.
    static func loadFromBundle(_ bundle: Bundle = .main) -> LogoSVGModel? {
        guard let url = bundle.url(forResource: "WelcomeLogo", withExtension: "svg"),
              let svg = try? String(contentsOf: url, encoding: .utf8) else {
            Logger(subsystem: "app.pbbls.ios", category: "splash")
                .error("LogoSVGModel: WelcomeLogo.svg not found in bundle")
            return nil
        }
        return LogoSVGModel(svg: svg)
    }

    // MARK: - XMLParser delegate

    private final class ParserDelegate: NSObject, XMLParserDelegate {
        var viewBox: CGRect?
        var paths: [CGPath] = []
        var parseFailed = false

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
            case "path":
                guard let dString = attributeDict["d"] else { return }
                if let parsed = SVGPathParser.parse(dString) {
                    paths.append(parsed)
                } else {
                    parseFailed = true
                }
            default:
                break
            }
        }
    }
}
