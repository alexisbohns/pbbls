// Run: deno test scripts/oracles/achievements/year.test.js
import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { yearPayload, yearsServed } from "./year.js";

const days = (month, list, pebble = true, family = "joy", intensity = 2) =>
  list.map((d) => ({ date: `2025-${String(month).padStart(2, "0")}-${String(d).padStart(2, "0")}`, pebble, family, intensity }));

Deno.test("a past month with 14 pebble days and every full week filled is on the rim", () => {
  // June 2025: full weeks start on Mondays 2, 9, 16 and 23 (the 30th's week runs into July).
  const items = days(6, [2, 3, 4, 9, 10, 11, 16, 17, 18, 23, 24, 25, 26, 27]);
  const y = yearPayload(2025, items, "2026-01-10", "2025-06-02");
  const june = y.months[5];
  assertEquals([june.state, june.level, june.moment_days, june.pebble_days, june.full_weeks, june.weeks_filled, june.progress, june.family],
    ["kept", 2, 14, 14, 4, 4, 0, "joy"]);
});

Deno.test("14 days of moments with one empty full week stays at level 0", () => {
  const items = days(6, [2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15], false);
  const june = yearPayload(2025, items, "2026-01-10", "2025-06-02").months[5];
  // weeks of 16 and 23 are empty: 2 of 4 filled.
  assertEquals([june.state, june.level, june.weeks_filled, june.progress], ["quiet", 0, 2, 0.5]);
});

Deno.test("level 1 then progress toward the rim is the smaller share", () => {
  const moments = days(6, [2, 3, 4, 9, 10, 11, 16, 17, 18, 23, 24, 25, 26, 27], false);
  const pebbles = days(6, [2, 9, 16], true, "peace", 1);
  const june = yearPayload(2025, [...moments, ...pebbles], "2026-01-10", "2025-06-02").months[5];
  // 3 pebble days of 14 = 0.214; 3 of 4 full weeks = 0.75 → 0.214
  assertEquals([june.level, june.pebble_days, june.weeks_filled, june.progress, june.family], [1, 3, 3, 0.214, "peace"]);
});

Deno.test("family ties break on intensity, then wheel order", () => {
  const items = [...days(6, [2], true, "sadness", 3), ...days(6, [3], true, "joy", 1)];
  assertEquals(yearPayload(2025, items, "2026-01-10", "2025-06-02").months[5].family, "sadness");
  const even = [...days(6, [2], true, "sadness", 2), ...days(6, [3], true, "joy", 2)];
  assertEquals(yearPayload(2025, even, "2026-01-10", "2025-06-02").months[5].family, "joy");
});

Deno.test("the current year: upcoming months, the listed months and the next step", () => {
  const items = [{ date: "2026-03-02", pebble: true, family: "joy", intensity: 2 }, { date: "2026-11-02", pebble: true, family: "joy", intensity: 2 }];
  const y = yearPayload(2026, items, "2026-11-04", "2026-03-02");
  assertEquals(y.current, true);
  assertEquals(y.months[11].state, "upcoming");
  assertEquals(y.months[0].state, "quiet");
  assertEquals(y.listed, [11, 10, 9, 8, 7, 6, 5, 4, 3]);
  assertEquals(y.next_step, { month: 11, needs: "moments", days_left: 13, reachable: true });
});

Deno.test("done flags say which counters reached their target", () => {
  const moments = days(6, [2, 3, 4, 9, 10, 11, 16, 17, 18, 23, 24, 25, 26, 27], false);
  const june = yearPayload(2025, moments, "2026-01-10", "2025-06-02").months[5];
  // Level 1: moments done; pebbles none; weeks_filled now counts pebbles (0 of 4), so weeks not done.
  assertEquals(june.done, { moments: true, pebbles: false, weeks: false });
  assertEquals(yearPayload(2025, moments, "2026-01-10", "2025-06-02").days_target, 14);
});

Deno.test("a past year before the first moment lists nothing", () => {
  assertEquals(yearPayload(2024, [], "2026-11-04", "2026-03-02").listed, []);
});

Deno.test("a full week counts only once it has ended, from the Monday after its Sunday", () => {
  // November 2026: the first full week runs Monday 2 to Sunday 8.
  const items = [2, 3, 4, 5, 6, 7, 8].map((d) => ({ date: `2026-11-0${d}`, pebble: false }));
  const onSunday = yearPayload(2026, items, "2026-11-08", "2026-11-02").months[10];
  assertEquals([onSunday.full_weeks, onSunday.weeks_filled], [0, 0]);
  const onMonday = yearPayload(2026, items, "2026-11-09", "2026-11-02").months[10];
  assertEquals([onMonday.full_weeks, onMonday.weeks_filled], [1, 1]);
});

Deno.test("reachable is false once the month has fewer days left than the step needs", () => {
  const nov = (list) => list.map((d) => ({ date: `2026-11-${String(d).padStart(2, "0")}`, pebble: false }));
  // Every ended full week holds a moment, but 11 days are needed and 6 remain (25 to 30).
  const late = yearPayload(2026, nov([3, 10, 17]), "2026-11-25", "2026-11-03");
  assertEquals(late.next_step, { month: 11, needs: "moments", days_left: 11, reachable: false });
  // Early in the month the same step is reachable.
  assertEquals(yearPayload(2026, nov([3, 10]), "2026-11-12", "2026-11-03").next_step,
    { month: 11, needs: "moments", days_left: 12, reachable: true });
  // Today already counted: 3 needed, 5 days can still be added (26 to 30).
  const counted = nov([3, 10, 17, 18, 19, 20, 21, 22, 23, 24, 25]);
  assertEquals(yearPayload(2026, counted, "2026-11-25", "2026-11-03").next_step,
    { month: 11, needs: "moments", days_left: 3, reachable: true });
  // …and 6 needed with 5 addable is out of reach, though 6 days remain counting today.
  const short = nov([3, 10, 17, 18, 19, 20, 21, 25]);
  assertEquals(yearPayload(2026, short, "2026-11-25", "2026-11-03").next_step,
    { month: 11, needs: "moments", days_left: 6, reachable: false });
});

Deno.test("a family off the wheel loses every tie to a family on it", () => {
  const items = [...days(6, [2], true, "awe", 2), ...days(6, [3], true, "peace", 2)];
  assertEquals(yearPayload(2025, items, "2026-01-10", "2025-06-02").months[5].family, "peace");
  const offs = [...days(6, [2], true, "zest", 2), ...days(6, [3], true, "awe", 2)];
  assertEquals(yearPayload(2025, offs, "2026-01-10", "2025-06-02").months[5].family, "awe");
});

Deno.test("the discs served: the current year always, past years with a moment, never a future one", () => {
  const at = (date) => ({ date, pebble: false });
  assertEquals(yearsServed([at("2024-05-01"), at("2026-02-01"), at("2027-01-03"), at("2024-06-01")], "2026-11-04"), [2026, 2024]);
  assertEquals(yearsServed([], "2026-11-04"), [2026]);
});
