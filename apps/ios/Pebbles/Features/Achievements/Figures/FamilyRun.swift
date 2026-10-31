/// Consecutive seats of one family, as the server seats them (wheel order, then
/// slug): where the wheel draws a family's arc and tick, the rosace its label
/// and the margin ladder its bar. A seat the server sent without a family runs
/// with its nil neighbours and draws in ink.
struct FamilyRun: Equatable, Identifiable {
    var id: Int { start }
    let family: String?
    let start: Int
    let length: Int

    func contains(_ seat: Int) -> Bool {
        seat >= start && seat < start + length
    }

    static func runs(_ families: [String?]) -> [FamilyRun] {
        var runs: [FamilyRun] = []
        for (index, family) in families.enumerated() {
            if let last = runs.last, last.family == family {
                runs[runs.count - 1] = FamilyRun(family: family, start: last.start, length: last.length + 1)
            } else {
                runs.append(FamilyRun(family: family, start: index, length: 1))
            }
        }
        return runs
    }
}
