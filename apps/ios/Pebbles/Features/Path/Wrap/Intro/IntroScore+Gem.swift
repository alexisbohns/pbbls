import Foundation

/// A family of the gem in the intro: its slug and its facets in sweep order.
struct IntroGemRun: Hashable, Sendable {
    let slug: String
    let facets: [String]
}

/// The gem's beats (#1241 §Choreography): the stone lights family by family.
extension IntroScore {
    static let blankAt: TimeInterval = 0.32
    static let facetsAt: TimeInterval = 0.52
    static let facetGap: TimeInterval = 0.045
    static let familyPause: TimeInterval = 0.22
    static let countAfter: TimeInterval = 0.16

    /// The gem: its head and lead, the blank stone, then each family's facets
    /// in turn (its name and share with its first facet), a pause between
    /// families, and the count in the table last, with *more*.
    static func gem(runs: [IntroGemRun]) -> [IntroBeat] {
        var beats = head(hasLead: true, insights: false)
        beats.append(IntroBeat(key: .blank, at: blankAt, duration: IntroMotion.rise))
        var at = facetsAt
        var last = facetsAt
        for (index, run) in runs.enumerated() {
            if index > 0 { at += familyPause }
            for (place, facet) in run.facets.enumerated() {
                if place == 0 {
                    beats.append(IntroBeat(key: .label(run.slug), at: at, duration: IntroMotion.rise))
                }
                beats.append(IntroBeat(key: .facet(facet), at: at, duration: IntroMotion.light, tick: .facet))
                last = at
                at += facetGap
            }
        }
        beats.append(IntroBeat(key: .count, at: last + countAfter, duration: IntroMotion.rise, tick: .firm))
        beats.append(IntroBeat(key: .more, at: last + countAfter, duration: IntroMotion.rise))
        return beats
    }

    /// The families in `order` that own facets in the server's allocation, each with its facets in sweep order.
    static func gemRuns(order: [String], facets: [String: String?]) -> [IntroGemRun] {
        order.compactMap { slug in
            let owned = GemGeometry.allocated.compactMap { id -> (id: String, theta: Double)? in
                guard (facets[id] ?? nil) == slug, let theta = GemGeometry.byId[id]?.theta else { return nil }
                return (id, theta)
            }
            return owned.isEmpty ? nil : IntroGemRun(slug: slug, facets: sweep(owned))
        }
    }

    /// A run's facets clockwise from the top, starting after the widest gap
    /// between them, so a run that crosses the top still lights as one sweep.
    static func sweep(_ facets: [(id: String, theta: Double)]) -> [String] {
        let turn = Double(GemGeometry.sides)
        let around = facets
            .map { (id: $0.id, theta: ($0.theta.truncatingRemainder(dividingBy: turn) + turn)
                .truncatingRemainder(dividingBy: turn)) }
            .sorted { $0.theta < $1.theta }
        guard around.count > 1 else { return around.map(\.id) }
        let gaps = around.indices.map { index in
            index == 0 ? around[0].theta + turn - around[around.count - 1].theta
                : around[index].theta - around[index - 1].theta
        }
        let start = gaps.indices.max { gaps[$0] < gaps[$1] } ?? 0
        return around.indices.map { around[(start + $0) % around.count].id }
    }
}
