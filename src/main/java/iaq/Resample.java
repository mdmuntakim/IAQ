package iaq;

/**
 * Lanczos-3 resampling with an anti-ringing clamp.
 *
 * <p>Plain Lanczos is sharp but overshoots around ink lines, which shows up as
 * a bright halo on anime art.  The clamp compares every output pixel with the
 * 3x3 source neighbourhood it came from and pulls the overshoot back, keeping
 * the crispness while removing the halo.</p>
 *
 * <p>Images with alpha are filtered premultiplied, so transparent cut-outs do
 * not pick up dark or coloured fringes at their edges.</p>
 */
public final class Resample {

    private static final double A = 3.0;

    private Resample() {
    }

    public static ImageData lanczos(ImageData src, int dw, int dh, double antiRing) {
        int sw = src.width;
        int sh = src.height;
        if (dw == sw && dh == sh) return src;

        Taps xt = taps(sw, dw);
        Taps yt = taps(sh, dh);
        float[] alpha = src.a;

        float[] hr = new float[dw * sh];
        float[] hg = new float[dw * sh];
        float[] hb = new float[dw * sh];
        float[] ha = alpha != null ? new float[dw * sh] : null;

        Parallel.rows(sh, y -> {
            int srcRow = y * sw;
            int dstRow = y * dw;
            for (int x = 0; x < dw; x++) {
                int start = xt.start[x];
                float[] wgt = xt.weights[x];
                float rr = 0, gg = 0, bb = 0, aa = 0;
                for (int k = 0; k < wgt.length; k++) {
                    int sx = clamp(start + k, 0, sw - 1);
                    int i = srcRow + sx;
                    float wk = wgt[k];
                    float av = alpha != null ? alpha[i] : 1f;
                    rr += src.r[i] * av * wk;
                    gg += src.g[i] * av * wk;
                    bb += src.b[i] * av * wk;
                    if (ha != null) aa += av * wk;
                }
                int o = dstRow + x;
                hr[o] = rr;
                hg[o] = gg;
                hb[o] = bb;
                if (ha != null) ha[o] = aa;
            }
        });

        ImageData out = new ImageData(dw, dh, alpha != null);
        Parallel.rows(dh, y -> {
            int start = yt.start[y];
            float[] wgt = yt.weights[y];
            int dstRow = y * dw;
            for (int x = 0; x < dw; x++) {
                float rr = 0, gg = 0, bb = 0, aa = 0;
                for (int k = 0; k < wgt.length; k++) {
                    int sy = clamp(start + k, 0, sh - 1);
                    int i = sy * dw + x;
                    float wk = wgt[k];
                    rr += hr[i] * wk;
                    gg += hg[i] * wk;
                    bb += hb[i] * wk;
                    if (ha != null) aa += ha[i] * wk;
                }
                int o = dstRow + x;
                out.r[o] = rr;
                out.g[o] = gg;
                out.b[o] = bb;
                if (ha != null) out.a[o] = aa;
            }
        });

        if (antiRing > 0.001) antiRing(src, out, xt, yt, (float) antiRing);

        if (alpha != null) {
            Parallel.rows(dh, y -> {
                int row = y * dw;
                for (int x = 0; x < dw; x++) {
                    int i = row + x;
                    float a = out.a[i];
                    if (a <= 1e-5f) {
                        out.r[i] = 0;
                        out.g[i] = 0;
                        out.b[i] = 0;
                    } else {
                        float inv = 1f / Math.min(1f, a);
                        out.r[i] *= inv;
                        out.g[i] *= inv;
                        out.b[i] *= inv;
                    }
                    out.a[i] = Math.max(0f, Math.min(1f, a));
                }
            });
        }
        return out;
    }

