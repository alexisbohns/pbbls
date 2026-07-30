import SwiftUI
import os

/// Opened by an invite universal link. Shows who is inviting first, then waits
/// for an explicit tap — accepting is the mutual consent, so it is never fired
/// automatically (design D5/D12).
struct InviteAcceptSheet: View {
    let token: String
    var onAccepted: () -> Void = {}

    @Environment(ConnectionsService.self) private var connections
    @Environment(\.dismiss) private var dismiss

    @State private var preview: InvitePreview?
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isAccepting = false
    @State private var acceptError: String?
    @State private var accepted: AcceptInviteResult?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "connections-accept")

    var body: some View {
        NavigationStack {
            content
                .pebblesToolbarTitle("Invitation")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        PebbleToolbarButton("Close") { dismiss() }
                    }
                }
                .pebblesScreen()
        }
        .task { await load() }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let accepted {
            centeredStack {
                glyphBadge(accepted.peer.glyph)
                Text(accepted.alreadyConnected
                     ? "You're already connected with \(accepted.peer.displayName ?? String(localized: "them"))."
                     : "You're connected with \(accepted.peer.displayName ?? String(localized: "them")).")
                    .pebblesFont(.cardHeading)
                    .foregroundStyle(Color.system.foreground)
                    .multilineTextAlignment(.center)
                Button("Done") {
                    onAccepted()
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
            }
        } else if let loadError {
            centeredStack {
                Text(loadError)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.system.secondary)
                    .multilineTextAlignment(.center)
                Button("Close") { dismiss() }
            }
        } else if let preview, preview.status == .valid {
            centeredStack {
                glyphBadge(preview.inviter?.glyph)
                Text("\(preview.inviter?.displayName ?? String(localized: "Someone")) invites you to connect.")
                    .pebblesFont(.cardHeading)
                    .foregroundStyle(Color.system.foreground)
                    .multilineTextAlignment(.center)
                Text("You'll both see each other in your connections. You can remove the connection at any time.")
                    .pebblesFont(.meta)
                    .foregroundStyle(Color.system.secondary)
                    .multilineTextAlignment(.center)

                if let acceptError {
                    Text(acceptError)
                        .pebblesFont(.meta)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                }

                Button {
                    Task { await accept() }
                } label: {
                    if isAccepting {
                        ProgressView()
                    } else {
                        Text("Connect")
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(isAccepting)
            }
        } else {
            // Expired, revoked and unknown tokens share one outward state — a
            // withdrawn invite goes fully dark and reveals no inviter.
            centeredStack {
                Image(systemName: "link.badge.plus")
                    .font(.largeTitle)
                    .foregroundStyle(Color.system.secondary)
                Text("This invite link isn't valid anymore.")
                    .pebblesFont(.cardHeading)
                    .foregroundStyle(Color.system.foreground)
                    .multilineTextAlignment(.center)
                Text("Ask for a fresh one and try again.")
                    .pebblesFont(.meta)
                    .foregroundStyle(Color.system.secondary)
                Button("Close") { dismiss() }
            }
        }
    }

    @ViewBuilder
    private func centeredStack<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(spacing: Spacing.md) {
            content()
        }
        .padding(.horizontal, Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    @ViewBuilder
    private func glyphBadge(_ glyph: PeerGlyph?) -> some View {
        if let glyph {
            GlyphThumbnail(strokes: glyph.strokes, side: 72, strokeColor: Color.accent.primary)
                .frame(width: 72, height: 72)
                .accessibilityHidden(true)
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            preview = try await connections.preview(token: token)
        } catch {
            logger.error("invite preview failed: \(error.localizedDescription, privacy: .private)")
            loadError = ConnectionsError.from(error).errorDescription
        }
        isLoading = false
    }

    private func accept() async {
        isAccepting = true
        acceptError = nil
        do {
            accepted = try await connections.accept(token: token)
        } catch {
            logger.error("invite accept failed: \(error.localizedDescription, privacy: .private)")
            acceptError = ConnectionsError.from(error).errorDescription
        }
        isAccepting = false
    }
}
