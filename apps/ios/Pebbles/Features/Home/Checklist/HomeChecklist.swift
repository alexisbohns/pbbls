import Foundation

/// The beginner checklist (spec D6, §1.4): five one-shot achievements in a
/// fixed order, keyed by slug, and the claimed blue pebble that ends it.
/// A pure projection of the catalog and the unlocks: no client counts anything.
struct HomeChecklist: Equatable {
    enum Task: CaseIterable, Equatable, Identifiable {
        case moment, pebble, collection, soul, glyph

        var slug: String {
            switch self {
            case .moment: "first-moment"
            case .pebble: "first-pebble"
            case .collection: "first-collection"
            case .soul: "first-soul"
            case .glyph: "first-glyph"
            }
        }

        var id: String { slug }
    }

    static let blueSlug = "blue-pebble"

    struct Row: Equatable {
        let task: Task
        let isDone: Bool
        let unlockedAt: Date?
    }

    enum State: Equatable { case unavailable, inProgress, claimable, done }

    let rows: [Row]
    let state: State

    var doneCount: Int { rows.filter(\.isDone).count }
    var canClaim: Bool { state == .claimable }

    /// `unlocked` are slugs; `unlockedAt` optional dates by slug for the rows.
    /// Unavailable when one of the six rows is missing, or retired and never
    /// unlocked (an unlock is permanent, so a retired row the user holds still
    /// counts); done once the blue pebble is unlocked.
    static func make(catalog: [AchievementRecord], unlocked: Set<String>,
                     unlockedAt: [String: Date] = [:]) -> HomeChecklist {
        let rows = Task.allCases.map {
            Row(task: $0, isDone: unlocked.contains($0.slug), unlockedAt: unlockedAt[$0.slug])
        }
        let bySlug = Dictionary(catalog.map { ($0.slug, $0) }, uniquingKeysWith: { first, _ in first })
        let slugs = Task.allCases.map(\.slug) + [blueSlug]
        let available = slugs.allSatisfy { slug in
            guard let record = bySlug[slug] else { return false }
            return record.isActive || unlocked.contains(slug)
        }
        let state: State
        if !available {
            state = .unavailable
        } else if unlocked.contains(blueSlug) {
            state = .done
        } else if rows.allSatisfy(\.isDone) {
            state = .claimable
        } else {
            state = .inProgress
        }
        return HomeChecklist(rows: rows, state: state)
    }
}
