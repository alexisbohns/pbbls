import SwiftUI

/// What part of life (record-flow D10, Margin treatment): RECENT, then all
/// 18 as a two-column list of glyph + name. Beneath the list a tinted bar,
/// pinned: the last tap's description once there is one, "Who was there?"
/// with the four most-tagged people and More, then "Make it a pebble".
struct CompleteLifeStep: View {
    let model: CompleteFlowModel
    let onComplete: () -> Void
    /// Reference renders only: ImageRenderer draws neither ScrollView content nor TextFields.
    var scrolls = true

    @Environment(ReferenceDataService.self) private var refs
    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var lastTapped: Domain?
    @State private var showsPicker = false

    /// How far the list dissolves into the bar instead of clipping at it.
    private let edgeFade: CGFloat = Spacing.xl

    private var recent: [Domain] {
        guard let owner = supabase.session?.user.id else { return [] }
        let ids = RecentDomains().ids(owner: owner)
        return ids.compactMap { id in refs.domains.first { $0.id == id } }
    }

    var body: some View {
        VStack(spacing: 0) {
            if scrolls {
                ScrollView { domainsList }
                    .contentMargins(.bottom, edgeFade, for: .scrollContent)
                    .scrollBounceBehavior(.basedOnSize)
                    .mask(fadeMask)
            } else {
                domainsList
                    .padding(.bottom, edgeFade)
                    .frame(maxHeight: .infinity, alignment: .top)
            }
            bar
        }
        .task { await refs.refreshSouls() }
        .sheet(isPresented: $showsPicker) {
            SoulChooserSheet(title: "Who was there?", currentSelection: model.draft.soulIds,
                             allowsSkip: true, detents: [.large], onConfirm: { model.setSouls($0) })
        }
    }

    private var fadeMask: some View {
        VStack(spacing: 0) {
            Color.pebbles.onSurface
            LinearGradient(colors: [Color.pebbles.onSurface, .clear], startPoint: .top, endPoint: .bottom)
                .frame(height: edgeFade)
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
        .padding(.horizontal, Spacing.lg)
    }

    private func sectionLabel(_ text: LocalizedStringResource) -> some View {
        Text(text).pebblesFont(.meta).foregroundStyle(Color.pebbles.onSurfaceVariant)
    }

    private func grid(_ domains: [Domain]) -> some View {
        // One column at accessibility sizes: a half-width tile truncates every name.
        let columns = typeSize.isAccessibilitySize ? 1 : 2
        return LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.sm), count: columns),
                  spacing: Spacing.sm) {
            ForEach(domains) { domain in
                let isSelected = model.draft.domainIds.contains(domain.id)
                Button {
                    lastTapped = domain
                    model.toggleDomain(domain.id)
                } label: {
                    HStack(spacing: Spacing.sm) {
                        GlyphView(case: isSelected ? .selected : .default, strokes: domain.strokes, side: 28)
                            .accessibilityHidden(true)
                        Text(verbatim: domain.localizedName)
                            .pebblesFont(isSelected ? .bodyEmphasized : .body)
                            .foregroundStyle(isSelected ? Color.pebbles.onPrimaryContainer : Color.pebbles.onSurface)
                            .lineLimit(1)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, Spacing.sm)
                    .frame(minHeight: 44)
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

    /// The pinned bar under the list. Its own tint and a hairline are what
    /// make the list's end read as the list's end rather than a cut.
    private var bar: some View {
        VStack(spacing: Spacing.sm) {
            if let lastTapped {
                caption(lastTapped)
            }
            UsualSoulsStrip(souls: refs.souls, selection: model.draft.soulIds,
                            onToggle: { model.toggleSoul($0) }, onMore: { showsPicker = true })
            if let error = model.completeError {
                Text(verbatim: error)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.pebbles.error)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
            Button("Make it a pebble", action: onComplete)
                .buttonStyle(PebblesPrimaryButtonStyle(isLoading: model.isCompleting))
                .disabled(!model.draft.isComplete || model.isCompleting)
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.top, Spacing.sm)
        .padding(.bottom, Spacing.sm)
        .background(Color.pebbles.surfaceContainerLow.ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) {
            Rectangle().fill(Color.pebbles.outlineVariant).frame(height: 0.5)
        }
    }

    /// The one-line description of the last tap. Absent until the first tap
    /// rather than a blank line reserving its height: the bar is tall enough.
    private func caption(_ domain: Domain) -> some View {
        Text(verbatim: domain.localizedLabel)
            .pebblesFont(.subhead)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
            .lineLimit(1)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}
