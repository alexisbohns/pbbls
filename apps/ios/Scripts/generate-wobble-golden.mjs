// Regenerates PebblesTests/Wobble/WobbleGolden.json — reference values for the
// iOS SVGTurbulence port (issue #555, PR #556).
//
// Usage:  node apps/ios/Scripts/generate-wobble-golden.mjs apps/ios/PebblesTests/Wobble/WobbleGolden.json
//
// The noise implementation below is copied VERBATIM from the SVG Humanizer
// playground (design source of truth for issue #555), which is itself a
// faithful port of the SVG 1.1 §15.19 feTurbulence reference code.
// Do not "clean it up": the LCG stream position depends on the exact loop
// structure (all 4 channels' gradients are generated even though only R/G
// are consumed). The future Android/web ports validate against this same
// fixture, so the file must only ever be regenerated from this script.

import { writeFileSync } from "node:fs";

const B_SIZE = 0x100, BM = 0xff, PERLIN_N = 0x1000;

const RM = 2147483647, RA = 16807, RQ = 127773, RR = 2836;
const rand = (l) => { let r = RA * (l % RQ) - RR * Math.floor(l / RQ); if (r <= 0) r += RM; return r; };

function makeNoise(seed) {
  const lat = new Int32Array(B_SIZE + B_SIZE + 2);
  const grad = [[], [], [], []];
  for (let k = 0; k < 4; k++) for (let i = 0; i < B_SIZE + B_SIZE + 2; i++) grad[k][i] = [0, 0];
  let s = Math.floor(seed); if (s <= 0) s = -(s % (RM - 1)) + 1; if (s > RM - 1) s = RM - 1;
  let i, j, k;
  for (k = 0; k < 4; k++) for (i = 0; i < B_SIZE; i++) {
    lat[i] = i;
    for (j = 0; j < 2; j++) { s = rand(s); grad[k][i][j] = ((s % (B_SIZE + B_SIZE)) - B_SIZE) / B_SIZE; }
    const m = Math.hypot(grad[k][i][0], grad[k][i][1]); grad[k][i][0] /= m; grad[k][i][1] /= m;
  }
  i = B_SIZE; while (--i) { k = lat[i]; s = rand(s); j = s % B_SIZE; lat[i] = lat[j]; lat[j] = k; }
  for (i = 0; i < B_SIZE + 2; i++) { lat[B_SIZE + i] = lat[i]; for (k = 0; k < 4; k++) for (j = 0; j < 2; j++) grad[k][B_SIZE + i][j] = grad[k][i][j]; }
  const sc = (t) => t * t * (3 - 2 * t), lerp = (t, a, b) => a + t * (b - a);
  function noise2(ch, vx, vy) {
    let t = vx + PERLIN_N; const bx0 = (t | 0) & BM, bx1 = (bx0 + 1) & BM, rx0 = t - (t | 0), rx1 = rx0 - 1;
    t = vy + PERLIN_N; const by0 = (t | 0) & BM, by1 = (by0 + 1) & BM, ry0 = t - (t | 0), ry1 = ry0 - 1;
    const iv = lat[bx0], jv = lat[bx1];
    const b00 = lat[iv + by0], b10 = lat[jv + by0], b01 = lat[iv + by1], b11 = lat[jv + by1];
    const sx = sc(rx0), sy = sc(ry0); let q, u, v, a, b;
    q = grad[ch][b00]; u = rx0 * q[0] + ry0 * q[1]; q = grad[ch][b10]; v = rx1 * q[0] + ry0 * q[1]; a = lerp(sx, u, v);
    q = grad[ch][b01]; u = rx0 * q[0] + ry1 * q[1]; q = grad[ch][b11]; v = rx1 * q[0] + ry1 * q[1]; b = lerp(sx, u, v);
    return lerp(sy, a, b);
  }
  function turb(ch, x, y, f, oct) {
    let vx = x * f, vy = y * f, sum = 0, ratio = 1;
    for (let o = 0; o < oct; o++) { sum += noise2(ch, vx, vy) / ratio; vx *= 2; vy *= 2; ratio *= 2; }
    return sum;
  }
  return { turb, noise2, lat, grad };
}

