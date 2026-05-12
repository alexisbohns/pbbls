import SwiftUI

/// Inline photo "chip" shown inside `PebbleFormView` once the user has picked
/// an image. Stateless: takes the current snap plus explicit `onRetry` and
/// `onRemove` callbacks. The parent (which owns a `SnapUploadCoordinator`)
/// decides what those mean.
struct AttachedPhotoView: View {

    let snap: AttachedSnap
    let onRetry: () -> Void
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            thumbnail
            VStack(alignment: .leading, spacing: 4) {
                Text("Photo")
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
    private var trailingButton: some View {
        switch snap.state {
        case .uploading:
            ProgressView()
        case .uploaded:
            removeButton
        case .failed:
            HStack(spacing: 8) {
                Button(action: onRetry) {
                    Image(systemName: "arrow.clockwise.circle.fill")
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Retry")
                removeButton
            }
        }
    }

    private var removeButton: some View {
        Button(role: .destructive, action: onRemove) {
            Image(systemName: "xmark.circle.fill")
                .foregroundStyle(.secondary)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Remove photo")
    }
}