    private static void antiRing(ImageData src, ImageData out, Taps xt, Taps yt, float amount) {
        int sw = src.width;
        int sh = src.height;
        int dw = out.width;
        int dh = out.height;
        int[] cols = new int[dw * 3];
        int[] rows = new int[dh * 3];
        for (int x = 0; x < dw; x++) {
            int base = clamp((int) Math.round(xt.center[x]), 0, sw - 1);
            cols[x * 3] = clamp(base - 1, 0, sw - 1);
            cols[x * 3 + 1] = base;
            cols[x * 3 + 2] = clamp(base + 1, 0, sw - 1);
        }
        for (int y = 0; y < dh; y++) {
            int base = clamp((int) Math.round(yt.center[y]), 0, sh - 1);
            rows[y * 3] = clamp(base - 1, 0, sh - 1);
            rows[y * 3 + 1] = base;
            rows[y * 3 + 2] = clamp(base + 1, 0, sh - 1);
        }

        float[] alpha = src.a;
        Parallel.rows(dh, y -> {
            int r0 = rows[y * 3] * sw;
            int r1 = rows[y * 3 + 1] * sw;
            int r2 = rows[y * 3 + 2] * sw;
            int dst = y * dw;
            int[] idx = new int[9];
            for (int x = 0; x < dw; x++) {
                int c0 = cols[x * 3];
                int c1 = cols[x * 3 + 1];
                int c2 = cols[x * 3 + 2];
                idx[0] = r0 + c0;
                idx[1] = r0 + c1;
                idx[2] = r0 + c2;
                idx[3] = r1 + c0;
                idx[4] = r1 + c1;
                idx[5] = r1 + c2;
                idx[6] = r2 + c0;
                idx[7] = r2 + c1;
                idx[8] = r2 + c2;
                int i = dst + x;
                out.r[i] = clampChannel(out.r[i], src.r, alpha, idx, amount);
                out.g[i] = clampChannel(out.g[i], src.g, alpha, idx, amount);
                out.b[i] = clampChannel(out.b[i], src.b, alpha, idx, amount);
                if (out.a != null) out.a[i] = Math.max(0f, Math.min(1f, out.a[i]));
            }
        });
    }

    private static float clampChannel(float v, float[] plane, float[] alpha, int[] idx, float amount) {
        float lo = Float.MAX_VALUE;
        float hi = -Float.MAX_VALUE;
        for (int k = 0; k < 9; k++) {
            int i = idx[k];
            float s = alpha != null ? plane[i] * alpha[i] : plane[i];
            if (s < lo) lo = s;
            if (s > hi) hi = s;
        }
        float c = v < lo ? lo : (v > hi ? hi : v);
        return v + (c - v) * amount;
    }

    private static Taps taps(int src, int dst) {
        double ratio = (double) dst / src;
        double scale = ratio < 1.0 ? 1.0 / ratio : 1.0;
        double support = A * scale;
        int radius = (int) Math.ceil(support);
        int[] start = new int[dst];
        float[][] weights = new float[dst][];
        double[] center = new double[dst];
        for (int i = 0; i < dst; i++) {
            double c = (i + 0.5) / ratio - 0.5;
            center[i] = c;
            int s = (int) Math.floor(c) - radius + 1;
            int n = radius * 2;
            float[] w = new float[n];
            double sum = 0;
            for (int k = 0; k < n; k++) {
                double d = (c - (s + k)) / scale;
                double v = lanczos(d);
                w[k] = (float) v;
                sum += v;
            }
            if (Math.abs(sum) < 1e-9) {
                for (int k = 0; k < n; k++) w[k] = 0;
                w[n / 2] = 1;
            } else {
                for (int k = 0; k < n; k++) w[k] /= (float) sum;
            }
            start[i] = s;
            weights[i] = w;
        }
        return new Taps(start, weights, center);
    }

    private static double lanczos(double x) {
        if (x == 0) return 1;
        if (x <= -A || x >= A) return 0;
        double px = Math.PI * x;
        return A * Math.sin(px) * Math.sin(px / A) / (px * px);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static final class Taps {
        final int[] start;
        final float[][] weights;
        final double[] center;

        Taps(int[] start, float[][] weights, double[] center) {
            this.start = start;
            this.weights = weights;
            this.center = center;
        }
    }
}
