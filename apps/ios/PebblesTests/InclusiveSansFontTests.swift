import Testing
import UIKit
@testable import Pebbles

/// The body face (#1382 D2). A wrong PostScript name or an unbundled file falls
/// back to the system font with no build error, so the bundle and the names are proved.
@Suite struct InclusiveSansFontTests {
    @Test("both variable files and the OFL are bundled",
          arguments: [("InclusiveSans-VariableFont_wght", "ttf"), ("InclusiveSans-Italic-VariableFont_wght", "ttf"),
                      ("InclusiveSans-OFL", "txt")])
    func bundled(name: String, ext: String) {
        #expect(Bundle.main.url(forResource: name, withExtension: ext) != nil, "missing \(name).\(ext)")
    }

    @Test("the roman and the italic resolve", arguments: ["InclusiveSans-Regular", "InclusiveSans-Italic"])
    func resolves(name: String) {
        #expect(UIFont(name: name, size: 17)?.familyName == "Inclusive Sans")
    }

    @Test("300, 400, 600 and 700 land on the wght axis and thicken the stem")
    func weightsApply() throws {
        let weights = [300, 400, 600, 700]
        let fonts = weights.map { InclusiveFace.uiFont(size: 17, weight: $0, italic: false) }
        #expect(fonts.map(CTProbe.wght) == [300, 400, 600, 700])
        let stems = try fonts.map { try CTProbe.inkWidth("l", font: $0) }
        for index in 1..<stems.count {
            #expect(stems[index - 1] < stems[index], "the stem did not thicken: \(stems)")
        }
    }
}
