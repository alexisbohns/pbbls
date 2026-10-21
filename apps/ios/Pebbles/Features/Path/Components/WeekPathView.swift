import SwiftUI

/// One TabView page in the iOS Path body. Renders the focused week's
/// pebble list with a per-pebble reveal cascade that re-runs whenever
/// the entry's identity or pebble count changes.
///
/// The bottom of the list fades behind the FAB row via a vertical
/// gradient mask (opaque 0% → opaque 85% → transparent 100%).
struct WeekPathView: View {
    let entry: WeekRollEntry
    let onTap: (MomentSummary) -> Void
    let onDelete: (MomentSummary) -> Void
    let onCreate: () -> Void
    /// Bumped by `TabNavigation` when Path is reselected at its root.
    var scrollToTopToken: Int = 0

    @State private var revealedCount = 0

    /// Stagger between consecutive pebble reveals.
    private static let revealStagger: Duration = .milliseconds(80)

    /// `cascadeKey` keys the reveal `.task`. Combining `weekStart` with
    /// `moments.count` guarantees the cascade replays both on week swap
    /// and on a same-week pebble create/delete.
    private var cascadeKey: String {
        "\(entry.weekStart.timeIntervalSince1970)-\(entry.moments.count)"
    }

    var body: some View {
        Group {
            if entry.moments.isEmpty {
                emptyState
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(Array(entry.moments.enumerated()), id: \.element.id) { index, moment in
                                if index < revealedCount {
                                    PathPebbleRow(
                                        moment: moment,
                                        positionIndex: index,
                                        onTap: { onTap(moment) },
                                        onDelete: { onDelete(moment) }
                                    )
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.vertical, 8)
                                    .padding(.horizontal, 24)
                                    .transition(.opacity.combined(with: .move(edge: .top)))
                                }
                            }
                        }
                        .padding(.bottom, 80)
                        .id(TabNavigation.scrollTopAnchor)
                    }
                    .scrollClipDisabled()
                    .mask(
                        LinearGradient(
                            stops: [
                                .init(color: .black, location: 0.0),
                                .init(color: .black, location: 0.85),
                                .init(color: .clear, location: 1.0),
                            ],
                            startPoint: .top, endPoint: .bottom
                        )
                    )
                    .onChange(of: scrollToTopToken) {
                        withAnimation { proxy.scrollTo(TabNavigation.scrollTopAnchor, anchor: .top) }
                    }
                }
            }
        }
        .task(id: cascadeKey) {
            revealedCount = 0
            for index in 0..<entry.moments.count {
                try? await Task.sleep(for: Self.revealStagger)
                withAnimation(.easeOut(duration: 0.25)) {
                    revealedCount = index + 1
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Spacer()
            Text("Fresh week")
                .font(.ysabeauSemibold(20))
                .foregroundStyle(Color.pebbles.onSurface)
            Text("Collect a new pebble")
                .font(.caption)
                .tracking(1.2)
                .textCase(.uppercase)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
            Button(action: onCreate) {
                Image(systemName: "plus")
                    .font(.title)
                    .fontWeight(.medium)
                    .foregroundStyle(Color.pebbles.onPrimary)
                    .frame(width: 44, height: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 17)
                            .fill(Color.pebbles.primary)
                    )
            }
            .buttonStyle(.plain)
            .accessibilityLabel("New pebble")
            .padding(.top, 12)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}
