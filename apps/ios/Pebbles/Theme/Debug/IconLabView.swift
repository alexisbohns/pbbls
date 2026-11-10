#if DEBUG
import SwiftUI

/// The icon lab (#1311), Settings → Developer, beside the stone and Spine
/// labs: every glyph of `packages/icons` at a chosen size, in the form a
/// picker chooses (regular, the `selected` or `done` token, marked), with all
/// of its forms beside it on a tap. Verbatim text throughout, so nothing
/// reaches the string catalogue.
struct IconLabView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var side: CGFloat = 28
    @State private var query = ""
    @State private var onPrimary = false
    @State private var picked: PbblsGlyph?
    @State private var shows: Shown = .regular

    /// What the grid draws for each glyph.
    private enum Shown: String, CaseIterable, Identifiable {
        case regular = "Regular", selected = "Selected", done = "Done", marked = "Marked"
        var id: String { rawValue }
    }

    private var glyphs: [PbblsGlyph] { PbblsGlyph.allCases }

    private var shown: [PbblsGlyph] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        return q.isEmpty ? glyphs : glyphs.filter { $0.rawValue.contains(q) }
    }

    /// The form a glyph takes under the picker: the state tokens resolve per glyph,
    /// and `Marked` falls back to regular when the glyph has no mark.
    private func shownForm(_ glyph: PbblsGlyph) -> GlyphForm {
        switch shows {
        case .regular: .regular
        case .selected: glyph.form(for: .selected)
        case .done: glyph.form(for: .done)
        case .marked: glyph.forms.contains(.marked) ? .marked : .regular
        }
    }

    private var ink: Color { onPrimary ? Color.pebbles.onPrimary : Color.pebbles.onSurface }
    private var paper: Color { onPrimary ? Color.pebbles.primary : Color.pebbles.surface }

    var body: some View {
        NavigationStack {
            ScrollView {
                let columns = [GridItem(.adaptive(minimum: side + 24), spacing: Spacing.xs)]
                LazyVGrid(columns: columns, spacing: Spacing.sm) {
                    ForEach(shown, id: \.self) { icon in
                        Button { picked = icon } label: {
                            VStack(spacing: 4) {
                                PbblsGlyphView(icon, size: side, form: shownForm(icon))
                                    .foregroundStyle(ink)
                                    .frame(width: side + 16, height: side + 16)
                                    .background(paper, in: RoundedRectangle(cornerRadius: 8))
                                Text(verbatim: icon.rawValue)
                                    .font(.system(size: 9))
                                    .lineLimit(1)
                                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, Spacing.md)
                .padding(.bottom, Spacing.xl)
            }
            .background(Color.pebbles.surface)
            .searchable(text: $query, prompt: Text(verbatim: "Filter by name"))
            .safeAreaInset(edge: .bottom) {
                VStack(spacing: Spacing.xs) {
                    HStack {
                        Text(verbatim: "\(Int(side)) pt")
                            .font(.caption.monospacedDigit())
                            .frame(width: 44, alignment: .leading)
                        Slider(value: $side, in: 16...72, step: 2)
                    }
                    Picker(selection: $shows) {
                        ForEach(Shown.allCases) { Text(verbatim: $0.rawValue).tag($0) }
                    } label: { Text(verbatim: "Form") }
                    .pickerStyle(.segmented)
                    HStack(spacing: Spacing.xs) {
                        ForEach(PbblsIconSize.allCases, id: \.rawValue) { size in
                            Button {
                                side = size.rawValue
                            } label: {
                                Text(verbatim: "\(size)")
                                    .font(.caption2.monospaced())
                                    .padding(.horizontal, Spacing.xs)
                                    .padding(.vertical, 2)
                                    .background(side == size.rawValue ? Color.pebbles.primaryContainer : .clear,
                                                in: Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    Toggle(isOn: $onPrimary) { Text(verbatim: "On primary") }
                        .font(.caption)
                }
                .padding(.horizontal, Spacing.md)
                .padding(.vertical, Spacing.xs)
                .background(.bar)
            }
            .navigationTitle(Text(verbatim: "Icon lab · \(shown.count)/\(glyphs.count)"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: { Text(verbatim: "Close") }
                }
            }
            .sheet(item: $picked) { icon in
                forms(of: icon)
                    .presentationDetents([.medium])
            }
        }
    }

    /// Every form the glyph has at 48pt, each captioned with its key, on paper and on primary.
    private func forms(of glyph: PbblsGlyph) -> some View {
        VStack(spacing: Spacing.md) {
            Text(verbatim: glyph.rawValue).font(.headline)
            ForEach([false, true], id: \.self) { primary in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.md) {
                        ForEach(glyph.forms, id: \.key) { form in
                            VStack(spacing: 4) {
                                PbblsGlyphView(glyph, size: 48, form: form)
                                    .foregroundStyle(primary ? Color.pebbles.onPrimary : Color.pebbles.onSurface)
                                Text(verbatim: form.key).font(.system(size: 10))
                                    .foregroundStyle(primary ? Color.pebbles.onPrimary : Color.pebbles.onSurfaceVariant)
                            }
                        }
                    }
                    .padding(Spacing.md)
                    .frame(minWidth: 0)
                }
                .background(
                    primary ? Color.pebbles.primary : Color.pebbles.surface,
                    in: RoundedRectangle(cornerRadius: 12)
                )
            }
            Spacer()
        }
        .padding(Spacing.md)
        .background(Color.pebbles.surfaceContainer)
    }
}

extension PbblsGlyph: Identifiable {
    var id: String { rawValue }
}
#endif
