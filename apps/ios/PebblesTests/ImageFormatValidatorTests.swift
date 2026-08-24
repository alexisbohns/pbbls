import Foundation
import Testing
import UIKit
@testable import Pebbles

@Suite("ImageFormatValidator")
struct ImageFormatValidatorTests {

    @Test("accepts JPEG, PNG, HEIC, HEIF UTIs")
    func acceptsSupportedUTIs() {
        #expect(ImageFormatValidator.isSupported("public.jpeg"))
        #expect(ImageFormatValidator.isSupported("public.png"))
        #expect(ImageFormatValidator.isSupported("public.heic"))
        #expect(ImageFormatValidator.isSupported("public.heif"))
    }

    @Test("rejects video, gif, webp, and arbitrary UTIs")
    func rejectsUnsupportedUTIs() {
        #expect(!ImageFormatValidator.isSupported("public.movie"))
        #expect(!ImageFormatValidator.isSupported("com.compuserve.gif"))
        #expect(!ImageFormatValidator.isSupported("org.webmproject.webp"))
        #expect(!ImageFormatValidator.isSupported(""))
        #expect(!ImageFormatValidator.isSupported("anything-else"))
    }

    @Test("uti(of:) reads the format back off the bytes")
    @MainActor
    func sniffsRealImageBytes() throws {
        let image = UIGraphicsImageRenderer(size: CGSize(width: 2, height: 2)).image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 2, height: 2))
        }
        let png = try #require(image.pngData())
        let jpeg = try #require(image.jpegData(compressionQuality: 0.8))

        #expect(ImageFormatValidator.uti(of: png) == "public.png")
        #expect(ImageFormatValidator.uti(of: jpeg) == "public.jpeg")
    }

    @Test("uti(of:) returns nil for empty and non-image bytes")
    func sniffRejectsNonImages() {
        #expect(ImageFormatValidator.uti(of: Data()) == nil)
        #expect(ImageFormatValidator.uti(of: Data("not an image".utf8)) == nil)
    }
}
