// Pebbles/Resources/StoneLab/stone.metal
//
// The stone lab's two effects (#974).
//
//   stone — a colorEffect over the body silhouette. Reads the body pixel's
//           premultiplied colour and returns the lit material in the same
//           alpha. Three materials share one entry point, selected by `kind`.
//   carve — a layerEffect over the flat carving ink. Shades the ink's edges
//           relative to the light so a line reads as a groove.
//
// Everything is a pure function of the uniforms. There is no time input.
// Noise functions are carried over from the Femfolk native card POC
// (femfolk/ios/FemfolkCard/Sources/Shaders/foil.metal).

#include <metal_stdlib>
#include <SwiftUI/SwiftUI.h>
using namespace metal;

// ---- noise ---------------------------------------------------------------

static float hash21(int2 p, uint seed) {
    uint n = (uint(p.x) * 1597334677u) ^ (uint(p.y) * 3812015801u) ^ (seed * 2654435761u);
    n = (n ^ (n >> 16)) * 0x45d9f3bu;
    n = (n ^ (n >> 16)) * 0x45d9f3bu;
    n = n ^ (n >> 16);
    return float(n) / 4294967296.0;
}

static float2 grad2(int2 p, uint seed) {
    float a = hash21(p, seed) * 2.0 * M_PI_F;
    return float2(cos(a), sin(a));
}

static float pnoise(float2 p, uint seed) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    int2 c = int2(i);
    float a = dot(grad2(c, seed), f);
    float b = dot(grad2(c + int2(1, 0), seed), f - float2(1, 0));
    float d = dot(grad2(c + int2(0, 1), seed), f - float2(0, 1));
    float e = dot(grad2(c + int2(1, 1), seed), f - float2(1, 1));
    return mix(mix(a, b, u.x), mix(d, e, u.x), u.y);
}

static float fractal(float2 p, float frequency, int octaves, uint seed) {
    float sum = 0.0, amp = 1.0;
    float2 q = p * frequency;
    for (int o = 0; o < octaves; o++) {
        sum += amp * pnoise(q, seed + uint(o) * 17u);
        q *= 2.0;
        amp *= 0.5;
    }
    return clamp(sum * 0.5 + 0.5, 0.0, 1.0);
}

/// Worley cells: nearest and second-nearest feature distance, and the
/// nearest cell's hash for a per-cell random.
static float worley(float2 p, uint seed, thread float &d1, thread float &d2) {
    float2 i = floor(p);
    float2 f = fract(p);
    d1 = 8.0; d2 = 8.0;
    float id = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            int2 cell = int2(i) + int2(x, y);
            float2 feature = float2(hash21(cell, seed), hash21(cell, seed + 101u));
            float d = length(float2(x, y) + feature - f);
            if (d < d1) { d2 = d1; d1 = d; id = hash21(cell, seed + 202u); }
            else if (d < d2) { d2 = d; }
        }
    }
    return id;
}

// ---- lighting ------------------------------------------------------------

struct Lit { float diffuse; float spec; };

static Lit lit(float3 n, float3 L, float shininess) {
    float diffuse = clamp(dot(n, L), 0.0, 1.0);
    float3 H = normalize(L + float3(0.0, 0.0, 1.0));
    float spec = pow(clamp(dot(n, H), 0.0, 1.0), shininess);
    return Lit{ diffuse, spec };
}

static float3 normalFromHeight(float h, float hx, float hy, float step, float relief) {
    // Screen y points down; the light vector is in the same frame, so no flip.
    return normalize(float3(-(hx - h) / step * relief, -(hy - h) / step * relief, 1.0));
}

// ---- stone ---------------------------------------------------------------

