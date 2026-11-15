import CoreGraphics
import Foundation

/// The five crop formats (spec D3). No free crop: a moment's photo is always
/// one of these shapes. Raw values are the labels the chips never show (the
/// chip draws the shape) but logs and references use.
enum CropRatio: String, CaseIterable, Identifiable, Equatable {
    case fourFive = "4:5"
    case threeFour = "3:4"
    case square = "1:1"
    case fourThree = "4:3"
    case fiveFour = "5:4"

    var id: String { rawValue }

    var width: Int {
        switch self {
        case .fourFive: 4
        case .threeFour: 3
        case .square: 1
        case .fourThree: 4
        case .fiveFour: 5
        }
    }

    var height: Int {
        switch self {
        case .fourFive: 5
        case .threeFour: 4
        case .square: 1
        case .fourThree: 3
        case .fiveFour: 4
        }
    }

    /// Width over height.
    var value: CGFloat { CGFloat(width) / CGFloat(height) }

    /// The same format turned a quarter (spec D5).
    var paired: CropRatio {
        switch self {
        case .fourFive: .fiveFour
        case .threeFour: .fourThree
        case .square: .square
        case .fourThree: .threeFour
        case .fiveFour: .fourFive
        }
    }

    /// The format nearest `ratio` (width over height), compared in log space so
    /// a portrait and its landscape pair sit equally far from square. On a tie
    /// the earlier case wins, and the portrait ones come first.
    static func closest(to ratio: CGFloat) -> CropRatio {
        guard ratio > 0, ratio.isFinite else { return .square }
        let target = log(ratio)
        var best = CropRatio.square
        var bestDistance = CGFloat.infinity
        for candidate in allCases {
            let distance = abs(log(candidate.value) - target)
            if distance < bestDistance {
                best = candidate
                bestDistance = distance
            }
        }
        return best
    }
}
