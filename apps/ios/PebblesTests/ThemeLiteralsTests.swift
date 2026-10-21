import Foundation
import Testing

/// D8 (spec 2026-10-20-ios-m3-colour §7), the iOS twin of Android's
/// ThemeLiteralsTest. Feature code names a colour only as
/// `Color.pebbles.<role>`. The simulator shares the host filesystem, so the test
/// reads the sources from `#filePath` (precedent: StoneLab/StoneLabPalettesTests).
///
/// A file's count is its number of offending *lines* (one line can break two
/// rules: `UIColor(red:` also matches `Color(red`). The ratchet fails when a
/// count rises ("new literal") and when one falls without its `debt` entry
/// being lowered ("lower the debt").
@Suite("Theme literals")
struct ThemeLiteralsTests {

    struct Rule {
        let regex: NSRegularExpression
        let reason: String

        // NSRegularExpression, not a Swift Regex literal: the first rule needs
        // a lookbehind.
        init(_ pattern: String, _ reason: String) {
            guard let regex = try? NSRegularExpression(pattern: pattern) else {
                preconditionFailure("invalid pattern \(pattern)")
            }
            self.regex = regex
            self.reason = reason
        }
    }

    /// SwiftUI's system and absolute colours, as one regex alternation.
    static let absoluteColours = "red|orange|yellow|green|mint|teal|cyan|blue|indigo|purple|pink|brown|gray|white|black"

