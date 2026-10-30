#if DEBUG
import SwiftUI

/// The icon lab (#1311), Settings → Developer, beside the stone and Spine
/// labs: every base icon of `packages/icons` at a chosen size, with its
/// twins (`active`, `lit-1…3`) beside it on a tap. Verbatim text throughout,
/// so nothing reaches the string catalogue.
struct IconLabView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var side: CGFloat = 28
    @State private var query = ""
    @State private var onPrimary = false
    @State private var picked: PbblsIcon?

    /// One case per manifest row: the bases, in manifest order.
    private var bases: [PbblsIcon] {
        PbblsIcon.allCases.filter { !$0.rawValue.contains(".") }
    }

    private var shown: [PbblsIcon] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        return q.isEmpty ? bases : bases.filter { $0.rawValue.contains(q) }
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
                                PbblsIconImage(icon, size: side)
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
            .navigationTitle(Text(verbatim: "Icon lab · \(shown.count)/\(bases.count)"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: { Text(verbatim: "Close") }
                }
            }
            .sheet(item: $picked) { icon in
                twins(of: icon)
                    .presentationDetents([.medium])
            }
        }
    }

    /// The base and its twins at 48pt, each labelled, on paper and on primary.
    private func twins(of icon: PbblsIcon) -> some View {
        let family: [(String, PbblsIcon)] = [("base", icon)]
            + (icon.activeTwin.map { [("active", $0)] } ?? [])
            + (1...3).compactMap { n in icon.lit(n).map { ("lit-\(n)", $0) } }
        return VStack(spacing: Spacing.md) {
            Text(verbatim: icon.rawValue).font(.headline)
            ForEach([false, true], id: \.self) { primary in
                HStack(spacing: Spacing.md) {
                    ForEach(family, id: \.1) { label, twin in
                        VStack(spacing: 4) {
                            PbblsIconImage(twin, size: 48)
                                .foregroundStyle(primary ? Color.pebbles.onPrimary : Color.pebbles.onSurface)
                            Text(verbatim: label).font(.system(size: 10))
                                .foregroundStyle(primary ? Color.pebbles.onPrimary : Color.pebbles.onSurfaceVariant)
                        }
                    }
                }
                .padding(Spacing.md)
                .frame(maxWidth: .infinity)
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

extension PbblsIcon: Identifiable {
    var id: String { rawValue }
}
#endif
