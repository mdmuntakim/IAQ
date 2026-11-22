#version 300 es
/*
 * IAQ remaster shader - real-time preview approximation (GLSL ES 3.0)
 * -------------------------------------------------------------------------
 * The reference implementation is IAQ.jar.  This shader reproduces the same
 * look in a fragment shader for live preview / playback, using a single pass
 * instead of the multi-pass CPU pipeline.  It is intentionally a close
 * approximation: the JAR has Lanczos-3 upscaling, a joint bilateral chroma
 * denoise and tiled CLAHE, which need more than one pass to match exactly.
 *
 * Uniforms:
 *   uTex       input frame
 *   uRes       output resolution in pixels
 *   uStrength  remaster amount, 0..1 (default 1)
 *   uGlow      bloom amount, 0..1 (default 1)
 *   uTime      seconds, only used for animated grain
 */
precision highp float;

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uTex;
uniform vec2 uRes;
uniform float uStrength;
uniform float uGlow;
uniform float uTime;

const vec3 LUMA = vec3(0.299, 0.587, 0.114);

vec3 sampleTex(vec2 uv) {
    return texture(uTex, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

float luma(vec3 c) {
    return dot(c, LUMA);
}

float scurve(float x, float c) {
    x = clamp(x, 0.0, 1.0);
    float smoothStep = x * x * (3.0 - 2.0 * x);
    return x + (smoothStep - x) * c;
}

float shoulder(float x, float knee) {
    if (x <= knee) return x;
    float room = 1.0 - knee;
    float over = (x - knee) / room;
    return knee + room * (1.0 - exp(-over));
}

vec3 shoulder3(vec3 c, float knee) {
    return vec3(shoulder(c.r, knee), shoulder(c.g, knee), shoulder(c.b, knee));
}

/* One-direction Gaussian, five taps. */
vec3 blurDir(vec2 uv, vec2 dir, float radius) {
    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    for (int i = -4; i <= 4; i++) {
        float fi = float(i);
        float w = exp(-fi * fi / 8.0);
        sum += sampleTex(uv + dir * fi * radius) * w;
        wsum += w;
    }
    return sum / wsum;
}

/* Highlight bloom: a ring of twelve taps, thresholded and warm tinted. */
vec3 bloom(vec2 uv) {
    vec2 texel = 1.0 / uRes;
    float radius = 9.0;
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 0.5235988; // 30 degrees
        vec2 o = vec2(cos(a), sin(a)) * texel * radius;
        vec3 s = sampleTex(uv + o);
        float l = luma(s);
        float t = smoothstep(0.72, 0.88, l);
        float amount = max(0.0, (l - 0.72) / 0.28);
        sum += s * t * amount;
    }
    return sum / 12.0;
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    vec2 texel = 1.0 / uRes;
    vec3 base = sampleTex(vUv);

    /* --- detail: small and medium unsharp, clamped against the 3x3 range --- */
    vec3 b1 = (blurDir(vUv, vec2(texel.x, 0.0), 1.0) + blurDir(vUv, vec2(0.0, texel.y), 1.0)) * 0.5;
    vec3 b2 = (blurDir(vUv, vec2(texel.x, 0.0), 3.0) + blurDir(vUv, vec2(0.0, texel.y), 3.0)) * 0.5;
    float yBase = luma(base);
    float detail = (yBase - luma(b1)) * 0.62 + (yBase - luma(b2)) * 0.38;
    float yDetail = yBase + detail;

    float lo = yBase;
    float hi = yBase;
    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            float v = luma(sampleTex(vUv + vec2(float(dx), float(dy)) * texel));
            lo = min(lo, v);
            hi = max(hi, v);
        }
    }
    yDetail = clamp(yDetail, lo - 0.012, hi + 0.012);

    /* Local contrast: a light large-radius lift, the preview stand-in for CLAHE. */
    yDetail += (yBase - luma(b2)) * 0.18;
    yDetail = max(0.0, yDetail);

    vec3 c = base * (yDetail / max(yBase, 1e-4));
    c = clamp(c, 0.0, 1.0);

    /* --- tone and colour --- */
    c = pow(max(c - 0.012, 0.0) / 0.988, vec3(1.06)) * 1.02;
    float y0 = luma(c);
    float y1 = scurve(y0, 0.36);
    c *= y1 / max(y0, 1e-4);

    float mx = max(c.r, max(c.g, c.b));
    float mn = min(c.r, min(c.g, c.b));
    float sat = (mx - mn) / max(mx, 1e-4);
    float boost = 1.0 + 0.02 + 0.14 * (1.0 - sat);
    c = vec3(luma(c)) + (c - vec3(luma(c))) * boost;

    c *= vec3(1.012, 0.994, 0.962);

    float lum = luma(c);
    float shadow = (1.0 - lum) * (1.0 - lum);
    float high = lum * lum;
    c += vec3(0.008 * shadow + 0.004 * high,
              0.002 * shadow + 0.010 * high,
              0.003 * shadow - 0.004 * high);

    float warmGate = smoothstep(0.32, 0.60, c.r - c.b);
    float lowBlue = 1.0 - smoothstep(0.22, 0.36, c.b);
    float hiLift = smoothstep(0.45, 0.85, lum) * warmGate * lowBlue;
    c += (1.0 - c) * vec3(0.15, 0.46, 0.24) * hiLift;

    c = shoulder3(c, 0.86);

    /* --- glow and vignette --- */
    vec3 glow = bloom(vUv) * vec3(1.0, 0.96, 0.72);
    c = 1.0 - (1.0 - c) * (1.0 - glow * 0.22 * uGlow);

    vec2 p = vUv - 0.5;
    float d = length(p) / 0.70710678;
    c *= 1.0 - 0.16 * smoothstep(0.55, 1.05, d);

    c = clamp(c, 0.0, 1.0);

    /* --- dither, so 8-bit output does not band in the sky --- */
    float n = hash(vUv * uRes + vec2(uTime * 91.7, uTime * 47.3)) - 0.5;
    c += n / 255.0;

    fragColor = vec4(mix(base, c, clamp(uStrength, 0.0, 1.0)), 1.0);
}
