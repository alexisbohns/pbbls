import Foundation

/// The RECENT row of the Life screen (record-flow D10): the last domains this
/// account picked here, newest first, five at most. Device-local: the Path's
/// summaries carry no domains, and recency from the server would need an RPC
/// the spec defers.
struct RecentDomains {
    static let cap = 5
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) { self.defaults = defaults }

    private func key(_ owner: UUID) -> String { "recentDomains.\(owner.uuidString.lowercased())" }

    func ids(owner: UUID) -> [UUID] {
        (defaults.stringArray(forKey: key(owner)) ?? []).compactMap(UUID.init(uuidString:))
    }

    func record(_ picked: [UUID], owner: UUID) {
        var ordered: [UUID] = []
        for id in picked + ids(owner: owner) where !ordered.contains(id) { ordered.append(id) }
        defaults.set(Array(ordered.prefix(Self.cap)).map(\.uuidString), forKey: key(owner))
    }
}
