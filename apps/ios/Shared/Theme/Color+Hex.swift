import SwiftUI

/// Parses `#RRGGBB` and `#RRGGBBAA` strings. The 6-digit form is used by the
/// legacy `EmotionRef.color` column; the 8-digit form is used by the four
/// palette columns on `public.emotion_categories` (alpha lives in the last
/// byte). Returns `nil` for any other length — caller decides on a default.
extension Color {
    init?(hex: String) {
        var trimmed = hex.trimmingCharacters(in: .whitespaces)
        if trimmed.hasPrefix("#") { trimmed.removeFirst() }
        guard let value = UInt32(trimmed, radix: 16) else { return nil }
        switch trimmed.count {
        case 6:
            let red   = Double((value >> 16) & 0xFF) / 255.0
            let green = Double((value >> 8) & 0xFF) / 255.0
            let blue  = Double(value & 0xFF) / 255.0
            self.init(red: red, green: green, blue: blue)
        case 8:
            let red   = Double((value >> 24) & 0xFF) / 255.0
            let green = Double((value >> 16) & 0xFF) / 255.0
            let blue  = Double((value >> 8) & 0xFF) / 255.0
            let alpha = Double(value & 0xFF) / 255.0
            self.init(red: red, green: green, blue: blue, opacity: alpha)
        default:
            return nil
        }
    }
}