/// `light` is a unit vector, xy in the stone plane (y down), z out of the
/// screen. Tones are straight, display-referred sRGB (the hex bytes / 255,
/// not linearised); the lighting works on them directly. `unit` is points per stone (viewBox) unit,
/// so the grain has the same size on every stone at every on-screen size.
/// `lipLightOpacity` is the lip-light tone's opacity; it scales every
/// highlight drawn in that tone (specular and glitter).
[[ stitchable ]] half4 stone(float2 position, half4 color,
                             float unit, float seedValue, float3 light,
                             float3 body, float3 ink, float3 lipLight, float3 lipShadow,
                             float lipLightOpacity,
                             float kind, float scale, float relief, float contrast, float sheen,
                             float facetDensity, float glitter, float pits, float banding) {
    if (color.a <= 0.002h) { return color; }
    float2 p = position / max(unit, 0.0001);
    uint seed = uint(seedValue);
    float3 L = normalize(light);
    float3 col = body;
    int m = int(kind);

    if (m == 0) {
        // Blackstone: matte basalt. A three-octave grain gives the micro
        // normal; a higher-frequency field thresholded by `pits` punches
        // small dark vesicles into it; the specular is broad and faint.
        float stepB = 0.5;
        float h = fractal(p, scale, 3, seed);
        float hx = fractal(p + float2(stepB, 0.0), scale, 3, seed);
        float hy = fractal(p + float2(0.0, stepB), scale, 3, seed);
        float3 n = normalFromHeight(h, hx, hy, stepB, relief);
        Lit l = lit(n, L, 4.0);
        float shade = 0.5 + 0.7 * l.diffuse;
        col = body * mix(1.0, shade, contrast);
        float pitField = fractal(p, scale * 5.0, 1, seed + 5u);
        float pitThreshold = 0.5 + (1.0 - pits) * 0.35;
        float pit = smoothstep(pitThreshold, pitThreshold + 0.04, pitField);
        col = mix(col, ink * 0.6, pit);
        col += sheen * l.spec * lipLight * lipLightOpacity * 0.5;
    } else if (m == 1) {
        // River: continuous fine grain, one axis stretched into sediment
        // banding, a soft broad specular.
        float2 q = p * float2(1.0, 1.0 + banding * 3.0);
        float step = 0.5;
        float h = fractal(q, scale, 3, seed);
        float hx = fractal(q + float2(step, 0.0), scale, 3, seed);
        float hy = fractal(q + float2(0.0, step), scale, 3, seed);
        float3 n = normalFromHeight(h, hx, hy, step, relief);
        Lit l = lit(n, L, 18.0);
        float shade = 0.6 + 0.6 * l.diffuse;
        col = body * mix(1.0, shade, contrast);
        col += sheen * l.spec * lipLight * lipLightOpacity * 0.8;
    } else {
        // Gem: flat facets, one random normal per Worley cell, blinking as
        // the light crosses; sparse glitter points on their own cell grid.
        float d1, d2;
        float id = worley(p * scale * facetDensity, seed, d1, d2);
        float a = hash21(int2(id * 4096.0, 7), seed) * 2.0 * M_PI_F;
        float t = hash21(int2(9, id * 4096.0), seed) * relief * 0.5;
        float3 n = normalize(float3(cos(a) * t, sin(a) * t, 1.0));
        Lit l = lit(n, L, 48.0);
        float shade = 0.55 + 0.75 * l.diffuse;
        col = body * mix(1.0, shade, contrast);
        float seam = smoothstep(0.0, 0.04, d2 - d1);
        col = mix(col * 0.7, col, seam);
        col += sheen * l.spec * lipLight * lipLightOpacity;
        // Glitter: one candidate point per cell of its own grid, 1.5x finer than
        // the facets at density 1. A cell keeps its point when its hash clears
        // 1 - glitter * 0.15 (glitter 1 lights 15% of cells), drawn as a
        // disc about 1 pt across at the cell's feature, whatever the stone's
        // on-screen size. A peak-of-noise threshold cannot do this: gradient
        // noise never reaches the top of its 0..1 range.
        float gd1, gd2;
        float grid = scale * 1.5;
        float gid = worley(p * grid, seed + 9u, gd1, gd2);
        float keep = step(1.0 - glitter * 0.15, gid);
        float fromPoint = gd1 / grid * unit;
        float twinkle = keep * (1.0 - smoothstep(0.4, 0.9, fromPoint)) * (0.4 + 0.6 * l.spec);
        col = mix(col, lipLight, twinkle * lipLightOpacity);
    }

    col = clamp(col, 0.0, 1.0);
    return half4(half3(col) * color.a, color.a);
}

// ---- carve ---------------------------------------------------------------

/// `offset` is the lip width times the direction toward the light, in
/// points. A groove's wall on the light side is in shadow and its far wall
/// catches the light, so: ink with no ink toward the light → shadow lip; ink
/// with no ink away from the light → light lip; the rest of the ink is `ink`.
/// `lipOpacity` is the two tones' own opacities (x light, y shadow);
/// `materialLipOpacity` is the material's overall lip strength over both.
[[ stitchable ]] half4 carve(float2 position, SwiftUI::Layer layer,
                             float2 offset, float3 ink, float3 lipLight, float3 lipShadow,
                             float2 lipOpacity, float materialLipOpacity) {
    half4 here = layer.sample(position);
    float a = float(here.a);
    if (a <= 0.002) { return here; }
    float toward = float(layer.sample(position + offset).a);
    float away = float(layer.sample(position - offset).a);
    float shadow = (1.0 - toward) * lipOpacity.y * materialLipOpacity;
    float light = (1.0 - away) * lipOpacity.x * materialLipOpacity;
    float3 col = mix(ink, lipShadow, shadow);
    col = mix(col, lipLight, light);
    return half4(half3(col) * a, a);
}
