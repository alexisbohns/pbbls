import Testing
import Foundation
@testable import Pebbles

@Suite
struct LogoLoaderArtTests {

    @Test("Loader SVG is bundled")
    func assetBundled() throws {
        let url = Bundle.main.url(forResource: "pbbls-logo-loader", withExtension: "svg")
        #expect(url != nil, "missing pbbls-logo-loader.svg")
    }
}
