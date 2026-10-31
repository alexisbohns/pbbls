import Testing
import UIKit

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
}
