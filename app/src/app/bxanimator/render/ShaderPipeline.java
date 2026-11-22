package app.bxanimator.render;

import android.graphics.Bitmap;

import app.bxanimator.core.AnimMath;
import app.bxanimator.core.Effect;
import app.bxanimator.core.EffectType;

import java.util.List;

/**
 * The software shader chain. It runs on the same effect stack as the GLSL
 * preview and is used for the canvas preview and for every exported frame, so
 * what the editor shows is exactly what the file contains.
 */
public final class ShaderPipeline {

    private ShaderPipeline() {
    }

    public static boolean hasVisibleEffects(List<Effect> effects) {
        if (effects == null) return false;
        for (Effect e : effects) {
            if (e.enabled && e.intensity > 0.004f) return true;
        }
        return false;
    }

    public static String signature(List<Effect> effects) {
        if (effects == null) return "none";
        StringBuilder sb = new StringBuilder();
        for (Effect e : effects) {
            if (!e.enabled || e.intensity <= 0.004f) continue;
            sb.append(e.type.name()).append(':').append(Math.round(e.intensity * 100));
            for (String p : e.type.params) sb.append(',').append(Math.round(e.param(p) * 100));
            sb.append(';');
        }
        return sb.toString();
    }

    /** Applies the stack in place and returns the same bitmap. */
    public static Bitmap apply(Bitmap bitmap, List<Effect> effects, float time) {
        if (bitmap == null || !hasVisibleEffects(effects)) return bitmap;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] buf = new int[w * h];
        bitmap.getPixels(buf, 0, w, 0, 0, w, h);
        for (Effect effect : effects) {
            if (!effect.enabled || effect.intensity <= 0.004f) continue;
            try {
                applyEffect(buf, w, h, effect, time);
            } catch (Throwable ignored) {
                // One bad effect must never take the frame down.
            }
        }
        bitmap.setPixels(buf, 0, w, 0, 0, w, h);
        return bitmap;
    }

    private static void applyEffect(int[] buf, int w, int h, Effect e, float time) {
        float k = AnimMath.clamp(e.intensity, 0f, 1f);
        switch (e.type) {
            case COLOR_GRADE:
                grade(buf, w, h, k, e);
                break;
            case SHARPEN:
                sharpen(buf, w, h, k * e.param("amount"));
                break;
            case CEL_OUTLINE:
                outline(buf, w, h, k, e);
                break;
            case BLOOM:
                bloom(buf, w, h, k, e);
                break;
            case CHROMATIC:
                chromatic(buf, w, h, k * e.param("amount"));
                break;
            case GRAIN:
                grain(buf, w, h, k * e.param("amount"), time);
                break;
            case VIGNETTE:
                vignette(buf, w, h, k, e);
                break;
            case HALFTONE:
                halftone(buf, w, h, k, e);
                break;
            case WATERCOLOR:
                watercolor(buf, w, h, k, e);
                break;
            case REMASTER:
                remaster(buf, w, h, k, e);
                break;
            case VHS:
                vhs(buf, w, h, k, e, time);
                break;
            case SPEED_LINES:
                speedLines(buf, w, h, k, e, time);
                break;
            default:
                break;
        }
    }

    // -------------------------------------------------------------- effects

    private static void grade(int[] buf, int w, int h, float k, Effect e) {
        float sat = Math.max(0f, e.param("saturation"));
        float contrast = Math.max(0.2f, e.param("contrast"));
        float warmth = AnimMath.clamp(e.param("warmth"), -1f, 1f);
        float lift = AnimMath.clamp(e.param("lift"), -1f, 1f);
        for (int i = 0; i < buf.length; i++) {
            int c = buf[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            float luma = 0.299f * r + 0.587f * g + 0.114f * b;
            float nr = luma + (r - luma) * sat;
            float ng = luma + (g - luma) * sat;
            float nb = luma + (b - luma) * sat;
            nr = (nr - 128f) * contrast + 128f;
            ng = (ng - 128f) * contrast + 128f;
            nb = (nb - 128f) * contrast + 128f;
            nr += warmth * 16f;
            ng += warmth * 4f;
            nb -= warmth * 16f;
            nr += lift * (255f - nr) * 0.16f;
            ng += lift * (255f - ng) * 0.16f;
            nb += lift * (255f - nb) * 0.16f;
            buf[i] = blend(c, nr, ng, nb, k);
        }
    }

    private static void sharpen(int[] buf, int w, int h, float amount) {
        float strength = AnimMath.clamp(amount, 0f, 2f) * 1.4f;
        if (strength < 0.01f) return;
        int[] src = buf.clone();
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int i = row + x;
                int c = src[i];
                int l = src[row + Math.max(0, x - 1)];
                int r = src[row + Math.min(w - 1, x + 1)];
                int u = src[Math.max(0, y - 1) * w + x];
                int d = src[Math.min(h - 1, y + 1) * w + x];
                float rr = ((c >> 16) & 0xFF) + strength * (4 * ((c >> 16) & 0xFF)
                        - ((l >> 16) & 0xFF) - ((r >> 16) & 0xFF) - ((u >> 16) & 0xFF) - ((d >> 16) & 0xFF)) * 0.25f;
                float gg = ((c >> 8) & 0xFF) + strength * (4 * ((c >> 8) & 0xFF)
                        - ((l >> 8) & 0xFF) - ((r >> 8) & 0xFF) - ((u >> 8) & 0xFF) - ((d >> 8) & 0xFF)) * 0.25f;
                float bb = (c & 0xFF) + strength * (4 * (c & 0xFF)
                        - (l & 0xFF) - (r & 0xFF) - (u & 0xFF) - (d & 0xFF)) * 0.25f;
                buf[i] = (c & 0xFF000000) | (clamp8(rr) << 16) | (clamp8(gg) << 8) | clamp8(bb);
            }
        }
    }

    private static void outline(int[] buf, int w, int h, float k, Effect e) {
        float ink = AnimMath.clamp(e.param("ink"), 0f, 1f);
        int step = Math.max(1, Math.round(e.param("width")));
        int[] luma = new int[buf.length];
        for (int i = 0; i < buf.length; i++) {
            int c = buf[i];
            luma[i] = (int) (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF));
        }
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - step);
                int x1 = Math.min(w - 1, x + step);
                int y0 = Math.max(0, y - step);
                int y1 = Math.min(h - 1, y + step);
                int gx = luma[row + x1] - luma[row + x0];
                int gy = luma[y1 * w + x] - luma[y0 * w + x];
                float mag = (float) Math.sqrt(gx * gx + gy * gy);
                if (mag < 26f) continue;
                float strength = AnimMath.clamp(mag / 90f, 0f, 1f) * k * ink;
                if (strength < 0.01f) continue;
                int c = buf[row + x];
                int r = (int) (((c >> 16) & 0xFF) * (1f - strength) + 14 * strength);
                int g = (int) (((c >> 8) & 0xFF) * (1f - strength) + 12 * strength);
                int b = (int) ((c & 0xFF) * (1f - strength) + 20 * strength);
                buf[row + x] = (c & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
        }
    }

    private static void bloom(int[] buf, int w, int h, float k, Effect e) {
        float threshold = AnimMath.clamp(e.param("threshold"), 0.2f, 1f) * 255f;
        float radius = AnimMath.clamp(e.param("radius"), 0f, 1f);
        int scale = 4;
        int sw = Math.max(1, w / scale);
        int sh = Math.max(1, h / scale);
        int[] small = new int[sw * sh];
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                int r = 0, g = 0, b = 0, n = 0;
                for (int dy = 0; dy < scale; dy++) {
                    int sy = Math.min(h - 1, y * scale + dy);
                    for (int dx = 0; dx < scale; dx++) {
                        int sx = Math.min(w - 1, x * scale + dx);
                        int c = buf[sy * w + sx];
                        r += Math.max(0, (int) (((c >> 16) & 0xFF) - threshold));
                        g += Math.max(0, (int) (((c >> 8) & 0xFF) - threshold));
                        b += Math.max(0, (int) ((c & 0xFF) - threshold));
                        n++;
                    }
                }
                small[y * sw + x] = 0xFF000000 | ((r / n) << 16) | ((g / n) << 8) | (b / n);
            }
        }
        small = boxBlur(small, sw, sh, Math.max(1, Math.round(1 + radius * 4)));
        float gain = k * (0.6f + radius * 1.2f);
        for (int y = 0; y < h; y++) {
            int sy = Math.min(sh - 1, y / scale);
            for (int x = 0; x < w; x++) {
                int sx = Math.min(sw - 1, x / scale);
                int glow = small[sy * sw + sx];
                int c = buf[y * w + x];
                float rr = ((c >> 16) & 0xFF) + ((glow >> 16) & 0xFF) * gain;
                float gg = ((c >> 8) & 0xFF) + ((glow >> 8) & 0xFF) * gain;
                float bb = (c & 0xFF) + (glow & 0xFF) * gain;
                buf[y * w + x] = (c & 0xFF000000) | (clamp8(rr) << 16) | (clamp8(gg) << 8) | clamp8(bb);
            }
        }
    }

    private static void chromatic(int[] buf, int w, int h, float amount) {
        float shift = amount * 14f;
        if (shift < 0.05f) return;
        int[] src = buf.clone();
        float cx = w / 2f;
        float cy = h / 2f;
        float maxR = (float) Math.hypot(cx, cy);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float dx = (x - cx) / maxR;
                float dy = (y - cy) / maxR;
                int off = Math.round((float) Math.hypot(dx, dy) * shift);
                int xr = AnimMath.clamp(x + off, 0, w - 1);
                int xb = AnimMath.clamp(x - off, 0, w - 1);
                int r = (src[row + xr] >> 16) & 0xFF;
                int g = (src[row + x] >> 8) & 0xFF;
                int b = src[row + xb] & 0xFF;
                buf[row + x] = (src[row + x] & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
        }
    }

    private static void grain(int[] buf, int w, int h, float amount, float time) {
        int strength = Math.round(amount * 46f);
        if (strength < 1) return;
        int seed = (int) (time * 977f) * 7919;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int n = hash(x, y, seed) % (strength * 2 + 1) - strength;
                int c = buf[row + x];
                int r = clamp8(((c >> 16) & 0xFF) + n);
                int g = clamp8(((c >> 8) & 0xFF) + n);
                int b = clamp8((c & 0xFF) + n);
                buf[row + x] = (c & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
        }
    }

    private static void vignette(int[] buf, int w, int h, float k, Effect e) {
        float softness = AnimMath.clamp(e.param("softness"), 0f, 1f);
        float strength = k * (0.25f + softness * 0.75f);
        float cx = w / 2f;
        float cy = h / 2f;
        float maxR = (float) Math.hypot(cx, cy);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            float dy = (y - cy) / maxR;
            for (int x = 0; x < w; x++) {
                float dx = (x - cx) / maxR;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float f = 1f - AnimMath.clamp((d - 0.35f) / 0.65f, 0f, 1f) * strength;
                int c = buf[row + x];
                buf[row + x] = (c & 0xFF000000)
                        | (clamp8(((c >> 16) & 0xFF) * f) << 16)
                        | (clamp8(((c >> 8) & 0xFF) * f) << 8)
                        | clamp8((c & 0xFF) * f);
            }
        }
    }

    private static void halftone(int[] buf, int w, int h, float k, Effect e) {
        float scale = AnimMath.clamp(e.param("scale"), 0f, 1f);
        int cell = Math.max(3, Math.round(4 + (1f - scale) * 9));
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int cy = (y % cell) - cell / 2;
            for (int x = 0; x < w; x++) {
                int c = buf[row + x];
                int luma = (int) (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF));
                int cx = (x % cell) - cell / 2;
                float dist = (float) Math.hypot(cx, cy);
                float radius = cell * 0.62f * (1f - luma / 255f);
                float ink = dist <= radius ? 1f : 0f;
                float amount = k * 0.85f;
                int r = (int) (((c >> 16) & 0xFF) * (1f - amount * ink) + 18 * amount * ink);
                int g = (int) (((c >> 8) & 0xFF) * (1f - amount * ink) + 16 * amount * ink);
                int b = (int) ((c & 0xFF) * (1f - amount * ink) + 26 * amount * ink);
                buf[row + x] = (c & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
        }
    }

    private static void watercolor(int[] buf, int w, int h, float k, Effect e) {
        float bleed = AnimMath.clamp(e.param("bleed"), 0f, 1f);
        int[] blurred = boxBlur(buf, w, h, Math.max(1, Math.round(1 + bleed * 3)));
        int[] luma = new int[buf.length];
        for (int i = 0; i < buf.length; i++) {
            int c = buf[i];
            luma[i] = (int) (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF));
        }
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int i = row + x;
                int c = buf[i];
                int bl = blurred[i];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                float mix = bleed * 0.8f * k;
                r = (int) (r + (((bl >> 16) & 0xFF) - r) * mix);
                g = (int) (g + (((bl >> 8) & 0xFF) - g) * mix);
                b = (int) (b + ((bl & 0xFF) - b) * mix);
                float l = 0.299f * r + 0.587f * g + 0.114f * b;
                r = (int) (l + (r - l) * (1f + 0.1f * k));
                g = (int) (l + (g - l) * (1f + 0.1f * k));
                b = (int) (l + (b - l) * (1f + 0.1f * k));
                int gx = luma[row + Math.min(w - 1, x + 1)] - luma[row + Math.max(0, x - 1)];
                int gy = luma[Math.min(h - 1, y + 1) * w + x] - luma[Math.max(0, y - 1) * w + x];
                float edge = Math.min(1f, (float) Math.hypot(gx, gy) / 90f) * k * 0.3f;
                r = (int) (r * (1f - edge) + 30 * edge);
                g = (int) (g * (1f - edge) + 26 * edge);
                b = (int) (b * (1f - edge) + 40 * edge);
                buf[i] = (c & 0xFF000000) | (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
            }
        }
    }

    private static void remaster(int[] buf, int w, int h, float k, Effect e) {
        float sharpen = AnimMath.clamp(e.param("sharpen"), 0f, 1f);
        float denoise = AnimMath.clamp(e.param("denoise"), 0f, 1f);
        float color = AnimMath.clamp(e.param("color"), 0f, 1f);
        int[] soft = boxBlur(buf, w, h, 2);
        int[] light = boxBlur(buf, w, h, 1);
        for (int i = 0; i < buf.length; i++) {
            int c = buf[i];
            int s = soft[i];
            int l = light[i];
            float r = ((c >> 16) & 0xFF) * (1f - denoise * 0.55f) + ((s >> 16) & 0xFF) * denoise * 0.55f;
            float g = ((c >> 8) & 0xFF) * (1f - denoise * 0.55f) + ((s >> 8) & 0xFF) * denoise * 0.55f;
            float b = (c & 0xFF) * (1f - denoise * 0.55f) + (s & 0xFF) * denoise * 0.55f;
            float amount = 0.4f + sharpen * 1.1f;
            r += (((c >> 16) & 0xFF) - ((l >> 16) & 0xFF)) * amount;
            g += (((c >> 8) & 0xFF) - ((l >> 8) & 0xFF)) * amount;
            b += ((c & 0xFF) - (l & 0xFF)) * amount;
            float luma = 0.299f * r + 0.587f * g + 0.114f * b;
            float sat = 1f + color * 0.35f;
            r = luma + (r - luma) * sat;
            g = luma + (g - luma) * sat;
            b = luma + (b - luma) * sat;
            r = (r - 128f) * (1f + color * 0.06f) + 128f;
            g = (g - 128f) * (1f + color * 0.06f) + 128f;
            b = (b - 128f) * (1f + color * 0.06f) + 128f;
            buf[i] = blend(c, r, g, b, k);
        }
    }

    private static void vhs(int[] buf, int w, int h, float k, Effect e, float time) {
        float tracking = AnimMath.clamp(e.param("tracking"), 0f, 1f);
        int[] src = buf.clone();
        int t = (int) (time * 60);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            float wave = (float) Math.sin(y * 0.055f + time * 7f) * tracking * 6f;
            int lineNoise = hash(0, y, t) % 5 == 0 ? (hash(y, t, 7) % 9) - 4 : 0;
            int shift = Math.round(wave) + lineNoise;
            float scan = 0.94f + 0.06f * (float) Math.sin(y * 1.7f);
            for (int x = 0; x < w; x++) {
                int xr = AnimMath.clamp(x + shift + 1, 0, w - 1);
                int xb = AnimMath.clamp(x + shift - 1, 0, w - 1);
                int r = (src[row + xr] >> 16) & 0xFF;
                int g = (src[row + x] >> 8) & 0xFF;
                int b = src[row + xb] & 0xFF;
                r = clamp8(r * scan);
                g = clamp8(g * scan);
                b = clamp8(b * scan);
                int n = (hash(x, y, t) % 22) - 11;
                int c = buf[row + x];
                buf[row + x] = blend(c, r + n, g + n, b + n, k);
            }
        }
    }

    private static void speedLines(int[] buf, int w, int h, float k, Effect e, float time) {
        float density = AnimMath.clamp(e.param("density"), 0f, 1f);
        float length = AnimMath.clamp(e.param("length"), 0f, 1f);
        int count = Math.round(18 + density * 70);
        float cx = w / 2f;
        float cy = h / 2f;
        float maxR = (float) Math.hypot(cx, cy);
        int frame = (int) (time * 12f);
        int alpha = Math.round(70 * k);
        if (alpha <= 2) return;
        for (int i = 0; i < count; i++) {
            int seed = i * 131 + frame * 17;
            float angle = (hash(i, frame, 11) % 6283) / 1000f;
            float inner = maxR * (0.16f + (hash(i, frame, 23) % 240) / 1000f);
            float outer = inner + maxR * (0.3f + length * 0.65f) * (0.6f + (hash(i, frame, 31) % 400) / 1000f);
            float x0 = cx + (float) Math.cos(angle) * inner;
            float y0 = cy + (float) Math.sin(angle) * inner;
            float x1 = cx + (float) Math.cos(angle) * outer;
            float y1 = cy + (float) Math.sin(angle) * outer;
            int width = 1 + (seed % 3);
            drawLine(buf, w, h, x0, y0, x1, y1, alpha, width);
        }
    }

    // -------------------------------------------------------------- helpers

    private static void drawLine(int[] buf, int w, int h, float x0, float y0, float x1, float y1,
                                 int alpha, int thickness) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        int steps = (int) Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(x0 + dx * t);
            int y = Math.round(y0 + dy * t);
            for (int oy = 0; oy < thickness; oy++) {
                for (int ox = 0; ox < thickness; ox++) {
                    int px = x + ox;
                    int py = y + oy;
                    if (px < 0 || px >= w || py < 0 || py >= h) continue;
                    int idx = py * w + px;
                    int c = buf[idx];
                    int r = (int) (((c >> 16) & 0xFF) * (1f - alpha / 255f) + 255 * (alpha / 255f));
                    int g = (int) (((c >> 8) & 0xFF) * (1f - alpha / 255f) + 255 * (alpha / 255f));
                    int b = (int) ((c & 0xFF) * (1f - alpha / 255f) + 255 * (alpha / 255f));
                    buf[idx] = (c & 0xFF000000) | (r << 16) | (g << 8) | b;
                }
            }
        }
    }

    private static int[] boxBlur(int[] src, int w, int h, int radius) {
        if (radius <= 0) return src.clone();
        int[] tmp = new int[src.length];
        int[] out = new int[src.length];
        int win = radius * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -radius; i <= radius; i++) {
                int c = src[row + AnimMath.clamp(i, 0, w - 1)];
                sa += (c >>> 24);
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                tmp[row + x] = ((sa / win) << 24) | ((sr / win) << 16) | ((sg / win) << 8) | (sb / win);
                int outX = AnimMath.clamp(x - radius, 0, w - 1);
                int inX = AnimMath.clamp(x + radius + 1, 0, w - 1);
                int co = src[row + outX];
                int ci = src[row + inX];
                sa += (ci >>> 24) - (co >>> 24);
                sr += ((ci >> 16) & 0xFF) - ((co >> 16) & 0xFF);
                sg += ((ci >> 8) & 0xFF) - ((co >> 8) & 0xFF);
                sb += (ci & 0xFF) - (co & 0xFF);
            }
        }
        for (int x = 0; x < w; x++) {
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -radius; i <= radius; i++) {
                int c = tmp[AnimMath.clamp(i, 0, h - 1) * w + x];
                sa += (c >>> 24);
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = ((sa / win) << 24) | ((sr / win) << 16) | ((sg / win) << 8) | (sb / win);
                int outY = AnimMath.clamp(y - radius, 0, h - 1);
                int inY = AnimMath.clamp(y + radius + 1, 0, h - 1);
                int co = tmp[outY * w + x];
                int ci = tmp[inY * w + x];
                sa += (ci >>> 24) - (co >>> 24);
                sr += ((ci >> 16) & 0xFF) - ((co >> 16) & 0xFF);
                sg += ((ci >> 8) & 0xFF) - ((co >> 8) & 0xFF);
                sb += (ci & 0xFF) - (co & 0xFF);
            }
        }
        return out;
    }

    private static int blend(int orig, float r, float g, float b, float k) {
        int or = (orig >> 16) & 0xFF;
        int og = (orig >> 8) & 0xFF;
        int ob = orig & 0xFF;
        int nr = clamp8(or + (r - or) * k);
        int ng = clamp8(og + (g - og) * k);
        int nb = clamp8(ob + (b - ob) * k);
        return (orig & 0xFF000000) | (nr << 16) | (ng << 8) | nb;
    }

    private static int clamp8(float v) {
        return v <= 0 ? 0 : (v >= 255 ? 255 : (int) v);
    }

    private static int hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >> 13)) * 1103515245;
        return (h ^ (h >> 16)) & 0x7FFFFFFF;
    }
}