    static let rules: [Rule] = [
        // The lookbehinds spare font weights: `.fontWeight(.black)` and
        // `weight: .black` name a weight, not a colour.
        Rule(#"(?<![\w.])(?<!fontWeight\()(?<!weight: )(?<!weight:)(Color)?\.("# + absoluteColours + #")\b"#,
             "a system or absolute colour: use a Color.pebbles role"),
        Rule(#"Color\.(primary|secondary|accentColor)\b"#,
             "a system label colour: use onSurface / onSurfaceVariant / primary"),
        Rule(#"Color\(\s*(red|white|hue|\.sRGB|\.displayP3)"#, "a literal colour"),
        Rule(#"Color\(hex:\s*""#, "a hex literal (Color(hex: variable) on server data is fine)"),
        Rule(#"Color\(""#, "a named asset: only the generated PebblesColors.swift names assets"),
        Rule(#"UIColor\((red|white|hue|named|displayP3Red):"#, "a UIKit literal: use UIColor.pebbles(_:)"),
        Rule(#"UIColor\.(system\w+|label|secondaryLabel|tertiaryLabel|white|black|red|clear)\b"#,
             "a UIKit system colour: use UIColor.pebbles(_:)"),
        Rule(#"Color\(\s*(uiColor:\s*)?\."#
                + #"(system\w+|label|secondaryLabel|tertiaryLabel|separator|\w*[Ss]ystem\w*Background)"#,
             "a UIKit system colour via Color(_:): use a Color.pebbles role"),
        Rule(#"role:\s*\.destructive"#, "a destructive role outside a system .alert / .confirmationDialog")
    ]

    static let scannedFolders = ["Features", "Components"]

    /// Not scanned (spec §7).
    static let exemptPrefixes = [
        "Features/Path/Stone/",
        "Features/StoneLab/",
        "Features/Path/Models/EmotionPalette.swift",
        "Components/Buttons/AppleSignInButton.swift",
        "Components/Buttons/GoogleSignInButton.swift"
    ]

    /// Permanent: hits that are not chrome colours.
    static let allowlist: [String: (count: Int, reason: String)] = [
        "Features/Path/Valence/ValenceStoneStyle.swift": (1, "joySurface, Joy's emotion surface (data)"),
        "Features/Path/Read/PebbleSnapFrame.swift": (1, "preview fixture image drawn above #Preview"),
        "Features/Path/Components/WeekPathView.swift": (2, "fade mask stops (alpha only)"),
        "Features/Path/Render/PebbleAnimatedRenderView.swift": (1, "stroke-reveal mask"),
        "Features/Welcome/HandcraftedLogoView.swift": (1, "stroke-reveal mask"),
        "Features/Connections/InviteSheet.swift": (1, "QR quiet zone must be white for scanners"),
        "Features/Capture/CaptureFlowView.swift": (1, "role: .destructive in a system dialog"),
        "Features/Path/MomentDeleteDialog.swift": (1, "role: .destructive in a system dialog"),
        "Features/Profile/Sheets/SettingsSheet.swift": (1, "role: .destructive in a system dialog"),
        "Features/Profile/Lists/SoulsListView.swift": (1, "role: .destructive in a system dialog"),
        "Features/Connections/ConnectionsListView.swift": (2, "role: .destructive in a system dialog"),
        "Features/Profile/Lists/CollectionsListView.swift": (1, "role: .destructive in a system dialog"),
        "Features/Glyph/Views/GlyphCarveSheet.swift": (1, "role: .destructive in a system dialog")
    ]

    /// Shrinks to nothing by the end of the stack (A2: .red and owned
    /// destructive roles; A3a / A3b: the folders they migrate). Never raise it.
    static let debt: [String: Int] = [
        "Features/Glyph/Views/GlyphCanvasView.swift": 1,
        "Features/Glyph/Views/GlyphPickerContent.swift": 1,
        "Features/Glyph/Views/GlyphTabBar.swift": 2,
        "Features/Glyph/Views/SlideToConfirm.swift": 1,
        "Features/Karma/AchievementMomentView.swift": 1,
        "Features/Karma/KarmaEarnedCapsule.swift": 2,
        "Features/Lab/Components/FeaturedCommunityCard.swift": 1,
        "Features/Path/Components/PathPebbleRow.swift": 2,
        "Features/Path/Components/WeekPathView.swift": 1,
        "Features/Path/Valence/ValenceStoneStyle.swift": 1,
        "Features/PebbleMedia/AttachedPhotoView.swift": 2,
        "Features/PebbleMedia/ExistingSnapRow.swift": 2,
        "Features/PebbleMedia/SnapImageView.swift": 1,
        "Features/Record/RecordStepScaffold.swift": 1,
        "Features/Welcome/WelcomeView.swift": 1
    ]

    /// `apps/ios/Pebbles`, two levels up from this file (`apps/ios/PebblesTests/`).
    static let pebblesRoot: URL = {
        let iosRoot = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
        let root = iosRoot.appendingPathComponent("Pebbles")
        var isDirectory: ObjCBool = false
        guard FileManager.default.fileExists(atPath: root.path, isDirectory: &isDirectory), isDirectory.boolValue else {
            preconditionFailure("ThemeLiteralsTests expects the app sources at \(root.path)")
        }
        return root
    }()

    /// A string literal on one line, escapes included. Its contents are blanked
    /// before the `//` split, so a `"https://…"` does not hide the rest of the
    /// line, and words inside strings never match. The quotes stay, which keeps
    /// `Color("…")` and `Color(hex: "…")` detectable. Multi-line `"""` strings
    /// are not modelled: their inner lines are scanned as code.
    static let stringLiteral: NSRegularExpression = {
        guard let regex = try? NSRegularExpression(pattern: #""(?:[^"\\]|\\.)*""#) else {
            preconditionFailure("invalid string-literal pattern")
        }
        return regex
    }()

    /// One line with its string contents blanked and its `//` comment dropped.
    static func code(of line: String) -> String {
        let range = NSRange(line.startIndex..., in: line)
        let blanked = stringLiteral.stringByReplacingMatches(in: line, range: range, withTemplate: "\"\"")
        return blanked.components(separatedBy: "//")[0]
    }

    /// Offending lines of one source. Comments are ignored (see `code(of:)`), and
    /// so is each brace-balanced `#Preview { … }` block, since preview fixtures
    /// fake server data. Code after a preview block is scanned again.
    static func offendingLines(in source: String) -> [(line: Int, reason: String)] {
        var hits: [(line: Int, reason: String)] = []
        var previewDepth: Int?
        for (index, rawLine) in source.components(separatedBy: "\n").enumerated() {
            let text = code(of: rawLine)
            if previewDepth == nil, text.trimmingCharacters(in: .whitespaces).hasPrefix("#Preview") {
                previewDepth = 0
            }
            if let depth = previewDepth {
                let opened = text.filter { $0 == "{" }.count
                let next = depth + opened - text.filter { $0 == "}" }.count
                // The block ends once its braces balance after opening.
                previewDepth = (opened > 0 || depth > 0) && next <= 0 ? nil : next
                continue
            }
            let range = NSRange(text.startIndex..., in: text)
            if let rule = rules.first(where: { $0.regex.firstMatch(in: text, range: range) != nil }) {
                hits.append((index + 1, rule.reason))
            }
        }
        return hits
    }

    static func scan() throws -> [String: [(line: Int, reason: String)]] {
        var result: [String: [(line: Int, reason: String)]] = [:]
        let rootDepth = pebblesRoot.pathComponents.count
        for folder in scannedFolders {
            let base = pebblesRoot.appendingPathComponent(folder)
            guard let walker = FileManager.default.enumerator(at: base, includingPropertiesForKeys: nil) else {
                continue
            }
            for case let url as URL in walker where url.pathExtension == "swift" {
                let relative = url.pathComponents.dropFirst(rootDepth).joined(separator: "/")
                if exemptPrefixes.contains(where: { relative.hasPrefix($0) }) { continue }
                let hits = try offendingLines(in: String(contentsOf: url, encoding: .utf8))
                if !hits.isEmpty { result[relative] = hits }
            }
        }
        return result
    }

    // MARK: - Rules

    @Test("each rule catches its target and spares the allowed spellings", arguments: [
        (".foregroundStyle(.red)", true),
        ("return SIMD3<Float>(resolved.red, resolved.green, resolved.blue)", false),
        (".foregroundStyle(.secondary)", false),
        (".fill(Color.secondary.opacity(0.2))", true),
        (".background(Color.white)", true),
        (".shadow(color: .black.opacity(0.22), radius: 14, y: 4)", true),
        ("Color(hex: palette.primaryHex)", false),
        ("Color(hex: \"#A15C081A\") ?? .clear", true),
        ("Color(\"SystemMuted\")", true),
        ("UIColor(red: 0.42, green: 0.48, blue: 0.56, alpha: 1).setFill()", true),
        ("Color(.sRGB, red: 1, green: 1, blue: 1)", true),
        ("UIColor(named: \"AccentColor\")", true),
        ("let muted = UIColor.systemGray5", true),
        ("Button(role: .destructive) {", true),
        (".foregroundStyle(Color.pebbles.error)", false),
        ("proxy.backgroundColor = UIColor.pebbles(.surfaceContainerHighest)", false),
        (".tint(.primary) // was Color.white", false),
        (".fontWeight(.black)", false),
        (".font(.system(size: 12, weight: .black))", false),
        ("Color(.systemGray6)", true),
        ("Color(uiColor: .label)", true),
        (".background(Color(.secondarySystemBackground))", true),
        ("Color(uiColor: UIColor.pebbles(.surface).resolvedColor(with: traits))", false),
        ("Link(\"Terms\", destination: URL(string: \"https://pbbls.app\")!).tint(.blue)", true),
        ("Text(\"Use .red sparingly\")", false)
    ])
    func rule(line: String, offends: Bool) {
        #expect(Self.offendingLines(in: line).isEmpty != offends, "\(line)")
    }

    @Test("a #Preview block is skipped and the code after it is scanned")
    func previewIsExempt() {
        let preview = "#Preview {\n    VStack {\n        Text(\"x\").foregroundStyle(.red)\n    }\n}\n"
        #expect(Self.offendingLines(in: "let ink = Color.pebbles.onSurface\n" + preview).isEmpty)
        let after = Self.offendingLines(in: preview + "private let leak = Color.white\n")
        #expect(after.map(\.line) == [6])
    }

    // MARK: - Ratchet

    @Test("feature code names colours only through Color.pebbles")
    func ratchet() throws {
        let found = try Self.scan()
        var allowed = Self.debt
        for (path, entry) in Self.allowlist { allowed[path, default: 0] += entry.count }

        var problems: [String] = []
        for path in Set(found.keys).union(allowed.keys).sorted() {
            let hits = found[path] ?? []
            let limit = allowed[path] ?? 0
            if hits.count > limit {
                let lines = hits.map { "  \(path):\($0.line) — \($0.reason)" }.joined(separator: "\n")
                problems.append("\(path): \(hits.count) offending lines, \(limit) allowed — new literal.\n\(lines)")
            } else if hits.count < limit {
                problems.append("\(path): \(hits.count) offending lines, \(limit) allowed — lower the debt.")
            }
        }
        // Paste-ready: the debt a file still owes once its permanent allowlist
        // entry is taken out. Files that owe nothing are left out.
        let pasteReady = found.keys.sorted().compactMap { path -> String? in
            let owed = max(0, (found[path]?.count ?? 0) - (Self.allowlist[path]?.count ?? 0))
            return owed > 0 ? "\"\(path)\": \(owed)," : nil
        }.joined(separator: "\n")
        let summary = problems.joined(separator: "\n")
        #expect(problems.isEmpty, "\(summary)\n\nPaste-ready debt (allowlist excluded):\n\(pasteReady)")
    }
}
