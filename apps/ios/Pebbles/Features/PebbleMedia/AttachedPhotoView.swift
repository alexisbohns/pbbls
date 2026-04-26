import SwiftUI

/// Inline photo "chip" shown inside `PebbleFormView` once the user has picked
/// an image. Operates entirely on a `Binding<AttachedSnap?>`:
///   - "Remove" sets the binding to nil; the parent observes via `.onChange`
///     and fires the compensating Storage delete.
///   - "Retry" mutates `snap.state` to `.uploading`; the parent observes the
///     transition (failed → uploading) and re-runs the upload.
struct AttachedPhotoView: View {

    @Binding var snap: AttachedSnap?

    var body: some View {
        if let current = snap {
            HStack(spacing: 12) {
                thumbnail(current)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Photo")
                        .font(.subheadline)
                    stateLabel(current.state)
                }
                Spacer()
                trailingButton(current.state)
            }
        }
    }

    @ViewBuilder
    private func thumbnail(_ current: AttachedSnap) -> some View {
        if let uiImage = UIImage(data: current.localThumb) {
            Image(uiImage: uiImage)
                .resizable()
                .scaledToFill()
                .frame(width: 56, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: 8))
        } else {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.secondary.opacity(0.2))
                .frame(width: 56, height: 56)
        }
    }

    @ViewBuilder
    private func stateLabel(_ state: AttachedSnap.UploadState) -> some View {
        switch state {
        case .uploading:
            Label("Uploading…", systemImage: "arrow.up.circle")
                .labelStyle(.titleAndIcon)
                .font(.caption)
                .foregroundStyle(.secondary)
        case .uploaded:
            Label("Ready", systemImage: "checkmark.circle.fill")
                .labelStyle(.titleAndIcon)
                .font(.caption)
                .foregroundStyle(.green)
        case .failed:
            Label("Upload failed", systemImage: "exclamationmark.triangle.fill")
                .labelStyle(.titleAndIcon)
                .font(.caption)
                .foregroundStyle(.red)
        }
    }

    @ViewBuilder
    private func trailingButton(_ state: AttachedSnap.UploadState) -> some View {
        switch state {
        case .uploading:
            ProgressView()
        case .uploaded:
            removeButton
        case .failed:
            HStack(spacing: 8) {
                Button {
                    snap?.state = .uploading
                } label: {
                    Image(systemName: "arrow.clockwise.circle.fill")
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Retry")
                removeButton
            }
        }
    }

    private var removeButton: some View {
        Button(role: .destructive) {
            snap = nil
        } label: {
            Image(systemName: "xmark.circle.fill")
                .foregroundStyle(.secondary)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Remove photo")
    }
}
