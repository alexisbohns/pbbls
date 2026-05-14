import SwiftUI
import os

/// The Lab tab — a living product-transparency view. Featured community
/// card at the top, then announcements, changelog (top 5 + see all),
/// ongoing initiatives, and backlog (top 5 by upvotes + see all).
struct LabView: View {
    private static let feedLimit = 5

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.locale) private var locale

    @State private var announcements: [Log] = []
    @State private var changelog: [Log] = []
    @State private var initiatives: [Log] = []
    @State private var backlog: [Log] = []
    @State private var reactedIds: Set<UUID> = []
    @State private var isLoading = true
    @State private var loadError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "lab")

    private var service: LogsService { LogsService(supabase: supabase) }

    var body: some View {
        content
            .navigationTitle("Lab")
            .pebblesScreen()
            .task { await load() }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            Text(loadError)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            List {
                Section {
                    FeaturedCommunityCard()
                        .listRowBackground(Color.pebblesListRow)
                }

                if !announcements.isEmpty {
                    Section("Announcements") {
                        ForEach(announcements) { log in
                            NavigationLink {
                                AnnouncementDetailView(
                                    log: log,
                                    coverImageURL: coverURL(for: log)
                                )
                            } label: {
                                AnnouncementRow(
                                    log: log,
                                    coverImageURL: coverURL(for: log)
                                )
                            }
                            .listRowBackground(Color.pebblesListRow)
                        }
                    }
                }

                if !changelog.isEmpty {
                    Section("Changelog") {
                        LogTimeline(mode: .changelog, logs: changelog)
                        NavigationLink {
                            LogListView(mode: .changelog)
                        } label: {
                            Label("See all", systemImage: "arrow.right")
                                .font(.footnote.weight(.semibold))
                        }
                        .listRowBackground(Color.pebblesListRow)
                    }
                }

                if !initiatives.isEmpty {
                    Section("In progress") {
                        LogTimeline(mode: .inProgress, logs: initiatives)
                    }
                }

                if !backlog.isEmpty {
                    Section("Backlog") {
                        LogTimeline(mode: .backlog, logs: backlog) { log in
                            ReactionButton(
                                count: log.reactionCount,
                                isReacted: reactedIds.contains(log.id)
                            ) {
                                Task { await toggle(log) }
                            }
                        }
                        NavigationLink {
                            LogListView(mode: .backlog)
                        } label: {
                            Label("See all", systemImage: "arrow.right")
                                .font(.footnote.weight(.semibold))
                        }
                        .listRowBackground(Color.pebblesListRow)
                    }
                }
            }
        }
    }

    // MARK: - Data

    private func load() async {
        async let announcementsResult: [Log] = service.announcements()
        async let changelogResult: [Log] = service.changelog(limit: Self.feedLimit)
        async let initiativesResult: [Log] = service.initiatives()
        async let backlogResult: [Log] = service.backlog(limit: Self.feedLimit)
        async let reactionsResult: Set<UUID> = service.myReactions()

        // Each feed loads independently so a single transient failure (e.g.
        // reactions) doesn't blank out the whole Lab. The fullscreen error
        // is reserved for the case where every content feed fails.
        var announcements: [Log]?
        var changelog: [Log]?
        var initiatives: [Log]?
        var backlog: [Log]?
        var reactedIds: Set<UUID>?

        do { announcements = try await announcementsResult } catch { logFetchFailure(error, "announcements") }
        do { changelog = try await changelogResult } catch { logFetchFailure(error, "changelog") }
        do { initiatives = try await initiativesResult } catch { logFetchFailure(error, "initiatives") }
        do { backlog = try await backlogResult } catch { logFetchFailure(error, "backlog") }
        do { reactedIds = try await reactionsResult } catch { logFetchFailure(error, "reactions") }

        self.announcements = announcements ?? []
        self.changelog = changelog ?? []
        self.initiatives = initiatives ?? []
        self.backlog = backlog ?? []
        self.reactedIds = reactedIds ?? []

        let allContentFailed = announcements == nil
            && changelog == nil
            && initiatives == nil
            && backlog == nil
        if allContentFailed {
            self.loadError = "Couldn't load the Lab."
        }
        self.isLoading = false
    }

    private func logFetchFailure(_ error: Error, _ label: String) {
        logger.error("lab \(label) fetch failed: \(error.localizedDescription, privacy: .private)")
    }

    private func toggle(_ log: Log) async {
        let wasReacted = reactedIds.contains(log.id)
        if wasReacted {
            reactedIds.remove(log.id)
        } else {
            reactedIds.insert(log.id)
        }
        adjustBacklogCount(for: log.id, by: wasReacted ? -1 : 1)

        do {
            if wasReacted {
                try await service.unreact(logId: log.id)
            } else {
                try await service.react(logId: log.id)
            }
        } catch {
            logger.error("reaction toggle failed: \(error.localizedDescription, privacy: .private)")
            if wasReacted {
                reactedIds.insert(log.id)
            } else {
                reactedIds.remove(log.id)
            }
            adjustBacklogCount(for: log.id, by: wasReacted ? 1 : -1)
        }
    }

    private func adjustBacklogCount(for id: UUID, by delta: Int) {
        guard let idx = backlog.firstIndex(where: { $0.id == id }) else { return }
        backlog[idx] = backlog[idx].withAdjustedCount(by: delta)
    }

    private func coverURL(for log: Log) -> URL? {
        guard let path = log.coverImagePath, !path.isEmpty else { return nil }
        return AppEnvironment.supabaseURL
            .appending(path: "storage/v1/object/public")
            .appending(path: LabConfig.assetsBucket)
            .appending(path: path)
    }
}

#Preview {
    LabView()
        .environment(SupabaseService())
}
