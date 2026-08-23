import Testing
import Foundation
import UIKit

/// Guards the Caveat name-input face. The failure mode of a font change is
/// silent: a wrong PostScript name or an unbundled file falls back to the
/// system font with no build error. Assert the TTF is bundled and that the
/// `Caveat-Regular` PostScript name actually resolves.
@Suite
struct CaveatFontTests {

    @Test("Caveat TTF is bundled")
    func ttfIsBundled() throws {
        let url = Bundle.main.url(forResource: "Caveat-VariableFont_wght", withExtension: "ttf")
        #expect(url != nil, "missing font: Caveat-VariableFont_wght.ttf")
    }

    @Test("Caveat-Regular PostScript name resolves to a font")
    func postScriptNameResolves() throws {
        let font = UIFont(name: "Caveat-Regular", size: 36)
        // nil here means the PostScript name is wrong or the font isn't registered.
        #expect(font != nil, "UIFont(name: \"Caveat-Regular\") returned nil")
        #expect(font?.fontName == "Caveat-Regular")
    }
}
