import SwiftUI

/// You's counters (#1382 D18): one stone cut in three, days, pebbles and
/// karma, each a figure in `counterFigure` ("—" while loading) over its icon
/// and label. The figures stop growing at `.xxxLarge` so three fit 358 pt; past
/// it the label wraps under the icon. Each tile is one element ("1,240, Karma").
struct YouCounters: View {
    let stats: YouStats

    private struct Counter {
        let value: Int?
        let icon: PbblsIcon
        let label: LocalizedStringKey
    }

    private var counters: [Counter] {
        [Counter(value: stats.daysPracticed, icon: .calendarDate, label: "Days"),
         Counter(value: stats.pebbles, icon: .pebbleRound, label: "Pebbles"),
         Counter(value: stats.karma, icon: .karmaDot, label: "Karma")]
    }

    var body: some View {
        SplitStone(count: 3, seed: "days|pebbles|karma", fills: [Color.pebbles.surfaceContainer], minHeight: 88) {
            cell(counters[$0], first: $0 == 0)
        }
    }

    private func cell(_ counter: Counter, first: Bool) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Group {
                if let value = counter.value {
                    Text(value, format: .number)
                } else {
                    Text(verbatim: "—")
                }
            }
            .spineType(.counterFigure)
            .foregroundStyle(Color.pebbles.onSurface)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
            HStack(alignment: .center, spacing: 6) {
                PbblsIconImage(counter.icon, size: 18, style: AnyShapeStyle(Color.pebbles.primary))
                Text(counter.label)
                    .spineType(.rowLine)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
        }
        .padding(EdgeInsets(top: 12, leading: first ? 16 : 20, bottom: 12, trailing: 10))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}
