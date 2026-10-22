import SwiftUI

/// The day being read (#1130 D12): each realised seam and pebble row reports
/// its day and its top in the page's named coordinate space (a `PreferenceKey`;
/// `onScrollGeometryChange` needs iOS 18). The reading day is the last mark at
/// or above 35 % of the viewport. Pebble rows report too (decision 2), so a long
/// day whose seam has left the `LazyVStack` is still read. The model writes it
/// only on a change.
enum ReadingDay {
    /// The web's `rootMargin: -35% …`.
    static let line: CGFloat = 0.35

    struct Mark: Equatable {
        let day: Int
        let minY: CGFloat
    }

    static func index(_ marks: [Mark], viewportHeight: CGFloat) -> Int? {
        let lineY = viewportHeight * line
        return marks.filter { $0.minY <= lineY }.max { $0.minY < $1.minY }?.day
    }
}

struct ReadingDayKey: PreferenceKey {
    static let defaultValue: [ReadingDay.Mark] = []

    static func reduce(value: inout [ReadingDay.Mark], nextValue: () -> [ReadingDay.Mark]) {
        value.append(contentsOf: nextValue())
    }
}

extension View {
    /// Reports this row's day and top for the reading day, in `space`.
    func spineDayMark(_ day: Int, in space: String) -> some View {
        background(GeometryReader { proxy in
            Color.clear.preference(key: ReadingDayKey.self,
                                   value: [ReadingDay.Mark(day: day, minY: proxy.frame(in: .named(space)).minY)])
        })
    }
}
