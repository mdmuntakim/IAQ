package iaq;

/**
 * The colour half of the remaster: tone curve, vibrance, split toning,
 * highlight glow and vignette.  Tuned against the Bleach before/after pair in
 * docs/benchmark: deeper blacks, warm golden highlights, richer mid-tone
 * colour and a soft glow around the bright sky.
 */
public final class Grade {

    private Grade() {
    }

    public static void apply(float[] r, float[] g, float[] b, int w, int h, double strength) {
        if (strength <= 0.001) return;
        final double s = strength;
        Parallel.rows(h, row -> {
            for (int x = 0; x < w; x++) {
                int i = row * w + x;
                double rr = r[i];
                double gg = g[i];
                double bb = b[i];

                // Deepen the black point, darken the mid-tones a touch, then a
                // filmic S-curve.  This is what gives the reference remaster
                // its deep shadows under a bright sky.
                rr = (rr - 0.012) / 0.988;
                gg = (gg - 0.012) / 0.988;
                bb = (bb - 0.012) / 0.988;
                rr = Math.pow(Color.clamp01(rr), 1.06) * 1.02;
                gg = Math.pow(Color.clamp01(gg), 1.06) * 1.02;
                bb = Math.pow(Color.clamp01(bb), 1.06) * 1.02;

                double contrast = 0.36 * s;
                double y0 = Color.luma601((float) rr, (float) gg, (float) bb);
                double y1 = scurve(y0, contrast);
                double k = y1 / Math.max(1e-4, y0);
                if (k < 0.5) k = 0.5;
                else if (k > 2.0) k = 2.0;
                rr *= k;
                gg *= k;
                bb *= k;

                // Vibrance protects already saturated pixels, saturation adds
                // the last bit of colour depth to flat anime fills.
                double y = Color.luma601((float) rr, (float) gg, (float) bb);
                double mx = Math.max(rr, Math.max(gg, bb));
                double mn = Math.min(rr, Math.min(gg, bb));
                double sat = mx > 1e-4 ? (mx - mn) / mx : 0;
                double boost = 1.0 + 0.02 * s + 0.14 * s * (1.0 - sat);
                rr = y + (rr - y) * boost;
                gg = y + (gg - y) * boost;
                bb = y + (bb - y) * boost;

                // Golden highlights, warm neutral mid-tones, brown shadows.
                rr *= 1.012;
                gg *= 0.994;
                bb *= 0.962;
                double lum = Color.luma601((float) rr, (float) gg, (float) bb);
                double shadow = (1.0 - lum) * (1.0 - lum);
                double high = lum * lum;
                rr += (0.008 * shadow + 0.004 * high) * s;
                gg += (0.002 * shadow + 0.010 * high) * s;
                bb += (0.003 * shadow - 0.004 * high) * s;

                // Creamy highlight rolloff: the reference lifts green and blue
                // in the bright sky far more than red, which turns a flat
                // orange into the glowing yellow of the benchmark.  The warm
                // gate keeps skin and white cloth out of the lift.
                double warmGate = Color.smoothstep(0.32, 0.60, rr - bb);
                double lowBlue = 1.0 - Color.smoothstep(0.22, 0.36, bb);
                double hi = Color.smoothstep(0.45, 0.85, lum) * warmGate * lowBlue;
                rr += (1.0 - rr) * 0.15 * hi * s;
                gg += (1.0 - gg) * 0.46 * hi * s;
                bb += (1.0 - bb) * 0.24 * hi * s;

                // Soft shoulder keeps highlight detail instead of clipping.
                rr = Color.shoulder((float) rr, 0.86f);
                gg = Color.shoulder((float) gg, 0.86f);
                bb = Color.shoulder((float) bb, 0.86f);

                r[i] = Color.clamp01(rr);
                g[i] = Color.clamp01(gg);
                b[i] = Color.clamp01(bb);
            }
        });
    }

