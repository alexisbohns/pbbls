import SwiftUI

/// A moment's name, or an italic "Untitled" when it has none (spec D3: a
/// photo is enough). Callers set the font; the colour differs per case so an
/// untitled moment never reads as a title (R3).
///
/// The bundled Ysabeau is SemiBold only, so `.italic()` on it renders
/// upright. Callers that set Ysabeau pass `slantsUntitled` and the label is
/// slanted instead.
struct MomentNameText: View {
    let name: String?
    var color: Color = Color.system.foreground
    var untitledColor: Color = Color.system.secondary
    var slantsUntitled = false

    var body: some View {
        if let display = MomentName.display(name) {
            // User-authored, never localized.
            Text(verbatim: display).foregroundStyle(color)
        } else {
            Text("Untitled")
                .italic()
                .foregroundStyle(untitledColor)
                .modifier(ObliqueEffect(isActive: slantsUntitled))
        }
    }
}

/// A synthetic oblique for a font without an italic face: shears the text
/// about its vertical centre, the top leaning right.
private struct ObliqueEffect: GeometryEffect {
    let isActive: Bool

    var animatableData: EmptyAnimatableData {
        get { EmptyAnimatableData() }
        set { _ = newValue }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        guard isActive else { return ProjectionTransform() }
        let slant = tan(12 * CGFloat.pi / 180)
        return ProjectionTransform(
            CGAffineTransform(a: 1, b: 0, c: -slant, d: 1, tx: slant * size.height / 2, ty: 0)
        )
    }
}
