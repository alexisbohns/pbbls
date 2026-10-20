import Testing
import Foundation
import UIKit
@testable import Pebbles

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

    @Test("the variable roman is bundled and the static SemiBold is gone")
    func variableRomanReplacesStaticCut() {
        #expect(Bundle.main.url(forResource: "Ysabeau", withExtension: "ttf") != nil, "missing font: Ysabeau.ttf")
        #expect(Bundle.main.url(forResource: "Ysabeau SemiBold", withExtension: "ttf") == nil)
        #expect(UIFont(name: "Ysabeau-SemiBold", size: 17) == nil)
    }

    @Test("Ysabeau-Bold, the roman's default instance, resolves upright")
    func romanDefaultInstanceResolves() {
        let font = UIFont(name: "Ysabeau-Bold", size: 17)
        #expect(font?.fontName == "Ysabeau-Bold")
        #expect(font?.fontDescriptor.symbolicTraits.contains(.traitItalic) == false)
    }

    @Test("the italic at 380 stays italic and reads 380 on the wght axis")
    func italicTakesTheAxis() {
        let font = YsabeauFace.uiFont(size: 17, weight: 380, italic: true, features: [])
        #expect(font.fontDescriptor.symbolicTraits.contains(.traitItalic))
        #expect(wght(font) == 380)
    }
}
