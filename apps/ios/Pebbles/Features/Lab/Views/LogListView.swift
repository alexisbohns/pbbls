import SwiftUI
import os

/// Full "See more" list for the Lab tab's changelog or backlog section.
/// Re-uses the same data-fetch service as the parent Lab view but without
/// the top-N limit. Backlog mode attaches a reaction toggle on each row.
struct LogListView: View {
    enum Mode {
        case changelog
        case backlog
    }

    let mode: Mode

    @Environment(SupabaseService.self) private var supabase
    @State private var logs: [Log] = []
    @State private var reactedIds: Set<UUID> = []
    @State private var isLoading = true
    @State private var loadError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "lab")

    private var service: LogsService { LogsService(supabase: supabase) }

    var body: some View {
        content
            .navigationTitle(title)
            .pebblesScreen()
            .task { await load() }
    }

    private var title: String {
        switch mode {
        case .changelog: return String(localized: "Changelog")
        case .backlog:   return String(localized: "Backlog")
        }
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
        } else if logs.isEmpty {
            Text("Nothing here yet.")
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            List {
                ForEach(logs) { log in
                    LogRow(log: log) {
                        if mode == .backlog {
                            ReactionButton(
                                count: log.reactionCount,
                                isReacted: reactedIds.contains(log.id)
                            ) {
                                Task { await toggle(log) }
                            }
                        }
                    }
                    .listRowBackground(Color.pebblesListRow)
                }
            }
        }
    }

    private func load() async {
        do {
            async let logsResult: [Log] = {
                switch mode {
                case .changelog: return try await service.changelog()
                case .backlog:   return try await service.backlog()
                }
            }()
            async let reactionsResult: Set<UUID> = service.myReactions()

            self.logs = try await logsResult
            self.reactedIds = try await reactionsResult
            self.isLoading = false
        } catch {
            logger.error("list fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load the list."
            self.isLoading = false
        }
    }

    private func toggle(_ log: Log) async {
        let wasReacted = reactedIds.contains(log.id)
        // Optimistic update.
        if wasReacted {
            reactedIds.remove(log.id)
        } else {
            reactedIds.insert(log.id)
        }
        if let idx = logs.firstIndex(where: { $0.id == log.id }) {
            logs[idx] = logs[idx].withAdjustedCount(by: wasReacted ? -1 : 1)
        }

        do {
            if wasReacted {
                try await service.unreact(logId: log.id)
            } else {
                try await service.react(logId: log.id)
            }
        } catch {
            logger.error("reaction toggle failed: \(error.localizedDescription, privacy: .private)")
            // Revert.
            if wasReacted {
                reactedIds.insert(log.id)
            } else {
                reactedIds.remove(log.id)
            }
            if let idx = logs.firstIndex(where: { $0.id == log.id }) {
                logs[idx] = logs[idx].withAdjustedCount(by: wasReacted ? 1 : -1)
            }
        }
    }
}

