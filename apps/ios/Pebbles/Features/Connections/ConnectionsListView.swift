import SwiftUI
import os

/// The people you're connected with. Discovery is invite-only by design — no
/// search, no directory (M49, design §1). Removing severs for both sides;
/// removing with a block also stops the peer re-entering through a live invite.
struct ConnectionsListView: View {
    @Environment(ConnectionsService.self) private var connections

    @State private var rows: [Connection] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var pendingRemoval: Connection?
    @State private var removeError: String?
    @State private var isPresentingInvite = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "connections-list")

    var body: some View {
        content
            .pebblesToolbarTitle("Connections")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    PebbleToolbarButton(action: { isPresentingInvite = true }) {
                        Image(systemName: "person.badge.plus")
                    }
                    .accessibilityLabel("Invite someone")
                }
            }
            .pebblesScreen()
            .task { await load() }
            .sheet(isPresented: $isPresentingInvite) {
                InviteSheet()
            }
            .confirmationDialog(
                pendingRemoval.map { removalTitle(for: $0) } ?? "",
                isPresented: Binding(
                    get: { pendingRemoval != nil },
                    set: { if !$0 { pendingRemoval = nil } }
                ),
                titleVisibility: .visible,
                presenting: pendingRemoval
            ) { row in
                Button("Remove", role: .destructive) {
                    Task { await remove(row, block: false) }
                }
                Button("Remove and block", role: .destructive) {
                    Task { await remove(row, block: true) }
                }
                Button("Cancel", role: .cancel) { pendingRemoval = nil }
            } message: { _ in
                Text("Blocking also stops them joining again through your invite link.")
            }
            .alert(
                "Couldn't remove",
                isPresented: Binding(
                    get: { removeError != nil },
                    set: { if !$0 { removeError = nil } }
                ),
                presenting: removeError
            ) { _ in
                Button("OK", role: .cancel) { removeError = nil }
            } message: { message in
                Text(message)
            }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView {
                Label("Couldn't load connections", systemImage: "person.2.slash")
            } description: {
                Text(loadError)
            } actions: {
                Button("Try again") { Task { await load() } }
            }
        } else if rows.isEmpty {
            ContentUnavailableView(
                "No connections yet",
                systemImage: "person.2",
                description: Text("Share your invite link and whoever accepts shows up here.")
            )
        } else {
            List {
                ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                    ConnectionRow(connection: row)
                        .pebblesListRow(position: pebblesRowPosition(index: index, count: rows.count))
                        .contextMenu {
                            Button(role: .destructive) {
                                pendingRemoval = row
                            } label: {
                                Label("Remove", systemImage: "person.badge.minus")
                            }
                        }
                }
            }
            .pebblesList()
        }
    }

    private func removalTitle(for row: Connection) -> String {
        String(localized: "Remove \(row.peer.displayName ?? String(localized: "this person"))?")
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            rows = try await connections.list()
        } catch {
            logger.error("connections load failed: \(error.localizedDescription, privacy: .private)")
            loadError = ConnectionsError.from(error).errorDescription
        }
        isLoading = false
    }

    /// Optimistic: the row leaves immediately, and a failure reloads the truth.
    private func remove(_ row: Connection, block: Bool) async {
        pendingRemoval = nil
        let previous = rows
        rows.removeAll { $0.id == row.id }
        do {
            try await connections.remove(connectionId: row.connectionId, block: block)
        } catch {
            logger.error("connection removal failed: \(error.localizedDescription, privacy: .private)")
            rows = previous
            removeError = ConnectionsError.from(error).errorDescription
        }
    }
}

/// One connected person: their glyph, their name, and when you connected.
private struct ConnectionRow: View {
    let connection: Connection

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Group {
                if let glyph = connection.peer.glyph {
                    GlyphThumbnail(
                        strokes: glyph.strokes,
                        side: 28,
                        strokeColor: Color.accent.primary
                    )
                } else {
                    Image(systemName: "person.circle")
                        .foregroundStyle(Color.system.secondary)
                }
            }
            .frame(width: 32, height: 32)
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 2) {
                Text(connection.peer.displayName ?? String(localized: "Someone"))
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.system.foreground)
                Text(connection.connectedAt, format: .relative(presentation: .named))
                    .pebblesFont(.meta)
                    .foregroundStyle(Color.system.secondary)
            }

            Spacer(minLength: 0)
        }
        .padding(.vertical, 2)
    }
}
