import Foundation
import Testing
@testable import Pebbles

@Suite("SharedPebbleLink")
struct SharedPebbleLinkTests {
    @Test("builds the canonical lowercase /p URL")
    func canonicalURL() {
        let id = UUID(uuidString: "BC74BA6F-A1F6-4E8C-881B-CF0488D647F7")!
        let url = SharedPebbleLink.url(for: id)
        #expect(url.absoluteString == "https://www.pbbls.app/p/bc74ba6f-a1f6-4e8c-881b-cf0488d647f7")
    }
}
