import SwiftUI

/// Hand-ported from the SVG <path d="…"> definitions in issue #442.
/// Authored against a 44×44 viewBox. Each shape scales uniformly when
/// drawn into a non-44×44 rect so RippleBadge renders correctly at
/// any size.

private extension Path {
    /// Apply uniform scale from a 44×44 source viewBox into `rect`.
    mutating func scaleFromRippleViewBox(into rect: CGRect) {
        let scaleX = rect.width / 44
        let scaleY = rect.height / 44
        let transform = CGAffineTransform(scaleX: scaleX, y: scaleY)
            .concatenating(CGAffineTransform(translationX: rect.minX, y: rect.minY))
        self = self.applying(transform)
    }
}

struct RippleStroke1: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 25.4147, y: 30.7822))
        path.addCurve(to: CGPoint(x: 16.4741, y: 29.5504),
                   control1: CGPoint(x: 22.7365, y: 31.9714),
                   control2: CGPoint(x: 19.5086, y: 31.8785))
        path.addCurve(to: CGPoint(x: 27.8664, y: 14.941),
                   control1: CGPoint(x: 6.9764, y: 22.2636),
                   control2: CGPoint(x: 18.3687, y: 7.65428))
        path.addCurve(to: CGPoint(x: 29.2088, y: 27.818),
                   control1: CGPoint(x: 32.662, y: 18.6202),
                   control2: CGPoint(x: 32.1318, y: 24.1663))
        path.scaleFromRippleViewBox(into: rect)
        return path
    }
}

struct RippleStroke2: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 34.1755, y: 13.5272))
        path.addCurve(to: CGPoint(x: 7.58572, y: 25.087),
                   control1: CGPoint(x: 25.9708, y: 1.34962),
                   control2: CGPoint(x: 4.58761, y: 9.44313))
        path.scaleFromRippleViewBox(into: rect)
        return path
    }
}

struct RippleStroke3: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 36.6088, y: 19.5146))
        path.addCurve(to: CGPoint(x: 10, y: 31.0941),
                   control1: CGPoint(x: 39.4844, y: 34.5339),
                   control2: CGPoint(x: 18.0179, y: 42.6778))
        path.scaleFromRippleViewBox(into: rect)
        return path
    }
}

struct RippleStroke4: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 41.458, y: 26.9565))
        path.addCurve(to: CGPoint(x: 11.4043, y: 40.1005),
                   control1: CGPoint(x: 39.2185, y: 38.9628),
                   control2: CGPoint(x: 23.9232, y: 45.4638))
        path.scaleFromRippleViewBox(into: rect)
        return path
    }
}

struct RippleStroke5: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 7.37405, y: 37.1175))
        path.addCurve(to: CGPoint(x: 10.831, y: 4.78223),
                   control1: CGPoint(x: -1.10595, y: 29.2114),
                   control2: CGPoint(x: 0.748869, y: 9.24398))
        path.scaleFromRippleViewBox(into: rect)
        return path
    }
}

struct RippleStroke6: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 15.4023, y: 2.71506))
        path.addCurve(to: CGPoint(x: 41.9999, y: 21.8993),
                   control1: CGPoint(x: 26.241, y: -0.507724),
                   control2: CGPoint(x: 41.9999, y: 7.38652))
        path.scaleFromRippleViewBox(into: rect)
        return path
    }
}
