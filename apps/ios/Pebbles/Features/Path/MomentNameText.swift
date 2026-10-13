import SwiftUI

/// A moment's name, or an italic "Untitled" when it has none (spec D3: a
/// photo is enough). Callers set the font; the colour differs per case so an
/// untitled moment never reads as a title (R3).
struct MomentNameText: View {
    let name: String?
    var color: Color = Color.system.foreground
    var untitledColor: Color = Color.system.secondary

    var body: some View {
        if let display = MomentName.display(name) {
            // User-authored, never localized.
            Text(verbatim: display).foregroundStyle(color)
        } else {
            Text("Untitled").italic().foregroundStyle(untitledColor)
        }
    }
}
