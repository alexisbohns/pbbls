import SwiftUI

/// The collection's glyph row (R11), shared by create and edit: tap to pick
/// through the shared glyph picker, **Remove glyph** when one is set, and the
/// line saying what the glyph does. Pebbles in the collection inherit it (D5).
struct CollectionGlyphSection: View {
    @Binding var glyph: Glyph?
    @State private var isPresentingPicker = false

    var body: some View {
        Section {
            Button { isPresentingPicker = true } label: {
                HStack(spacing: 12) {
                    if let glyph {
                        GlyphView(case: .default, strokes: glyph.strokes, side: 32)
                            .accessibilityHidden(true)
                    } else {
                        RoundedRectangle(cornerRadius: 6)
                            .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3]))
                            .frame(width: 32, height: 32)
                            .foregroundStyle(.secondary)
                            .accessibilityHidden(true)
                    }
                    Text("Tap to choose").foregroundStyle(.primary)
                    Spacer()
                    Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
                        .accessibilityHidden(true)
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Glyph"))
            .accessibilityValue(glyph == nil ? Text("None") : Text(verbatim: glyph?.name ?? ""))
            .accessibilityHint(Text("Tap to choose"))
            .pebblesListRow(position: glyph == nil ? .only : .top)
            if glyph != nil {
                Button("Remove glyph", role: .destructive) { glyph = nil }
                    .pebblesListRow(position: .bottom)
            }
            // A plain row, not a `footer:`: a plain-style list draws footers as
            // opaque, separated cells (same as EditMomentSheet's hint).
            Text("Pebbles in this collection carry its glyph.")
                .font(.footnote)
                .foregroundStyle(Color.system.secondary)
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
        } header: {
            Text("Glyph").pebblesSectionHeader()
        }
        .sheet(isPresented: $isPresentingPicker) {
            GlyphPickerSheet(currentGlyphId: glyph?.id, onSelected: { glyph = $0 })
        }
    }
}
