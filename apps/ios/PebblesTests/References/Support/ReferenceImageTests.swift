import CoreGraphics
import SwiftUI
import Testing
import UIKit
@testable import Pebbles

@Suite("Reference harness")
struct ReferenceImageTests {

    @Test("file names cover every scheme, locale and Dynamic Type combination")
    func fileNames() {
        let expected: [(ReferenceScheme, ReferenceLocale, DynamicTypeSize, String)] = [
            (.paper, .en, .large, "slide-paper-en.png"),
            (.paper, .fr, .large, "slide-paper-fr.png"),
            (.night, .en, .large, "slide-night-en.png"),
            (.night, .fr, .large, "slide-night-fr.png"),
            (.paper, .en, .accessibility2, "slide-paper-en-ax2.png"),
            (.paper, .fr, .accessibility2, "slide-paper-fr-ax2.png"),
            (.night, .en, .accessibility2, "slide-night-en-ax2.png"),
            (.night, .fr, .accessibility2, "slide-night-fr-ax2.png")
        ]
        for (scheme, locale, dynamicType, name) in expected {
            let config = ReferenceConfig(scheme: scheme, locales: [locale], dynamicType: dynamicType)
            #expect(config.fileName("slide", locale: locale) == name)
        }
    }

    @Test("a reference only runs in the passes it declares")
    func localeSkip() {
        let englishOnly = ReferenceConfig(scheme: .paper, locales: [.en])
        #expect(englishOnly.runs(in: .en))
        #expect(!englishOnly.runs(in: .fr))
        let both = ReferenceConfig(scheme: .night, locales: [.en, .fr])
        #expect(both.runs(in: .fr))
    }

    @Test("the reference width is the iPhone 15's 393 pt")
    func defaultWidth() {
        #expect(ReferenceConfig(scheme: .paper).width == 393)
        #expect(ReferenceConfig(scheme: .paper).dynamicType == .large)
    }
}
