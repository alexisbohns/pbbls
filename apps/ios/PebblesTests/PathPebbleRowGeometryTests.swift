import Foundation
import Testing
@testable import Pebbles
import CoreGraphics

@Suite("PathPebbleRow geometry helpers")
struct PathPebbleRowGeometryTests {

    @Test("rotation alternates: even = -7°, odd = +4°")
    func rotation() {
        #expect(PathPebbleRow.rotationAngle(forPositionIndex: 0) == -7)
        #expect(PathPebbleRow.rotationAngle(forPositionIndex: 1) == 4)
        #expect(PathPebbleRow.rotationAngle(forPositionIndex: 2) == -7)
        #expect(PathPebbleRow.rotationAngle(forPositionIndex: 3) == 4)
    }

    @Test("row height: small/medium without photo = 60pt")
    func smallNoPhoto() {
        #expect(PathPebbleRow.rowHeight(intensity: 1, hasPhoto: false, positionIndex: 0) == 60)
        #expect(PathPebbleRow.rowHeight(intensity: 2, hasPhoto: false, positionIndex: 5) == 60)
    }

    @Test("row height: small/medium with +4° photo (odd) = 68pt")
    func smallPhotoOdd() {
        #expect(PathPebbleRow.rowHeight(intensity: 1, hasPhoto: true, positionIndex: 1) == 68)
        #expect(PathPebbleRow.rowHeight(intensity: 2, hasPhoto: true, positionIndex: 3) == 68)
    }

    @Test("row height: small/medium with -7° photo (even) = 71pt")
    func smallPhotoEven() {
        #expect(PathPebbleRow.rowHeight(intensity: 1, hasPhoto: true, positionIndex: 0) == 71)
        #expect(PathPebbleRow.rowHeight(intensity: 2, hasPhoto: true, positionIndex: 2) == 71)
    }

    @Test("row height: large = 100pt regardless of photo state")
    func largeRow() {
        #expect(PathPebbleRow.rowHeight(intensity: 3, hasPhoto: false, positionIndex: 0) == 100)
        #expect(PathPebbleRow.rowHeight(intensity: 3, hasPhoto: true, positionIndex: 0) == 100)
        #expect(PathPebbleRow.rowHeight(intensity: 3, hasPhoto: true, positionIndex: 1) == 100)
    }
}
