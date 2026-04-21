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
        NavigationStack {
            content
                .navigationTitle("Lab")
                .pebblesScreen()
        }
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
                        ForEach(changelog) { log in
                            LogRow(log: log)
                                .listRowBackground(Color.pebblesListRow)
                        }
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
                        ForEach(initiatives) { log in
                            LogRow(log: log)
                                .listRowBackground(Color.pebblesListRow)
                        }
                    }
                }

                if !backlog.isEmpty {
                    Section("Backlog") {
                        ForEach(backlog) { log in
                            LogRow(log: log) {
                                ReactionButton(
                                    count: log.reactionCount,
                                    isReacted: reactedIds.contains(log.id)
                                ) {
                                    Task { await toggle(log) }
                                }
                            }
                            .listRowBackground(Color.pebblesListRow)
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
        do {
            async let announcementsResult: [Log] = service.announcements()
            async let changelogResult: [Log] = service.changelog(limit: Self.feedLimit)
            async let initiativesResult: [Log] = service.initiatives()
            async let backlogResult: [Log] = service.backlog(limit: Self.feedLimit)
            async let reactionsResult: Set<UUID> = service.myReactions()

            self.announcements = try await announcementsResult
            self.changelog = try await changelogResult
            self.initiatives = try await initiativesResult
            self.backlog = try await backlogResult
            self.reactedIds = try await reactionsResult
            self.isLoading = false
        } catch {
            logger.error("lab fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load the Lab."
            self.isLoading = false
        }
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
