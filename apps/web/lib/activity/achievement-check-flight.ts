// How many mutation-path `check_achievements()` calls are in flight (#1308).
// The Profile shelf's screen-open grant skips its own check while one runs:
// a check that unlocks returns its rows once, so a grant racing a mutation's
// check could take a fresh unlock that the mutation path should celebrate.

let inFlight = 0

export function beginAchievementCheck(): void {
  inFlight += 1
}

export function endAchievementCheck(): void {
  inFlight = Math.max(0, inFlight - 1)
}

export function achievementCheckInFlight(): boolean {
  return inFlight > 0
}
