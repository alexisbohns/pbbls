import Foundation

/// The sequences that have played this launch (#1241 D3): a month's sequence
/// plays once per process. `PathModel` holds the app's; the Lab keeps its own.
@MainActor
final class IntroLedger {
    private struct Entry: Hashable {
        let month: CalendarMonth
        let sequence: IntroSequence
    }

    private var played: Set<Entry> = []

    func contains(_ month: CalendarMonth, _ sequence: IntroSequence) -> Bool {
        played.contains(Entry(month: month, sequence: sequence))
    }

    func record(_ month: CalendarMonth, _ sequence: IntroSequence) {
        played.insert(Entry(month: month, sequence: sequence))
    }

    /// Forgets one month, so its intro plays again (the Lab's replay).
    func reset(_ month: CalendarMonth) {
        played = played.filter { $0.month != month }
    }
}
