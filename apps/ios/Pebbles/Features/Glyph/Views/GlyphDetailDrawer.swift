import SwiftUI
import os

/// Bottom drawer for a glyph. Shows the SWAP state (cost + slide-to-confirm) or
/// the OWNED state (acquired date + seal), and morphs SWAP → OWNED in place after
/// a successful swap. Stat values we can't cheaply source (usage, owners, creator)
/// render as muted placeholders so the layout matches Figma.
struct GlyphDetailDrawer: View {
    let item: GlyphGridItem
    /// Caller's karma balance at open; kept in `@State` so it updates post-swap.
    let balance: Int
    /// Called after a successful swap so the parent can refresh lists.
    let onSwapped: (BuyGlyphResult) async -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var isOwned: Bool
    @State private var acquiredAt: Date?
    @State private var currentBalance: Int
    @State private var isBuying = false
    @State private var errorText: String?
    @State private var feedback = GlyphSwapFeedback()

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "glyph-detail")
    private var market: GlyphMarketService { GlyphMarketService(supabase: supabase) }

    init(item: GlyphGridItem, balance: Int, onSwapped: @escaping (BuyGlyphResult) async -> Void) {
        self.item = item
        self.balance = balance
        self.onSwapped = onSwapped
        _isOwned = State(initialValue: item.owned)
        _acquiredAt = State(initialValue: item.acquiredAt)
        _currentBalance = State(initialValue: balance)
    }

    private var canAfford: Bool { currentBalance >= item.price }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: Spacing.lg) {
                    GlyphView(case: .profile, strokes: item.glyph.strokes, side: 96)

                    VStack(spacing: 2) {
                        Text(item.glyph.name ?? String(localized: "Untitled glyph"))
                            .font(.title2.weight(.semibold))
                        Text("BY @community")
                            .font(.caption)
                            .foregroundStyle(Color.system.muted)
                    }

                    statCards
                    Divider().overlay(costOrSeal)
                    meVsCreatorRow

                    if isOwned {
                        acquiredLabel
                    } else {
                        SlideToConfirm(
                            cost: item.price,
                            isEnabled: canAfford && !isBuying,
                            feedback: feedback,
                            onConfirm: { await performSwap() }
                        )
                        if !canAfford {
                            Text("Not enough karma")
                                .font(.footnote)
                                .foregroundStyle(.red)
                        }
                    }

                    if let errorText {
                        Text(errorText).font(.footnote).foregroundStyle(.red)
                    }
                }
                .padding(Spacing.lg)
                .frame(maxWidth: .infinity)
            }
            .pebblesToolbarTitle(isOwned ? "Owned" : "Swap")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Cancel") { dismiss() }
                }
            }
            .pebblesScreen()
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    // MARK: - Pieces

    private var statCards: some View {
        HStack(spacing: Spacing.sm) {
            statCard(system: "calendar", value: createdText)
            statCard(system: "chart.bar.fill", value: Text("Soon"), muted: true)   // 🐚 usage
            statCard(system: "person.2.fill", value: Text("Soon"), muted: true)    // 👥 owners
        }
    }

    private func statCard(system: String, value: Text, muted: Bool = false) -> some View {
        VStack(spacing: Spacing.xs) {
            Image(systemName: system)
                .foregroundStyle(muted ? Color.system.muted : Color.accent.primary)
            value
                .font(.footnote.weight(.medium))
                .foregroundStyle(muted ? Color.system.muted : Color.system.foreground)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.md)
        .background(Color.accent.surface)
        .clipShape(RoundedRectangle(cornerRadius: Spacing.md, style: .continuous))
    }

    private var createdText: Text {
        if let createdAt = item.createdAt {
            return Text(createdAt, format: .dateTime.month(.abbreviated).year())
        }
        return Text("—")
    }

    @ViewBuilder private var costOrSeal: some View {
        if isOwned {
            Image(systemName: "checkmark.seal.fill")
                .foregroundStyle(Color.accent.primary)
                .padding(.horizontal, Spacing.sm)
                .background(Color.system.background)
        } else {
            HStack(spacing: 2) {
                Image(systemName: "sparkle")
                Text("\(item.price)")
            }
            .font(.headline)
            .foregroundStyle(Color.accent.primary)
            .padding(.horizontal, Spacing.sm)
            .background(Color.system.background)
        }
    }

    private var meVsCreatorRow: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Me").font(.caption).foregroundStyle(Color.system.secondary)
                HStack(spacing: 2) {
                    Image(systemName: "sparkle")
                    Text("\(currentBalance)")
                }.font(.subheadline.weight(.medium))
            }
            Spacer()
            Image(systemName: "arrow.left.arrow.right").foregroundStyle(Color.system.muted)
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text("Creator").font(.caption).foregroundStyle(Color.system.secondary)
                Text("@community").font(.subheadline.weight(.medium)).foregroundStyle(Color.system.muted)
            }
        }
    }

    @ViewBuilder private var acquiredLabel: some View {
        if let acquiredAt {
            Text("Acquired · \(acquiredAt.formatted(.dateTime.month(.abbreviated).day().year()))")
                .font(.subheadline)
                .foregroundStyle(Color.system.secondary)
        } else {
            Text("Owned").font(.subheadline).foregroundStyle(Color.system.secondary)
        }
    }

    // MARK: - Swap

    /// Returns `true` on success so `SlideToConfirm` can spring the thumb back on failure.
    private func performSwap() async -> Bool {
        isBuying = true
        errorText = nil
        defer { isBuying = false }
        do {
            let result = try await market.buy(id: item.glyph.id)
            currentBalance = result.balance
            acquiredAt = Date()
            withAnimation(.snappy) { isOwned = true }
            await onSwapped(result)
            return true
        } catch {
            logger.error("glyph swap failed: \(error.localizedDescription, privacy: .private)")
            errorText = Self.friendlyMessage(for: error)
            return false
        }
    }

    /// Maps `buy_glyph`'s Postgres error hints to user copy.
    private static func friendlyMessage(for error: Error) -> String {
        let text = error.localizedDescription.lowercased()
        if text.contains("insufficient_karma") { return String(localized: "Not enough karma") }
        if text.contains("not_in_market") { return String(localized: "This glyph is no longer available") }
        if text.contains("already_owned") { return String(localized: "You already own this glyph") }
        if text.contains("cannot_buy_own") { return String(localized: "This is your own glyph") }
        return String(localized: "Couldn't complete the swap. Please try again.")
    }
}