const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);

// Displacement exactly as the playground bakes it (note the MINUS —
// matches feDisplacementMap inverse sampling; issue §2.3's "+" is superseded).
function makeDisp(noise, A, f, O) {
  return (x, y) => {
    const r = clamp((noise.turb(0, x, y, f, O) + 1) / 2, 0, 1) - 0.5;
    const g = clamp((noise.turb(1, x, y, f, O) + 1) / 2, 0, 1) - 0.5;
    return [x - A * r, y - A * g];
  };
}

// ── Fixture assembly ─────────────────────────────────────────

const SEED = 3;
const noise = makeNoise(SEED);

// 1. Raw LCG sequence from seed 3 (validates the seeded generator in isolation).
const lcg = [];
{ let s = SEED; for (let n = 0; n < 10; n++) { s = rand(s); lcg.push(s); } }

// 2. Internals snapshot (debug aids — pinpoint lattice/shuffle bugs fast).
const latticePrefix = Array.from(noise.lat.slice(0, 16));
const gradientSamples = [
  { channel: 0, index: 0, x: noise.grad[0][0][0], y: noise.grad[0][0][1] },
  { channel: 1, index: 0, x: noise.grad[1][0][0], y: noise.grad[1][0][1] },
  { channel: 0, index: 255, x: noise.grad[0][255][0], y: noise.grad[0][255][1] },
  { channel: 1, index: 511, x: noise.grad[1][511][0], y: noise.grad[1][511][1] },
];

// 3. Turbulence values across channels, frequencies, octave counts, coords.
const turbCases = [];
const coords = [[0, 0], [10.5, 20.25], [50, 50], [123.456, 78.9], [199, 3.75], [260, 310]];
for (const [f, oct] of [[0.024, 5], [0.024, 1], [0.05, 3]]) {
  for (const ch of [0, 1]) {
    for (const [x, y] of coords) {
      turbCases.push({ channel: ch, x, y, frequency: f, octaves: oct, value: noise.turb(ch, x, y, f, oct) });
    }
  }
}

// 4. End-to-end displaced points: canonical params, plus the §2.1-scaled
//    variant for the 260×310 canvas (s = 200/310 → A/s, f·s).
const displaced = [];
{
  const A = 18, f = 0.024, O = 5;
  const disp = makeDisp(noise, A, f, O);
  for (const [x, y] of [[10, 10], [100, 100], [150.5, 42.25], [199, 199]]) {
    const [xOut, yOut] = disp(x, y);
    displaced.push({ x, y, amplitude: A, frequency: f, octaves: O, xOut, yOut });
  }
}
{
  const s = 200 / 310, A = 18 / s, f = 0.024 * s, O = 5;
  const disp = makeDisp(noise, A, f, O);
  for (const [x, y] of [[130, 155], [42.5, 297.75]]) {
    const [xOut, yOut] = disp(x, y);
    displaced.push({ x, y, amplitude: A, frequency: f, octaves: O, xOut, yOut });
  }
}

const fixture = {
  comment: "Golden values for the SVG 1.1 §15.19 fractalNoise port (issue #555 wobble). Generated from the design playground's reference JS. Regenerate with the script in the PR description if the algorithm ever changes.",
  seed: SEED,
  lcg,
  latticePrefix,
  gradientSamples,
  turbulence: turbCases,
  displaced,
};

const out = process.argv[2] ?? "WobbleGolden.json";
writeFileSync(out, JSON.stringify(fixture, null, 2) + "\n");
console.log(`wrote ${out}`);
console.log("lcg:", lcg.join(", "));
console.log("sample turbulence [ch0 (50,50) f.024 o5]:", turbCases.find(c => c.channel === 0 && c.x === 50 && c.octaves === 5).value);
console.log("sample displaced (100,100):", JSON.stringify(displaced[1]));
