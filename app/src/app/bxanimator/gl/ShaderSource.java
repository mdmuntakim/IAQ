package app.bxanimator.gl;

/**
 * GLSL ES 2.0 sources for the Shader Lab. Every effect id matches
 * {@link app.bxanimator.core.EffectType#glsl}, so the GLSL preview and the
 * software renderer speak about the same effect.
 */
public final class ShaderSource {

    private ShaderSource() {
    }

    public static String vertex() {
        return "attribute vec2 aPos;\n"
                + "varying vec2 vUv;\n"
                + "void main() {\n"
                + "  vUv = aPos * 0.5 + 0.5;\n"
                + "  gl_Position = vec4(aPos, 0.0, 1.0);\n"
                + "}\n";
    }

    public static String fragment(String id) {
        String body;
        if (id == null) id = "grade";
        switch (id) {
            case "cel":
                body = "vec3 effect(vec2 uv, vec3 base) {\n"
                        + "  float px = uParams.y / uRes.y;\n"
                        + "  float l = luma(sampleTex(uv));\n"
                        + "  float lx = luma(sampleTex(uv + vec2(px, 0.0)));\n"
                        + "  float ly = luma(sampleTex(uv + vec2(0.0, px)));\n"
                        + "  float edge = smoothstep(0.05, 0.2, abs(l - lx) + abs(l - ly));\n"
                        + "  vec3 ink = vec3(0.055, 0.045, 0.075);\n"
                        + "  return mix(base, ink, clamp(edge * uParams.x * 1.9, 0.0, 1.0));\n"
                        + "}\n";
                break;
            case "bloom":
                body = "vec3 effect(vec2 uv, vec3 base) {\n"
                        + "  float radius = uParams.y * 0.02;\n"
                        + "  vec3 sum = vec3(0.0);\n"
                        + "  for (int i = 0; i < 8; i++) {\n"
                        + "    float a = float(i) * 0.785398;\n"
                        + "    vec2 o = vec2(cos(a), sin(a)) * radius;\n"
                        + "    sum += max(sampleTex(uv + o) - uParams.x, 0.0);\n"
                        + "  }\n"
                        + "  return base + (sum / 8.0) * 1.7;\n"
                        + "}\n";
                break;
            case "chroma":
                body = "vec3 effect(vec2 uv, vec3 base) {\n"
                        + "  float amount = uParams.x * 0.012 * (0.35 + length(uv - 0.5));\n"
                        + "  float r = sampleTex(uv + vec2(amount, 0.0)).r;\n"
                        + "  float b = sampleTex(uv - vec2(amount, 0.0)).b;\n"
                        + "  return vec3(r, base.g, b);\n"
                        + "}\n";
                break;
            case "grain":
                body = "float hash(vec2 p) {\n"
                        + "  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);\n"
                        + "}\n"
                        + "vec3 effect(vec2 uv, vec3 base) {\n"
                        + "  float n = hash(uv * uRes + vec2(uTime * 91.7, uTime * 47.3)) - 0.5;\n"
                        + "  return base + n * uParams.x * 0.55;\n"
                        + "}\n";
                break;
            case "vignette":
                body = "vec3 effect(vec2 uv, vec3 base) {\n"
                        + "  float d = distance(uv, vec2(0.5));\n"
                        + "  float soft = mix(0.55, 1.15, uParams.x);\n"
                        + "  float v = 1.0 - smoothstep(0.18, soft, d);\n"
                        + "  return base * mix(0.28, 1.0, v);\n"
                        + "}\n";
                break;
            case "speedlines":
                body = "float hash(vec2 p) {\n"
                        + "  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);\n"
                        + "}\n"
                        + "vec3 effect(vec2 uv, vec3 base) {\n"
                        + "  vec2 p = uv - 0.5;\n"
                        + "  float ang = atan(p.y, p.x);\n"
                        + "  float r = length(p);\n"
                        + "  float bands = 55.0 + uParams.x * 150.0;\n"
                        + "  float line = step(0.70, hash(vec2(floor(ang * bands), 3.0)));\n"
                        + "  float reach = smoothstep(0.16, 0.52, r) * uParams.y;\n"
                        + "  return mix(base, vec3(1.0), line * reach * 0.9);\n"
                        + "}\n";
                break;
            case "halftone":
                body = "vec3 effect(vec2 uv, vec3 base) {\n"
                        + "  float scale = mix(90.0, 380.0, uParams.x);\n"
                        + "  vec2 p = uv * scale;\n"
                        + "  vec2 cell = fract(p) - 0.5;\n"
                        + "  float d = length(cell);\n"
                        + "  float l = luma(base);\n"
                        + "  float radius = (1.0 - l) * 0.58;\n"
                        + "  float dotMask = 1.0 - smoothstep(radius - 0.07, radius + 0.07, d);\n"
                        + "  vec3 paper = vec3(0.93, 0.91, 0.86);\n"
                        + "  return mix(base, mix(paper, base * 0.32, dotMask), 0.92);\n"
                        + "}\n";
                break;
            case "watercolor":
                body = "vec3 effect(vec2 uv, vec3 base) {\n"
                        + "  vec2 px = uParams.x * 6.0 / uRes;\n"
                        + "  vec3 c = base;\n"
                        + "  c += sampleTex(uv + vec2(px.x, 0.0));\n"
                        + "  c += sampleTex(uv - vec2(px.x, 0.0));\n"
                        + "  c += sampleTex(uv + vec2(0.0, px.y));\n"
                        + "  c += sampleTex(uv - vec2(0.0, px.y));\n"
                        + "  c /= 5.0;\n"
                        + "  return floor(c * 12.0 + 0.5) / 12.0;\n"
                        + "}\n";
                break;
            case "remaster":
                body = "vec3 effect(vec2 uv, vec3 base) {\n"
                        + "  vec2 px = 1.5 / uRes;\n"
                        + "  vec3 blur = (sampleTex(uv + vec2(px.x, 0.0)) + sampleTex(uv - vec2(px.x, 0.0))\n"
                        + "             + sampleTex(uv + vec2(0.0, px.y)) + sampleTex(uv - vec2(0.0, px.y))) * 0.25;\n"
                        + "  vec3 denoised = mix(base, blur, uParams.y * 0.7);\n"
                        + "  vec3 sharp = denoised + (denoised - blur) * (uParams.x * 3.0);\n"
                        + "  float l = luma(sharp);\n"
                        + "  vec3 colour = mix(vec3(l), sharp, 1.0 + uParams.z * 0.65);\n"
                        + "  colour = (colour - 0.5) * (1.0 + uParams.z * 0.18) + 0.5;\n"
                        + "  return clamp(colour, 0.0, 1.0);\n"
                        + "}\n";
                break;
            case "vhs":
                body = "float hash(vec2 p) {\n"
                        + "  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);\n"
                        + "}\n"
                        + "vec3 effect(vec2 uv, vec3 base) {\n"
                        + "  float tracking = uParams.x;\n"
                        + "  float wobble = sin(uv.y * 90.0 + uTime * 5.0) * 0.0025 * tracking;\n"
                        + "  vec2 p = vec2(uv.x + wobble, uv.y);\n"
                        + "  float r = sampleTex(p + vec2(0.004 * tracking, 0.0)).r;\n"
                        + "  float b = sampleTex(p - vec2(0.004 * tracking, 0.0)).b;\n"
                        + "  vec3 c = vec3(r, base.g, b);\n"
                        + "  c *= 0.9 + 0.1 * sin(uv.y * uRes.y * 1.5);\n"
                        + "  c += (hash(vec2(uv.y * 400.0, uTime * 30.0)) - 0.5) * 0.06 * tracking;\n"
                        + "  return c;\n"
                        + "}\n";
                break;
            case "sharpen":
                body = "vec3 effect(vec2 uv, vec3 base) {\n"
                        + "  vec2 px = 1.5 / uRes;\n"
                        + "  vec3 blur = (sampleTex(uv + vec2(px.x, 0.0)) + sampleTex(uv - vec2(px.x, 0.0))\n"
                        + "             + sampleTex(uv + vec2(0.0, px.y)) + sampleTex(uv - vec2(0.0, px.y))) * 0.25;\n"
                        + "  return clamp(base + (base - blur) * uParams.x * 2.5, 0.0, 1.0);\n"
                        + "}\n";
                break;
            case "grade":
            default:
                body = "vec3 effect(vec2 uv, vec3 base) {\n"
                        + "  float l = luma(base);\n"
                        + "  vec3 c = mix(vec3(l), base, uParams.x);\n"
                        + "  c = (c - 0.5) * uParams.y + 0.5 + uParams.w * 0.25;\n"
                        + "  c.r += uParams.z * 0.08;\n"
                        + "  c.b -= uParams.z * 0.08;\n"
                        + "  return clamp(c, 0.0, 1.0);\n"
                        + "}\n";
                break;
        }
        return "precision mediump float;\n"
                + "varying vec2 vUv;\n"
                + "uniform sampler2D uTex;\n"
                + "uniform vec2 uRes;\n"
                + "uniform float uTime;\n"
                + "uniform float uIntensity;\n"
                + "uniform vec4 uParams;\n"
                + "vec3 sampleTex(vec2 uv) {\n"
                + "  return texture2D(uTex, clamp(uv, vec2(0.001), vec2(0.999))).rgb;\n"
                + "}\n"
                + "float luma(vec3 c) {\n"
                + "  return dot(c, vec3(0.299, 0.587, 0.114));\n"
                + "}\n"
                + body
                + "void main() {\n"
                + "  vec3 base = sampleTex(vUv);\n"
                + "  vec3 result = effect(vUv, base);\n"
                + "  gl_FragColor = vec4(mix(base, result, clamp(uIntensity, 0.0, 1.0)), 1.0);\n"
                + "}\n";
    }
}
