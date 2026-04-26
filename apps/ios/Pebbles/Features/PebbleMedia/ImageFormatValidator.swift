import Foundation

/// Pure UTI gate for `PHPickerResult.itemProvider.registeredTypeIdentifiers`.
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
}
