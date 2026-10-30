import CoreGraphics

/// Points equal to a millionth of a point: the geometry's tests compare places, not floats.
func nearly(_ a: CGPoint, _ b: CGPoint, _ tolerance: CGFloat = 0.000_001) -> Bool {
    abs(a.x - b.x) < tolerance && abs(a.y - b.y) < tolerance
}

func distance(_ a: CGPoint, _ b: CGPoint) -> CGFloat {
    ((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)).squareRoot()
}
