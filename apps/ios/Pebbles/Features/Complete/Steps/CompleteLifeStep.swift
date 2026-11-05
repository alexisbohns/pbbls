import SwiftUI

/// What part of life (record-flow D10, Margin treatment): RECENT, then all
/// 18 as a two-column list of glyph + name, the last tap's description in a
/// caption bar, three at most. Beneath the list, sticky, "Who was there?"
/// with the four most-tagged people and More.
struct CompleteLifeStep: View {
    let model: CompleteFlowModel
    /// Reference renders only: ImageRenderer draws neither ScrollView content nor TextFields.
    var scrolls = true

    @Environment(ReferenceDataService.self) private var refs
    @Environment(SupabaseService.self) private var supabase
    @State private var lastTapped: Domain?
    @State private var showsPicker = false

    /// The scaffold floats the "Make it a pebble" pill over the bottom of
    /// this content, `Spacing.lg` off the edge; the strip keeps the pill's
    /// height (scaled as `PebblesPrimaryButtonStyle` scales it) plus one more
    /// gutter clear so the pill never covers a tile.
    @ScaledMetric(relativeTo: .body) private var buttonHeight: CGFloat = 52
    private var floatingActionClearance: CGFloat { buttonHeight + 2 * Spacing.lg }

    private var recent: [Domain] {
        guard let owner = supabase.session?.user.id else { return [] }
        let ids = RecentDomains().ids(owner: owner)
        return ids.compactMap { id in refs.domains.first { $0.id == id } }
    }

    var body: some View {
        VStack(spacing: 0) {
            if scrolls {
                ScrollView { domainsList }
                    .scrollBounceBehavior(.basedOnSize)
            } else {
                domainsList.frame(maxHeight: .infinity, alignment: .top)
            }
            captionBar
            VStack(spacing: 0) {
                UsualSoulsStrip(souls: refs.souls, selection: model.draft.soulIds,
                                onToggle: { model.toggleSoul($0) }, onMore: { showsPicker = true })
                    .padding(.vertical, Spacing.sm)
                if let error = model.completeError {
                    Text(verbatim: error)
                        .pebblesFont(.callout)
                        .foregroundStyle(Color.pebbles.error)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.bottom, Spacing.sm)
                }
            }
            .padding(.bottom, floatingActionClearance)
            .background(Color.pebbles.surface)
        }
        .task { await refs.refreshSouls() }
        .sheet(isPresented: $showsPicker) {
            SoulChooserSheet(title: "Who was there?", currentSelection: model.draft.soulIds,
                             allowsSkip: true, detents: [.large], onConfirm: { model.setSouls($0) })
        }
    }

    private var domainsList: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            if !recent.isEmpty {
                sectionLabel("RECENT")
                grid(recent)
            }
            sectionLabel("ALL")
            grid(refs.domains)
        }
        .padding(.bottom, Spacing.lg)
    }

    private func sectionLabel(_ text: LocalizedStringResource) -> some View {
        Text(text).pebblesFont(.meta).foregroundStyle(Color.pebbles.onSurfaceVariant)
    }

    private func grid(_ domains: [Domain]) -> some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: Spacing.sm), GridItem(.flexible(), spacing: Spacing.sm)],
                  spacing: Spacing.sm) {
            ForEach(domains) { domain in
                let isSelected = model.draft.domainIds.contains(domain.id)
                Button {
                    lastTapped = domain
                    model.toggleDomain(domain.id)
                } label: {
                    HStack(spacing: Spacing.sm) {
                        GlyphView(case: isSelected ? .selected : .default, strokes: domain.strokes, side: 40)
                            .accessibilityHidden(true)
                        Text(verbatim: domain.localizedName)
                            .pebblesFont(isSelected ? .bodyEmphasized : .body)
                            .foregroundStyle(isSelected ? Color.pebbles.onPrimaryContainer : Color.pebbles.onSurface)
                            .lineLimit(1)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, Spacing.sm)
                    .frame(minHeight: 52)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(isSelected ? Color.pebbles.primaryContainer : Color.pebbles.surfaceContainerLow))
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(verbatim: domain.localizedName))
                .accessibilityHint(Text(verbatim: domain.localizedLabel))
                .accessibilityAddTraits(isSelected ? [.isSelected] : [])
            }
        }
    }

    /// The one-line description of the last tap; empty keeps the height.
    private var captionBar: some View {
        Text(verbatim: lastTapped?.localizedLabel ?? " ")
            .pebblesFont(.subhead)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
            .lineLimit(1)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, Spacing.xs)
    }
}
