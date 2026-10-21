import SwiftUI

/// A moment's name, or an italic "Untitled" when it has none (spec D3: a
/// photo is enough). Callers set the font; the colour differs per case so an
/// untitled moment never reads as a title (R3).
///
/// `.italic()` only works on a font with an italic face. Callers that set
/// Ysabeau pass its true italic as `untitledFont`
/// (`.ysabeauSemiboldItalic(_:)`); without one the caller's font is italicised.
struct MomentNameText: View {
    let name: String?
    var color: Color = Color.pebbles.onSurface
    var untitledColor: Color = Color.pebbles.onSurfaceVariant
    var untitledFont: Font? = nil

    var body: some View {
        if let display = MomentName.display(name) {
            // User-authored, never localized.
            Text(verbatim: display).foregroundStyle(color)
        } else if let untitledFont {
            Text("Untitled")
                .font(untitledFont)
                .foregroundStyle(untitledColor)
        } else {
            Text("Untitled")
                .italic()
                .foregroundStyle(untitledColor)
        }
    }
}
