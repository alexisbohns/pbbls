import Testing
import Foundation
import UIKit

/// Guards the Ysabeau italic face used for an untitled moment (R3). A wrong
/// PostScript name or an unbundled file falls back silently to an upright
/// face, so assert the TTF is bundled and `Ysabeau-BoldItalic` (the variable
/// font's default instance) resolves.
@Suite
struct YsabeauItalicFontTests {

    @Test("Ysabeau Italic TTF is bundled")
    func ttfIsBundled() {
        let url = Bundle.main.url(forResource: "Ysabeau Italic", withExtension: "ttf")
        #expect(url != nil, "missing font: Ysabeau Italic.ttf")
    }

    @Test("Ysabeau-BoldItalic PostScript name resolves to an italic font")
    func postScriptNameResolves() {
        let font = UIFont(name: "Ysabeau-BoldItalic", size: 17)
        #expect(font?.fontName == "Ysabeau-BoldItalic")
        #expect(font?.fontDescriptor.symbolicTraits.contains(.traitItalic) == true)
    }
}
