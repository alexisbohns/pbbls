import SwiftUI

/// The lens and the plus in thumb reach (souls spec S3, S6). The lens opens
/// into a search field in its place, anchored above the keyboard because the
/// bar lives in the host's bottom safe-area inset; the close sits inside the
/// field. The plus lights up when the query names nobody, because it then does
/// what the Add row does. Glass on iOS 26, material below: the one
/// `if #available` exception, as `NewPebbleFab`.
struct SoulChooserBottomBar: View {
    let model: SoulChooserModel
    /// Fired with the trimmed query; the host inserts the row.
    let onAdd: (String) -> Void
    var addError: String?
    /// Reference renders only: `ImageRenderer` draws neither ScrollView content nor TextFields.
    var rendersStatic = false

    @FocusState private var fieldFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            if let addError {
                Text(verbatim: addError)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.pebbles.error)
                    .padding(.horizontal, Spacing.lg)
            }
            HStack(spacing: Spacing.sm) {
                if model.isSearching {
                    field
                } else {
                    Spacer(minLength: 0)
                    orb(label: "Search souls", lit: false, action: { model.isSearching = true }, glyph: {
                        Image(systemName: "magnifyingglass").font(.system(size: 22, weight: .medium))
                    })
                }
                orb(label: "Add a soul", lit: model.canAdd, action: { plusTapped() }, glyph: {
                    PbblsGlyphView(.plusCircle, size: 26)
                })
                    .accessibilityValue(Text(verbatim: model.canAdd ? model.trimmedQuery : ""))
            }
            .padding(.horizontal, Spacing.lg)
        }
        .onAppear { fieldFocused = model.isSearching }
        .onChange(of: model.isSearching) { _, searching in fieldFocused = searching }
    }

    private var field: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .accessibilityHidden(true)
            if rendersStatic {
                staticField
            } else {
                TextField("Search souls", text: Binding(get: { model.query }, set: { model.query = $0 }))
                    .pebblesFont(.body)
                    .focused($fieldFocused)
                    .submitLabel(.done)
                    .autocorrectionDisabled()
                    .onSubmit { if model.canAdd { onAdd(model.trimmedQuery) } }
            }
            Button {
                model.query = ""
                model.isSearching = false
            } label: {
                Image(systemName: "xmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close search")
        }
        .padding(.leading, Spacing.lg)
        .padding(.trailing, Spacing.xs)
        .frame(minHeight: 52)
        .frame(maxWidth: .infinity)
        .glassCapsule()
    }

    @ViewBuilder
    private var staticField: some View {
        if model.query.isEmpty {
            Text("Search souls")
                .pebblesFont(.body)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            Text(verbatim: model.query)
                .pebblesFont(.body)
                .foregroundStyle(Color.pebbles.onSurface)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// The lens keeps its SF symbol (the icon set has none); the plus is the
    /// set's `plus-circle`, which the icons lint requires over SF `plus`.
    private func orb<Glyph: View>(label: LocalizedStringResource, lit: Bool,
                                  action: @escaping () -> Void,
                                  @ViewBuilder glyph: () -> Glyph) -> some View {
        Button(action: action) {
            glyph()
                .foregroundStyle(lit ? Color.pebbles.onPrimary : Color.pebbles.onSurface)
                .frame(width: 52, height: 52)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .glassCircle(tinted: lit)
        .accessibilityLabel(Text(label))
    }

    private func plusTapped() {
        if model.canAdd {
            onAdd(model.trimmedQuery)
        } else {
            model.isSearching = true
        }
    }
}

/// "Add <name> · A drawing can wait": the first result slot when the query
/// names nobody (souls spec S3).
struct SoulAddRow: View {
    let name: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: Spacing.md) {
                PbblsGlyphView(.plusCircle, size: 32, style: AnyShapeStyle(Color.pebbles.onPrimaryContainer))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Add \(name)")
                        .pebblesFont(.bodyEmphasized)
                        .foregroundStyle(Color.pebbles.onPrimaryContainer)
                        .lineLimit(1)
                    Text("A drawing can wait")
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.pebbles.onPrimaryContainer)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, Spacing.md)
            .frame(minHeight: 64)
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Color.pebbles.primary.opacity(0.20))
            )
            .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private extension View {
    @ViewBuilder
    func glassCircle(tinted: Bool) -> some View {
        if #available(iOS 26.0, *) {
            if tinted {
                glassEffect(.regular.tint(Color.pebbles.primary).interactive(), in: Circle())
            } else {
                glassEffect(.regular.interactive(), in: Circle())
            }
        } else {
            background(Circle().fill(tinted ? Color.pebbles.primary : Color.pebbles.surfaceContainerHigh))
                .shadow(color: Color.pebbles.scrim.opacity(0.16), radius: 12, y: 4)
        }
    }

    @ViewBuilder
    func glassCapsule() -> some View {
        if #available(iOS 26.0, *) {
            glassEffect(.regular, in: Capsule())
        } else {
            background(Capsule().fill(Color.pebbles.surfaceContainerHigh))
                .shadow(color: Color.pebbles.scrim.opacity(0.16), radius: 12, y: 4)
        }
    }
}
