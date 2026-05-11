import SwiftUI

/// One TabView page in the iOS Path body. Renders the focused week's
/// pebble list with a per-pebble reveal cascade that re-runs whenever
/// the entry's identity or pebble count changes.
///
/// The bottom of the list fades behind the New button via a vertical
/// gradient mask (opaque 0% → opaque 85% → transparent 100%).
struct WeekPathView: View {
    let entry: WeekRollEntry
    let onTap: (Pebble) -> Void
    let onDelete: (Pebble) -> Void

    @State private var revealedCount = 0

    /// Stagger between consecutive pebble reveals.
    private static let revealStagger: Duration = .milliseconds(80)

    /// `cascadeKey` keys the reveal `.task`. Combining `weekStart` with
    /// `pebbles.count` guarantees the cascade replays both on week swap
    /// and on a same-week pebble create/delete.
    private var cascadeKey: String {
        "\(entry.weekStart.timeIntervalSince1970)-\(entry.pebbles.count)"
    }

    var body: some View {
        Group {
            if entry.pebbles.isEmpty {
                emptyState
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(Array(entry.pebbles.enumerated()), id: \.element.id) { index, pebble in
                            if index < revealedCount {
                                PathPebbleRow(
                                    pebble: pebble,
                                    positionIndex: index,
                                    onTap: { onTap(pebble) },
                                    onDelete: { onDelete(pebble) }
                                )
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.vertical, 8)
                                .padding(.horizontal, 24)
                                .transition(.opacity.combined(with: .move(edge: .top)))
                            }
                        }
                    }
                    .padding(.bottom, 80)
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
            }
        }
        .task(id: cascadeKey) {
            revealedCount = 0
            for index in 0..<entry.pebbles.count {
                try? await Task.sleep(for: Self.revealStagger)
                withAnimation(.easeOut(duration: 0.25)) {
                    revealedCount = index + 1
                }
            }
        }
    }

    private var emptyState: some View {
        VStack {
            Spacer()
            Text("No pebbles this week")
                .foregroundStyle(Color.pebblesMutedForeground)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}