    private static double scurve(double x, double c) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        double smooth = x * x * (3 - 2 * x);
        return x + (smooth - x) * c;
    }

    /**
     * Multi-scale highlight bloom.  The bright pass runs on a quarter
     * resolution copy, so the glow is cheap even on a 4K output.
     */
    public static void bloom(float[] r, float[] g, float[] b, int w, int h, double strength) {
        if (strength <= 0.001) return;
        final int scale = 4;
        int bw = Math.max(1, w / scale);
        int bh = Math.max(1, h / scale);
        float[] br = new float[bw * bh];
        float[] bg = new float[bw * bh];
        float[] bb = new float[bw * bh];

        Parallel.rows(bh, by -> {
            for (int bx = 0; bx < bw; bx++) {
                int x0 = bx * scale;
                int y0 = by * scale;
                int x1 = Math.min(w, x0 + scale);
                int y1 = Math.min(h, y0 + scale);
                double sr = 0;
                double sg = 0;
                double sb = 0;
                int count = 0;
                for (int y = y0; y < y1; y++) {
                    int row = y * w;
                    for (int x = x0; x < x1; x++) {
                        sr += r[row + x];
                        sg += g[row + x];
                        sb += b[row + x];
                        count++;
                    }
                }
                int o = by * bw + bx;
                br[o] = (float) (sr / count);
                bg[o] = (float) (sg / count);
                bb[o] = (float) (sb / count);
            }
        });

        final float threshold = 0.72f;
        final float knee = 0.16f;
        Parallel.rows(bh, by -> {
            for (int bx = 0; bx < bw; bx++) {
                int i = by * bw + bx;
                float lum = Color.luma601(br[i], bg[i], bb[i]);
                double t = Color.smoothstep(threshold, threshold + knee, lum);
                if (t <= 0) {
                    br[i] = bg[i] = bb[i] = 0;
                } else {
                    double amount = (lum - threshold) / (1.0 - threshold);
                    if (amount < 0) amount = 0;
                    float k = (float) (t * amount);
                    br[i] *= k;
                    bg[i] *= k;
                    bb[i] *= k;
                }
            }
        });

        float[] smallR = Filters.gaussian(br, bw, bh, 1.2);
        float[] smallG = Filters.gaussian(bg, bw, bh, 1.2);
        float[] smallB = Filters.gaussian(bb, bw, bh, 1.2);
        float[] largeR = Filters.gaussian(br, bw, bh, 4.2);
        float[] largeG = Filters.gaussian(bg, bw, bh, 4.2);
        float[] largeB = Filters.gaussian(bb, bw, bh, 4.2);

        final double amount = 0.18 * strength;
        Parallel.rows(h, y -> {
            double fy = (y + 0.5) / scale - 0.5;
            int y0 = (int) Math.floor(fy);
            double ty = fy - y0;
            int y1 = y0 + 1;
            if (y0 < 0) {
                y0 = 0;
                y1 = 0;
                ty = 0;
            } else if (y0 >= bh - 1) {
                y0 = bh - 1;
                y1 = bh - 1;
                ty = 0;
            }
            int row = y * w;
            for (int x = 0; x < w; x++) {
                double fx = (x + 0.5) / scale - 0.5;
                int x0 = (int) Math.floor(fx);
                double tx = fx - x0;
                int x1 = x0 + 1;
                if (x0 < 0) {
                    x0 = 0;
                    x1 = 0;
                    tx = 0;
                } else if (x0 >= bw - 1) {
                    x0 = bw - 1;
                    x1 = bw - 1;
                    tx = 0;
                }
                int i00 = y0 * bw + x0;
                int i10 = y0 * bw + x1;
                int i01 = y1 * bw + x0;
                int i11 = y1 * bw + x1;
                float gr = mix(smallR, largeR, i00, i10, i01, i11, tx, ty);
                float gg = mix(smallG, largeG, i00, i10, i01, i11, tx, ty);
                float gb = mix(smallB, largeB, i00, i10, i01, i11, tx, ty);
                int i = row + x;
                // Screen blend, warm tinted.
                double er = gr * 1.00 * amount;
                double eg = gg * 0.92 * amount;
                double eb = gb * 0.80 * amount;
                r[i] = Color.clamp01(1.0 - (1.0 - r[i]) * (1.0 - er));
                g[i] = Color.clamp01(1.0 - (1.0 - g[i]) * (1.0 - eg));
                b[i] = Color.clamp01(1.0 - (1.0 - b[i]) * (1.0 - eb));
            }
        });
    }

    private static float mix(float[] small, float[] large,
                             int i00, int i10, int i01, int i11,
                             double tx, double ty) {
        double s0 = small[i00] + (small[i10] - small[i00]) * tx;
        double s1 = small[i01] + (small[i11] - small[i01]) * tx;
        double l0 = large[i00] + (large[i10] - large[i00]) * tx;
        double l1 = large[i01] + (large[i11] - large[i01]) * tx;
        double s = s0 + (s1 - s0) * ty;
        double l = l0 + (l1 - l0) * ty;
        return (float) (s * 0.62 + l * 0.38);
    }

    /** Gentle optical vignette; strength is deliberately low. */
    public static void vignette(float[] r, float[] g, float[] b, int w, int h, double strength) {
        if (strength <= 0.001) return;
        final double amount = 0.16 * strength;
        Parallel.rows(h, y -> {
            double dy = (y + 0.5) / h - 0.5;
            int row = y * w;
            for (int x = 0; x < w; x++) {
                double dx = (x + 0.5) / w - 0.5;
                double d = Math.sqrt(dx * dx + dy * dy) / 0.70710678;
                double v = 1.0 - amount * Color.smoothstep(0.55, 1.05, d);
                int i = row + x;
                r[i] = Color.clamp01(r[i] * v);
                g[i] = Color.clamp01(g[i] * v);
                b[i] = Color.clamp01(b[i] * v);
            }
        });
    }
}
