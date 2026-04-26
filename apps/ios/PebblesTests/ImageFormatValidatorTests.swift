import Foundation
import Testing
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
}
