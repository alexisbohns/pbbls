import Foundation
import ImageIO

/// Pure UTI gate for picked image bytes.
/// Accept only formats `ImageIO`'s JPEG encoder can ingest natively on iOS 17.
enum ImageFormatValidator {

    static let supportedUTIs: Set<String> = [
        "public.jpeg",
        "public.png",
        "public.heic",
        "public.heif"
    ]

    static func isSupported(_ uti: String) -> Bool {
        supportedUTIs.contains(uti)
    }

    /// The format of the bytes themselves, or nil when they are not an image
    /// `ImageIO` can identify.
    ///
    /// `PHPickerResult` names the representation it hands over;
    /// `PhotosPickerItem.loadTransferable` does not — it only advertises a list
    /// of `supportedContentTypes` and picks one for you. Reading the type back
    /// off the bytes is the only answer that cannot disagree with what the
    /// pipeline is about to decode.
    static func uti(of data: Data) -> String? {
        guard !data.isEmpty,
              let source = CGImageSourceCreateWithData(data as CFData, nil),
              let type = CGImageSourceGetType(source)
        else {
            return nil
        }
        return type as String
    }
}
