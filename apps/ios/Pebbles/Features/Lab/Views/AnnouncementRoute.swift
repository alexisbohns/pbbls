import SwiftUI
import os

/// Shows `YouRoute.announcement(id)` (#1382 D21): routes carry ids, never
/// models, so the announcement is read again by id (the `SoulRoute` shape).
/// Loading shows a spinner; a missing or failed read logs and pops.
struct AnnouncementRoute: View {
    let id: UUID

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss
    @State private var resolution: RouteResolution<Log> = .loading

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "lab.announcement-route")

    var body: some View {
        Group {
            switch resolution {
            case .found(let log):
                AnnouncementDetailView(log: log, coverImageURL: LabConfig.coverURL(for: log))
            case .missing:
                Color.clear
            case .loading:
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .pebblesScreen()
            }
        }
        .task(id: id) { await resolve() }
    }

    private func resolve() async {
        do {
            if let log = try await LogsService(supabase: supabase).announcement(id: id) {
                resolution = .found(log)
                return
            }
            logger.error("announcement \(id, privacy: .public) is gone or unpublished")
        } catch {
            logger.error("announcement read failed: \(error.localizedDescription, privacy: .private)")
        }
        resolution = .missing
        dismiss()
    }
}
