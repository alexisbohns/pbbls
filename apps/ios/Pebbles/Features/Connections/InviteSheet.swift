import CoreImage.CIFilterBuiltins
import SwiftUI
import os

/// Your invite: one link, shown as text and as a QR so a friend across the
/// table can just scan it. The same invite is handed back on every open until
/// you ask for a new one — a link already shared stays alive (design D3).
struct InviteSheet: View {
    @Environment(ConnectionsService.self) private var connections
    @Environment(\.dismiss) private var dismiss

    @State private var invite: ConnectionInvite?
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isRotating = false
    @State private var didCopy = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "connections-invite")

    var body: some View {
        NavigationStack {
            content
                .pebblesToolbarTitle("Invite")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        PebbleToolbarButton("Done") { dismiss() }
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
        } else if let loadError {
            VStack(spacing: Spacing.md) {
                Text(loadError)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.system.secondary)
                    .multilineTextAlignment(.center)
                Button("Try again") { Task { await load() } }
            }
            .padding(.horizontal, Spacing.xl)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let invite {
            ScrollView {
                VStack(spacing: Spacing.lg) {
                    Text("Anyone who opens this link can connect with you. It works for several people, so one code is enough for a whole table.")
                        .pebblesFont(.callout)
                        .foregroundStyle(Color.system.secondary)
                        .multilineTextAlignment(.center)

                    qrCode(for: invite.url)

                    Text(invite.url.absoluteString)
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.system.secondary)
                        .multilineTextAlignment(.center)
                        .textSelection(.enabled)

                    HStack(spacing: Spacing.sm) {
                        Button {
                            UIPasteboard.general.string = invite.url.absoluteString
                            didCopy = true
                        } label: {
                            Label(didCopy ? "Copied" : "Copy link", systemImage: didCopy ? "checkmark" : "doc.on.doc")
                        }
                        .buttonStyle(.bordered)

                        ShareLink(item: invite.url) {
                            Label("Share", systemImage: "square.and.arrow.up")
                        }
                        .buttonStyle(.borderedProminent)
                    }

                    Text("Expires \(invite.expiresAt, format: .relative(presentation: .named)).")
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.system.secondary)

                    Button(role: .destructive) {
                        Task { await rotate() }
                    } label: {
                        if isRotating {
                            ProgressView()
                        } else {
                            Text("Get a new link")
                        }
                    }
                    .disabled(isRotating)
                    .padding(.top, Spacing.sm)

                    Text("A new link retires this one. Anyone still holding it won't be able to connect.")
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.system.secondary)
                        .multilineTextAlignment(.center)
                }
                .padding(Spacing.lg)
            }
        }
    }

    /// Renders the link as a QR. `CIFilter.qrCodeGenerator` outputs a tiny
    /// bitmap, so it is scaled up before rasterizing to keep the modules crisp.
    @ViewBuilder
    private func qrCode(for url: URL) -> some View {
        if let image = Self.qrImage(from: url.absoluteString) {
            Image(decorative: image, scale: 1)
                .interpolation(.none)
                .resizable()
                .scaledToFit()
                .frame(maxWidth: 220, maxHeight: 220)
                .padding(Spacing.md)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .accessibilityLabel("QR code for your invite link")
        } else {
            // The link text and share button above remain fully usable, so a
            // QR failure degrades rather than blocking the flow.
            EmptyView()
        }
    }

    private static func qrImage(from string: String) -> CGImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        let scaled = output.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        return CIContext().createCGImage(scaled, from: scaled.extent)
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            invite = try await connections.createInvite()
        } catch {
            logger.error("invite load failed: \(error.localizedDescription, privacy: .private)")
            loadError = ConnectionsError.from(error).errorDescription
        }
        isLoading = false
    }

    private func rotate() async {
        isRotating = true
        didCopy = false
        do {
            invite = try await connections.createInvite(rotate: true)
        } catch {
            logger.error("invite rotation failed: \(error.localizedDescription, privacy: .private)")
            loadError = ConnectionsError.from(error).errorDescription
        }
        isRotating = false
    }
}
