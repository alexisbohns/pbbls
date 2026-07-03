import Testing
import Foundation
import UIKit

/// Guards the handwritten name face (issue #515). The failure mode of a font
/// change is silent: a wrong PostScript name or an unbundled file falls back to
/// the system font with no build error. Assert the TTF is bundled and that the
/// `ReenieBeanie` PostScript name actually resolves.
@Suite
struct ReenieBeanieFontTests {

    @Test("Reenie Beanie TTF is bundled")
    func ttfIsBundled() throws {
        let url = Bundle.main.url(forResource: "ReenieBeanie-Regular", withExtension: "ttf")
        #expect(url != nil, "missing font: ReenieBeanie-Regular.ttf")
    }

    @Test("ReenieBeanie PostScript name resolves to a font")
    func postScriptNameResolves() throws {
        let font = UIFont(name: "ReenieBeanie", size: 22)
        // nil here means the PostScript name is wrong or the font isn't registered.
        #expect(font != nil, "UIFont(name: \"ReenieBeanie\") returned nil")
        #expect(font?.fontName == "ReenieBeanie")
    }
}
