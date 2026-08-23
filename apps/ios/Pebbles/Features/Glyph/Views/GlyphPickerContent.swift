import SwiftUI
import os

/// The glyph picker's grid, tabs and inline buy flow.
///
/// Presentation plus its own fetching (D5): unlike the other content views this
/// one owns loading, because the three tabs each have their own query and
/// nothing outside the picker caches them.
///
/// `onSelected` fires for every way a glyph becomes the choice — picked from a
/// tab, carved fresh, or bought from Commu. The caller decides what happens
/// next: `GlyphPickerSheet` dismisses, the record flow's glyph step advances.
/// Nothing in here dismisses anything.
///
/// Before #547 this listed `GlyphService.list()` — an unfiltered `glyphs` read
/// that, under the browse-friendly `glyphs_select` RLS, surfaced every approved
/// community glyph as pickable (including unbought ones). The server guard
/// (`can_use_glyph`, #545) now rejects attaching an unowned glyph, so the picker
/// must only offer usable glyphs (Mine ∪ Owned) and route Commu through a buy.
struct GlyphPickerContent: View {
    let currentGlyphId: UUID?
    let onSelected: (Glyph) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(PathStatsService.self) private var stats

    @State private var tab: GlyphTab = .mine
    @State private var itemsByTab: [GlyphTab: [GlyphGridItem]] = [:]
    @State private var isLoading = false
    @State private var loadError: String?
    @State private var showCarveSheet = false
    /// Commu glyph pending purchase — drives the buy drawer.
    @State private var buying: GlyphGridItem?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "glyph-picker")
    private var market: GlyphMarketService { GlyphMarketService(supabase: supabase) }

    private let columns = [GridItem(.adaptive(minimum: 96), spacing: 12)]

    private var items: [GlyphGridItem] { itemsByTab[tab] ?? [] }

    var body: some View {
        content
            .safeAreaInset(edge: .bottom) {
                GlyphTabBar(selection: $tab)
            }
            .task { await stats.load() }
            .task(id: tab) { await load(tab) }
            .fullScreenCover(isPresented: $showCarveSheet) {
                GlyphCarveSheet(onSaved: { glyph in
                    onSelected(glyph)
                })
            }
            .sheet(item: $buying) { item in
                GlyphDetailDrawer(item: item, balance: stats.karma ?? 0) { _ in
                    // Bought → the glyph is now usable; select it.
                    //
                    // Clearing `buying` here is load-bearing: the drawer does
                    // not dismiss itself on a successful swap (it flips to its
                    // owned state), and the old sheet only got away with that
                    // because the caller's `dismiss()` took the whole picker
                    // down with it. The record flow does not dismiss, so
                    // without this the drawer would hang over the next step.
                    buying = nil
                    onSelected(item.glyph)
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading && items.isEmpty {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load glyphs",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
            .overlay(alignment: .bottom) {
                Button("Retry") { Task { await load(tab) } }
                    .padding()
            }
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Mine keeps the carve entry point; it also serves as the
                    // empty-state CTA, so Mine shows no ContentUnavailableView.
                    if tab == .mine {
                        carveNewRow
                    }

                    if items.isEmpty {
                        // Mine's empty state is EmptyView — its carve row is the CTA.
                        emptyState
                    } else {
                        LazyVGrid(columns: columns, spacing: 12) {
                            ForEach(items) { item in
                                cell(for: item)
                            }
                        }
                    }
                }
                .padding()
            }
        }
    }

    @ViewBuilder
    private var emptyState: some View {
        switch tab {
        case .mine:
            EmptyView()
        case .owned:
            ContentUnavailableView(
                "Nothing owned yet",
                systemImage: "checkmark.seal",
                description: Text("Swap a community glyph to see it here.")
            )
            .frame(maxWidth: .infinity)
        case .commu:
            ContentUnavailableView(
                "No community glyphs",
                systemImage: "person.3",
                description: Text("Check back soon.")
            )
            .frame(maxWidth: .infinity)
        }
    }

    private var carveNewRow: some View {
        Button {
            showCarveSheet = true
        } label: {
            HStack(spacing: Spacing.sm) {
                GlyphView(case: .carve, side: 48)
                Text("Carve new glyph")
                    .font(.body)
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(.secondary)
            }
            .padding(Spacing.sm)
            .background(Color.secondary.opacity(0.06))
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func cell(for item: GlyphGridItem) -> some View {
        Button {
            switch tab {
            case .mine, .owned:
                onSelected(item.glyph)
            case .commu:
                buying = item
            }
        } label: {
            VStack(spacing: 4) {
                GlyphView(
                    case: item.glyph.id == currentGlyphId ? .selected : .default,
                    strokes: item.glyph.strokes,
                    side: 96
                )
                if let name = item.glyph.name {
                    Text(name).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
                if tab == .commu, item.price > 0 {
                    HStack(spacing: 2) {
                        Image(systemName: "sparkle")
                        Text("\(item.price)")
                    }
                    .font(.caption2)
                    .foregroundStyle(Color.system.muted)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(item.glyph.name ?? "Untitled glyph")
    }

    // MARK: - Loading

    private func load(_ which: GlyphTab) async {
        isLoading = true
        loadError = nil
        do {
            let result: [GlyphGridItem]
            switch which {
            case .mine:  result = try await market.listMine()
            case .owned: result = try await market.listOwned()
            // Owned community glyphs live under the Owned tab; Commu only offers
            // what's still buyable.
            case .commu: result = try await market.listCommunity().filter { !$0.owned }
            }
            itemsByTab[which] = result
        } catch {
            let reason = error.localizedDescription
            logger.error("glyphs fetch failed (\(which.rawValue, privacy: .public)): \(reason, privacy: .private)")
            if (itemsByTab[which] ?? []).isEmpty { loadError = "Please try again." }
        }
        isLoading = false
    }
}
