import SwiftUI

/// Inline photo "chip" shown inside `PebbleFormView` once the user has picked
/// an image. Displays the local thumbnail, an upload-state badge, and lets the
/// user remove the attachment or retry a failed upload.
struct AttachedPhotoView: View {

    let snap: AttachedSnap
    let onRemove: () -> Void
    let onRetry: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            thumbnail
            VStack(alignment: .leading, spacing: 4) {
                Text("attached_photo.title")
                    .font(.subheadline)
                stateLabel
            }
            Spacer()
            trailingButton
        }
    }

    @ViewBuilder
    private var thumbnail: some View {
        if let uiImage = UIImage(data: snap.localThumb) {
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
    private var stateLabel: some View {
        switch snap.state {
        case .uploading:
            Label("attached_photo.state.uploading", systemImage: "arrow.up.circle")
                .labelStyle(.titleAndIcon)
                .font(.caption)
                .foregroundStyle(.secondary)
        case .uploaded:
            Label("attached_photo.state.uploaded", systemImage: "checkmark.circle.fill")
                .labelStyle(.titleAndIcon)
                .font(.caption)
                .foregroundStyle(.green)
        case .failed:
            Label("attached_photo.state.failed", systemImage: "exclamationmark.triangle.fill")
                .labelStyle(.titleAndIcon)
                .font(.caption)
                .foregroundStyle(.red)
        }
    }

    @ViewBuilder
    private var trailingButton: some View {
        switch snap.state {
        case .uploading:
            ProgressView()
        case .uploaded:
            Button(role: .destructive, action: onRemove) {
                Image(systemName: "xmark.circle.fill")
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("attached_photo.action.remove")
        case .failed:
            HStack(spacing: 8) {
                Button(action: onRetry) {
                    Image(systemName: "arrow.clockwise.circle.fill")
                }
                .buttonStyle(.plain)
                .accessibilityLabel("attached_photo.action.retry")
                Button(role: .destructive, action: onRemove) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("attached_photo.action.remove")
            }
        }
    }
}
