import CoreGraphics
import Foundation
import PhotosUI
import SwiftUI
import os

/// What the crop editor opens on (spec "Working image"): the photo decoded
/// upright at 2048 px, and the capture date read from the original bytes
/// before anything drops the metadata (spec D8).
struct CropSource: Identifiable {
    let id = UUID()
    let image: CGImage
    let captureDate: Date?

    static let workingMaxEdge: CGFloat = 2048
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "crop-source")

    /// Bytes in hand (a pick already loaded, or the camera's JPEG). The format
    /// is sniffed off the bytes; `fallbackUTI` is only for bytes ImageIO cannot
    /// identify, and still has to pass the same gate. Nil when the format is
    /// unsupported or the bytes do not decode (logged).
    static func make(from data: Data, fallbackUTI: String? = nil) async -> CropSource? {
        let candidate = ImageFormatValidator.uti(of: data) ?? fallbackUTI ?? ""
        return await decode(data, uti: candidate)
    }

    /// The record flow's inline `PhotosPicker`. A `PhotosPickerItem` carries no
    /// chosen representation, so the bytes come first and the format is read
    /// back off them.
    static func make(from item: PhotosPickerItem) async -> CropSource? {
        let advertised = item.supportedContentTypes.map(\.identifier)
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else {
                logger.warning("picker item carried no data representation")
                return nil
            }
            return await make(from: data, fallbackUTI: advertised.first)
        } catch {
            logger.error("picker item load failed: \(error.localizedDescription, privacy: .private)")
            return nil
        }
    }

    /// The sheet-presented `PHPicker` (`EditMomentSheet`), which names the
    /// representation it hands over.
    static func make(from picked: PhotoPickerView.PickedItem) async -> CropSource? {
        do {
            let data = try await loadData(from: picked.itemProvider, uti: picked.uti)
            return await decode(data, uti: picked.uti)
        } catch {
            logger.error("picker data load failed: \(error.localizedDescription, privacy: .private)")
            return nil
        }
    }

    private static func decode(_ data: Data, uti: String) async -> CropSource? {
        guard ImageFormatValidator.isSupported(uti) else {
            logger.warning("unsupported crop source format: \(uti, privacy: .public)")
            return nil
        }
        // Read before the working image drops the metadata (D8).
        let captureDate = ExifCaptureDate.from(data)
        do {
            let image = try await Task.detached(priority: .userInitiated) {
                try ImagePipeline.decodeOriented(data, maxEdge: CropSource.workingMaxEdge)
            }.value
            return CropSource(image: image, captureDate: captureDate)
        } catch {
            logger.error("crop source decode failed: \(String(describing: error), privacy: .public)")
            return nil
        }
    }

    /// Loads the bytes for a `PHPicker` selection. Bridges the callback-based
    /// `loadDataRepresentation` API into async/await.
    private static func loadData(from provider: NSItemProvider, uti: String) async throws -> Data {
        try await withCheckedThrowingContinuation { continuation in
            provider.loadDataRepresentation(forTypeIdentifier: uti) { data, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let data {
                    continuation.resume(returning: data)
                } else {
                    continuation.resume(throwing: URLError(.cannotDecodeContentData))
                }
            }
        }
    }
}
