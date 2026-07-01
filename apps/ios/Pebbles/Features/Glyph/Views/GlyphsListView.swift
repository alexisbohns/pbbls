import SwiftUI
import os

/// Profile → Glyphs. Three tabs (Mine / Owned / Commu). Mine keeps carve + rename;
/// Owned and Commu cells open the detail drawer (swap or owned state).
struct GlyphsListView: View {
    @Environment(SupabaseService.self) private var supabase
    @Environment(PathStatsService.self) private var stats

    @State private var tab: GlyphTab = .mine
    @State private var itemsByTab: [GlyphTab: [GlyphGridItem]] = [:]
    @State private var isLoading = false
    @State private var loadError: String?
    @State private var showCarveSheet = false
    @State private var renaming: Glyph?
    @State private var renameDraft = ""
    @State private var renameError: String?
    @State private var selected: GlyphGridItem?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.glyphs")
    private var glyphService: GlyphService { GlyphService(supabase: supabase) }
    private var market: GlyphMarketService { GlyphMarketService(supabase: supabase) }
    private let columns = [GridItem(.adaptive(minimum: 96), spacing: 12)]

    private var items: [GlyphGridItem] { itemsByTab[tab] ?? [] }

    var body: some View {
        content
            .pebblesToolbarTitle("Glyphs")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    PebbleToolbarButton(
                        action: { showCarveSheet = true },
                        label: { Image(systemName: "plus") }
                    )
                    .accessibilityLabel("Carve new glyph")
                }
            }
            .safeAreaInset(edge: .bottom) {
                GlyphTabBar(selection: $tab)
            }
            .task { await stats.load() }
            .task(id: tab) { await load(tab) }
            .fullScreenCover(isPresented: $showCarveSheet) {
                GlyphCarveSheet(onSaved: { glyph in
                    let item = GlyphGridItem(glyph: glyph, price: 0, owned: false, createdAt: nil, acquiredAt: nil)
                    itemsByTab[.mine, default: []].insert(item, at: 0)
                    tab = .mine
                })
            }
            .sheet(item: $selected) { item in
                GlyphDetailDrawer(item: item, balance: stats.karma ?? 0) { result in
                    await onSwapped(item: item, result: result)
                }
            }
            .pebblesScreen()
            .alert(
                "Rename glyph",
                isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } }),
                presenting: renaming
            ) { glyph in
                TextField("Name (optional)", text: $renameDraft)
                    .textInputAutocapitalization(.words)
                Button("Cancel", role: .cancel) {}
                Button("Save") { Task { await commitRename(glyph) } }
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
        } else if items.isEmpty {
            emptyState
        } else {
            VStack(spacing: 0) {
                if let renameError {
                    Text(renameError)
                        .font(.callout).foregroundStyle(.red)
                        .padding(.horizontal).padding(.top, 8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(items) { item in
                            cell(for: item)
                        }
                    }
                    .padding()
                }
            }
        }
    }

    private var emptyState: some View {
        switch tab {
        case .mine:
            return ContentUnavailableView("No glyphs yet", systemImage: "scribble",
                description: Text("Tap + to carve your first glyph."))
        case .owned:
            return ContentUnavailableView("Nothing owned yet", systemImage: "checkmark.seal",
                description: Text("Swap a community glyph to see it here."))
        case .commu:
            return ContentUnavailableView("No community glyphs", systemImage: "person.3",
                description: Text("Check back soon."))
        }
    }

    @ViewBuilder
    private func cell(for item: GlyphGridItem) -> some View {
        Button {
            switch tab {
            case .mine:
                // Mine keeps the rename flow.
                renameDraft = item.glyph.name ?? ""
                renaming = item.glyph
            case .owned, .commu:
                selected = item
            }
        } label: {
            VStack(spacing: 4) {
                GlyphView(case: .default, strokes: item.glyph.strokes, side: 96)
                if let name = item.glyph.name {
                    Text(name).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
                HStack(spacing: 2) {
                    Image(systemName: "sparkle")
                    Text("\(item.price)")
                }
                .font(.caption2)
                .foregroundStyle(Color.system.muted)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(item.glyph.name ?? "Untitled glyph")
        .accessibilityHint(tab == .mine ? "Double tap to rename" : "Double tap to open")
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
            case .commu: result = try await market.listCommunity()
            }
            itemsByTab[which] = result
        } catch {
            let reason = error.localizedDescription
            logger.error("glyphs fetch failed (\(which.rawValue, privacy: .public)): \(reason, privacy: .private)")
            if (itemsByTab[which] ?? []).isEmpty { loadError = "Please try again." }
        }
        isLoading = false
    }

    private func onSwapped(item: GlyphGridItem, result: BuyGlyphResult) async {
        stats.karma = result.balance
        // Remove from Commu's buyable list; refresh Owned lazily on next visit.
        itemsByTab[.commu]?.removeAll { $0.id == item.id }
        itemsByTab[.owned] = nil
    }

    // MARK: - Rename (Mine only)

    private func commitRename(_ glyph: Glyph) async {
        renameError = nil
        guard let index = (itemsByTab[.mine] ?? []).firstIndex(where: { $0.glyph.id == glyph.id }) else { return }
        let original = itemsByTab[.mine]![index]
        let trimmed = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        let optimisticName: String? = trimmed.isEmpty ? nil : trimmed

        itemsByTab[.mine]![index] = GlyphGridItem(
            glyph: Glyph(
                id: glyph.id, name: optimisticName, strokes: glyph.strokes,
                viewBox: glyph.viewBox, userId: glyph.userId
            ),
            price: original.price, owned: original.owned, createdAt: original.createdAt, acquiredAt: original.acquiredAt
        )

        do {
            let updated = try await glyphService.updateName(id: glyph.id, name: renameDraft)
            if let idx = (itemsByTab[.mine] ?? []).firstIndex(where: { $0.glyph.id == updated.id }) {
                itemsByTab[.mine]![idx] = GlyphGridItem(
                    glyph: updated, price: original.price, owned: original.owned,
                    createdAt: original.createdAt, acquiredAt: original.acquiredAt
                )
            }
        } catch {
            logger.error("glyph rename failed: \(error.localizedDescription, privacy: .private)")
            itemsByTab[.mine]![index] = original
            renameError = "Couldn't rename glyph. Please try again."
        }
    }
}

#Preview {
    NavigationStack {
        GlyphsListView()
            .environment(SupabaseService())
            .environment(PathStatsService(supabase: SupabaseService()))
    }
}
