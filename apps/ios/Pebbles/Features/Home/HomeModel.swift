import Foundation
import os

/// The Home tab's state. Static parts (greeting, shortcuts, About header)
/// render at once; the server projections load in one task, each section
/// keeping its last value while it reloads (spec §1.6). Later parts add
/// wraps, the checklist and the guides here.
@Observable @MainActor final class HomeModel {
    private(set) var name: String = ""
    private(set) var hasLoadedOnce = false
    @ObservationIgnored var service: (any HomeServicing)?
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "home.model")

    func load() async {
        defer { hasLoadedOnce = true }
        guard let service else { return }
        do {
            name = try await service.displayName() ?? ""
        } catch {
            logger.warning("display name failed: \(error.localizedDescription, privacy: .public)")
        }
    }
}
