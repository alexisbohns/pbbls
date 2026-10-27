import SwiftUI
import os

/// What opening a month on Home shows. Folded: the count and one button that
/// unfolds it (the server records when). Revealed: the month's deck, the same
/// `MonthPage` the Path shows, played once a launch through Home's own ledger.
struct HomeWrapSheet: View {
    let monthId: CalendarMonth
    let lookups: WrapLookups
    let model: HomeModel
    let onWeek: (CalendarDay) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss
    @State private var wrap: MonthWrap?
    @State private var isLoading = false
    @State private var error: String?
    @State private var revealing = false
    @State private var revealError: String?
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "home.sheet")

    private var month: HomeMonth? { model.wraps?.months.first { $0.month == monthId } }
    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        NavigationStack {
            Group {
                if let month, month.isFolded {
                    ScrollView {
                        VStack(alignment: .leading, spacing: Spacing.lg) {
                            HomeWrapFoldedView(month: month, onUnfold: unfold, isRevealing: revealing)
                            if let revealError {
                                Text(verbatim: revealError).pebblesFont(.callout).foregroundStyle(Color.pebbles.error)
                            }
                        }
                        .padding(Spacing.lg)
                    }
                } else {
                    deck
                }
            }
            .task(id: month?.isFolded) { if month?.isFolded == false { await loadDeck() } }
            .onAppear { if month == nil { dismiss() } }
            .onChange(of: month == nil) { _, gone in if gone { dismiss() } }
            .pebblesToolbarTitle(WrapCopy.monthName(monthId, locale: locale))
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    PebbleToolbarButton("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .pebblesScreen()
    }

    @ViewBuilder
    private var deck: some View {
        if let wrap {
            MonthPage(wrap: wrap, lookups: lookups, isFocused: true, returnFocus: .constant(nil),
                      onWeek: { day in dismiss(); onWeek(day) }, onOpen: { _ in },
                      intro: IntroContext(ledger: model.introLedger, haptics: IntroHaptics.shared))
        } else if let error {
            VStack(spacing: Spacing.sm) {
                Text(verbatim: error).pebblesFont(.callout).foregroundStyle(Color.pebbles.error)
                Button("home.wraps.retry") { Task { await loadDeck() } }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func unfold() {
        guard !revealing else { return }
        revealing = true
        revealError = nil
        Task {
            defer { revealing = false }
            do {
                let reveal = try await model.reveal(monthId, zone: model.zone)
                if reveal.revealedAt == nil { revealError = String(localized: "home.wraps.error") }
            } catch {
                logger.error("reveal failed: \(error.localizedDescription, privacy: .public)")
                revealError = String(localized: "home.wraps.error")
            }
        }
    }

    private func loadDeck() async {
        guard !isLoading else { return }
        isLoading = true
        error = nil
        defer { isLoading = false }
        let service = PathWrapService(client: supabase.client, disk: .standard,
                                      owner: supabase.session?.user.id.uuidString.lowercased())
        if wrap == nil, let cached = await service.cachedMonthWrap(monthId, zone: model.zone) { wrap = cached }
        do {
            wrap = try await service.monthWrap(monthId, zone: model.zone)
        } catch {
            logger.warning("month wrap failed: \(error.localizedDescription, privacy: .public)")
            if wrap == nil { self.error = String(localized: "home.wraps.error") }
        }
    }
}

/// The folded month's content alone, so the references draw it without a service.
struct HomeWrapFoldedView: View {
    let month: HomeMonth
    let onUnfold: () -> Void
    let isRevealing: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text(verbatim: WrapCopy.line(pebbles: month.count, people: month.cover.soulsInOrder.count,
                                         live: false, hasMoments: true, locale: SpineStrings.appLocale))
                .spineType(.summary)
                .foregroundStyle(Color.pebbles.onSurface)
            Button(action: onUnfold) {
                Text("home.wrap.unfold").frame(maxWidth: .infinity)
            }
            .buttonStyle(PebblesPrimaryButtonStyle(isLoading: isRevealing))
            VStack(spacing: Spacing.sm) {
                ForEach(0..<3, id: \.self) { _ in
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .strokeBorder(Color.pebbles.outlineVariant, style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
                        .frame(height: 72)
                }
            }
            .accessibilityHidden(true)
        }
    }
}
