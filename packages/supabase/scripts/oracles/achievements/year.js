// The year disc's reference (#1305, spec D12–D13), ported from the approved
// board's monthStats/monthLevels. The harness compares achievements_year
// against it month by month. Pure: dates are YYYY-MM-DD strings already local
// to the reader's zone; nothing here reads a clock.
export const WHEEL = ["joy", "pride", "anger", "fear", "shame", "sadness", "peace"];
export const DAYS_TARGET = 14;

const iso = (y, m, d) => new Date(Date.UTC(y, m - 1, d)).toISOString().slice(0, 10);
const daysIn = (y, m) => new Date(Date.UTC(y, m, 0)).getUTCDate();
const round3 = (x) => Math.round(x * 1000) / 1000;

/** items: [{date, pebble, family, intensity}], one per moment. */
export function monthPayload(year, month, items, today) {
  const start = iso(year, month, 1);
  if (start > today) {
    return { month, state: "upcoming", moment_days: 0, pebble_days: 0, full_weeks: 0, weeks_filled: 0, level: 0, progress: 0,
      done: { moments: false, pebbles: false, weeks: false } };
  }
  const n = daysIn(year, month);
  const inMonth = items.filter((i) => i.date.slice(0, 7) === start.slice(0, 7) && i.date <= today);
  const mDays = new Set(inMonth.map((i) => Number(i.date.slice(8))));
  const pDays = new Set(inMonth.filter((i) => i.pebble).map((i) => Number(i.date.slice(8))));
  const weeks = [];
  for (let d = 1; d + 6 <= n; d++) {
    if (new Date(Date.UTC(year, month - 1, d)).getUTCDay() === 1 && iso(year, month, d + 6) <= today) weeks.push(d);
  }
  const covered = (set) => weeks.filter((w) => [0, 1, 2, 3, 4, 5, 6].some((k) => set.has(w + k))).length;
  const wm = covered(mDays), wp = covered(pDays), fw = weeks.length;
  const level = pDays.size >= 14 && wp === fw ? 2 : mDays.size >= 14 && wm === fw ? 1 : 0;
  const share = (days, filled) => Math.min(days / 14, fw > 0 ? filled / fw : 1);
  const progress = level === 0 ? round3(share(mDays.size, wm)) : level === 1 ? round3(share(pDays.size, wp)) : 0;
  const filled = level === 0 ? wm : wp;
  const out = {
    month, state: pDays.size === 0 ? "quiet" : "kept",
    moment_days: mDays.size, pebble_days: pDays.size, full_weeks: fw,
    weeks_filled: filled, level, progress,
    done: { moments: mDays.size >= DAYS_TARGET, pebbles: pDays.size >= DAYS_TARGET, weeks: fw > 0 && filled === fw },
  };
  const tally = new Map();
  for (const i of inMonth.filter((x) => x.pebble && x.family)) {
    const t = tally.get(i.family) ?? { n: 0, s: 0 };
    tally.set(i.family, { n: t.n + 1, s: t.s + i.intensity });
  }
  const best = [...tally.entries()].sort((a, b) => b[1].n - a[1].n || b[1].s - a[1].s || WHEEL.indexOf(a[0]) - WHEEL.indexOf(b[0]))[0];
  if (best) out.family = best[0];
  return out;
}

/** firstMoment: the user's first moment ever (local YYYY-MM-DD) or null. */
export function yearPayload(year, items, today, firstMoment) {
  const months = Array.from({ length: 12 }, (_, k) => monthPayload(year, k + 1, items, today));
  const current = Number(today.slice(0, 4)) === year;
  const listed = [];
  if (firstMoment) {
    for (let m = 12; m >= 1; m--) {
      const start = iso(year, m, 1);
      if (start <= today && start >= firstMoment.slice(0, 7) + "-01") listed.push(m);
    }
  }
  const out = { year, current, days_target: DAYS_TARGET, months, listed };
  if (current) {
    const cur = months[Number(today.slice(5, 7)) - 1];
    if (cur.level < 2) {
      const days = cur.level === 0 ? cur.moment_days : cur.pebble_days;
      out.next_step = { month: cur.month, needs: cur.level === 0 ? "moments" : "pebbles",
        days_left: Math.max(0, 14 - days), reachable: cur.weeks_filled === cur.full_weeks };
    }
  }
  return out;
}
