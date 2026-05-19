import Testing
import Foundation

@Suite
struct OutlineAssetSentinelTests {

    private static let names: [String] = [
        "small-neutral",   "small-lowlight",   "small-highlight",
        "medium-neutral",  "medium-lowlight",  "medium-highlight",
        "large-neutral",   "large-lowlight",   "large-highlight",
    ]

    @Test("Each outline asset is bundled and parseable")
    func assetsExist() throws {
        for name in Self.names {
            let url = Bundle.main.url(forResource: name, withExtension: "svg")
            #expect(url != nil, "missing asset: \(name).svg")
        }
    }

    @Test("Each outline contains exactly one #FF00FF sentinel")
    func sentinelExactlyOnce() throws {
        for name in Self.names {
            let url = try #require(Bundle.main.url(forResource: name, withExtension: "svg"))
            let body = try String(contentsOf: url, encoding: .utf8)
            let count = body.components(separatedBy: "#FF00FF").count - 1
            #expect(count == 1, "\(name).svg expected 1 sentinel, got \(count)")
        }
    }
}
