import Testing
import Foundation

@Suite
struct OutlineAssetSentinelTests {

    private static let names: [String] = [
        "small-neutral",   "small-lowlight",   "small-highlight",
        "medium-neutral",  "medium-lowlight",  "medium-highlight",
        "large-neutral",   "large-lowlight",   "large-highlight",
    ]

    /// Locate the Pebbles app bundle from the test runner host.
    private static var appBundle: Bundle? {
        Bundle.allBundles.first { $0.bundleIdentifier?.hasPrefix("app.pbbls.ios") == true && !$0.bundleIdentifier!.contains("test") }
    }

    private static func svgURL(for name: String) -> URL? {
        // Try app bundle first; fall back to Bundle.main for in-app contexts.
        if let url = appBundle?.url(forResource: name, withExtension: "svg") { return url }
        return Bundle.main.url(forResource: name, withExtension: "svg")
    }

    @Test("Each outline asset is bundled and parseable")
    func assetsExist() throws {
        for name in Self.names {
            let url = Self.svgURL(for: name)
            #expect(url != nil, "missing asset: \(name).svg")
        }
    }

    @Test("Each outline contains exactly one #FF00FF sentinel")
    func sentinelExactlyOnce() throws {
        for name in Self.names {
            let url = try #require(Self.svgURL(for: name), "missing asset: \(name).svg")
            let body = try String(contentsOf: url, encoding: .utf8)
            let count = body.components(separatedBy: "#FF00FF").count - 1
            #expect(count == 1, "\(name).svg expected 1 sentinel, got \(count)")
        }
    }
}
