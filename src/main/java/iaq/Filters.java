package iaq;

/**
 * The spatial half of the remaster: noise control, local contrast and detail.
 * Everything here works on sRGB encoded planes so the adjustments follow
 * perception instead of raw light.
 */
public final class Filters {

    private static final double[] SPATIAL = buildSpatial();

    private Filters() {
    }

    /** Separable Gaussian blur with clamped edges. */
    public static float[] gaussian(float[] src, int w, int h, double sigma) {
        if (sigma < 0.35) return src.clone();
        int radius = Math.max(1, (int) Math.ceil(sigma * 3.0));
        float[] kernel = new float[radius * 2 + 1];
        double sum = 0;
        for (int i = -radius; i <= radius; i++) {
            double v = Math.exp(-(i * i) / (2.0 * sigma * sigma));
            kernel[i + radius] = (float) v;
            sum += v;
        }
        for (int i = 0; i < kernel.length; i++) kernel[i] /= (float) sum;

        float[] tmp = new float[w * h];
        Parallel.rows(h, y -> {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float acc = 0;
                for (int k = -radius; k <= radius; k++) {
                    int sx = x + k;
                    if (sx < 0) sx = 0;
                    else if (sx >= w) sx = w - 1;
                    acc += src[row + sx] * kernel[k + radius];
                }
                tmp[row + x] = acc;
            }
        });

        float[] out = new float[w * h];
        Parallel.rows(h, y -> {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float acc = 0;
                for (int k = -radius; k <= radius; k++) {
                    int sy = y + k;
                    if (sy < 0) sy = 0;
                    else if (sy >= h) sy = h - 1;
                    acc += tmp[sy * w + x] * kernel[k + radius];
                }
                out[row + x] = acc;
            }
        });
        return out;
    }

    /**
     * Joint bilateral chroma denoise.  Luma guides the filter, so ink lines
     * and colour boundaries stay sharp while JPEG chroma noise and colour
     * fringing are averaged away.
     */
    public static void chromaDenoise(float[] r, float[] g, float[] b, int w, int h, double strength) {
        if (strength <= 0.001) return;
        int n = w * h;
        float[] y = new float[n];
        float[] cr = new float[n];
        float[] cb = new float[n];
        Parallel.rows(h, row -> {
            for (int x = 0; x < w; x++) {
                int i = row * w + x;
                float l = Color.luma601(r[i], g[i], b[i]);
                y[i] = l;
                cr[i] = r[i] - l;
                cb[i] = b[i] - l;
            }
        });

        float[] range = new float[256];
        double sigma = 0.045;
        for (int k = 0; k < 256; k++) {
            double d = k / 255.0;
            range[k] = (float) Math.exp(-(d * d) / (2 * sigma * sigma));
        }

        float[] outCr = new float[n];
        float[] outCb = new float[n];
        Parallel.rows(h, row -> {
            for (int x = 0; x < w; x++) {
                int ci = row * w + x;
                double sw = 0;
                double scr = 0;
                double scb = 0;
                for (int dy = -2; dy <= 2; dy++) {
                    int sy = row + dy;
                    if (sy < 0) sy = 0;
                    else if (sy >= h) sy = h - 1;
                    for (int dx = -2; dx <= 2; dx++) {
                        int sx = x + dx;
                        if (sx < 0) sx = 0;
                        else if (sx >= w) sx = w - 1;
                        int ni = sy * w + sx;
                        int idx = (int) (Math.abs(y[ni] - y[ci]) * 255f);
                        if (idx > 255) idx = 255;
                        double wgt = SPATIAL[(dy + 2) * 5 + (dx + 2)] * range[idx];
                        sw += wgt;
                        scr += cr[ni] * wgt;
                        scb += cb[ni] * wgt;
                    }
                }
                outCr[ci] = (float) (scr / sw);
                outCb[ci] = (float) (scb / sw);
            }
        });

        double blend = strength * 0.85;
        Parallel.rows(h, row -> {
            for (int x = 0; x < w; x++) {
                int i = row * w + x;
                float ncr = (float) (cr[i] + (outCr[i] - cr[i]) * blend);
                float ncb = (float) (cb[i] + (outCb[i] - cb[i]) * blend);
                float nr = y[i] + ncr;
                float nb = y[i] + ncb;
                float ng = (y[i] - 0.2126f * nr - 0.0722f * nb) / 0.7152f;
                r[i] = Color.clamp01(nr);
                g[i] = Color.clamp01(ng);
                b[i] = Color.clamp01(nb);
            }
        });
    }

    /**
     * Contrast limited adaptive histogram equalisation on luma.
     *
     * <p>The mapping is clamped to the identity plus or minus a small delta,
     * so a flat sky cannot turn into a posterised staircase while textured
     * areas still get the local punch of the reference remaster.</p>
     */
    public static void clahe(float[] r, float[] g, float[] b, int w, int h, double strength) {
        if (strength <= 0.001) return;
        int n = w * h;
        float[] y = luma(r, g, b, w, h);

        int tilesX = Math.max(1, Math.min(8, w / 8));
        int tilesY = Math.max(1, Math.min(8, h / 8));
        int tileW = (w + tilesX - 1) / tilesX;
        int tileH = (h + tilesY - 1) / tilesY;
        int tiles = tilesX * tilesY;
        float[] lut = new float[tiles * 256];
        int[] hist = new int[256];
        double maxDelta = 0.090;

        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                java.util.Arrays.fill(hist, 0);
                int x0 = tx * tileW;
                int y0 = ty * tileH;
                int x1 = Math.min(w, x0 + tileW);
                int y1 = Math.min(h, y0 + tileH);
                int count = 0;
                for (int yy = y0; yy < y1; yy++) {
                    int row = yy * w;
                    for (int xx = x0; xx < x1; xx++) {
                        int v = (int) (y[row + xx] * 255f);
                        if (v < 0) v = 0;
                        else if (v > 255) v = 255;
                        hist[v]++;
                        count++;
                    }
                }
                if (count == 0) count = 1;
                int limit = Math.max(1, (int) Math.round(2.5 * count / 256.0));
                int excess = 0;
                for (int i = 0; i < 256; i++) {
                    if (hist[i] > limit) {
                        excess += hist[i] - limit;
                        hist[i] = limit;
                    }
                }
                int share = excess / 256;
                int rem = excess % 256;
                for (int i = 0; i < 256; i++) {
                    hist[i] += share + (i < rem ? 1 : 0);
                }
                int[] cdf = new int[256];
                int acc = 0;
                for (int i = 0; i < 256; i++) {
                    acc += hist[i];
                    cdf[i] = acc;
                }
                int first = 0;
                while (first < 256 && cdf[first] == 0) first++;
                int cdfMin = first < 256 ? cdf[first] : 0;
                int denom = Math.max(1, count - cdfMin);
                int base = (ty * tilesX + tx) * 256;
                for (int i = 0; i < 256; i++) {
                    double id = i / 255.0;
                    double mapped = (cdf[i] - cdfMin) / (double) denom;
                    if (mapped < id - maxDelta) mapped = id - maxDelta;
                    if (mapped > id + maxDelta) mapped = id + maxDelta;
                    lut[base + i] = (float) mapped;
                }
            }
        }

        Parallel.rows(h, row -> {
            int yi = row * w;
            for (int x = 0; x < w; x++) {
                int i = yi + x;
                double v = y[i] * 255.0;
                int i0 = (int) v;
                if (i0 < 0) i0 = 0;
                else if (i0 > 255) i0 = 255;
                int i1 = i0 < 255 ? i0 + 1 : 255;
                double fv = v - i0;

                double tx = (x + 0.5) / tileW - 0.5;
                int ix = (int) Math.floor(tx);
                double fx = tx - ix;
                if (tilesX == 1) {
                    ix = 0;
                    fx = 0;
                } else if (ix < 0) {
                    ix = 0;
                    fx = 0;
                } else if (ix >= tilesX - 1) {
                    ix = tilesX - 2;
                    fx = 1;
                }
                double ty = (row + 0.5) / tileH - 0.5;
                int iy = (int) Math.floor(ty);
                double fy = ty - iy;
                if (tilesY == 1) {
                    iy = 0;
                    fy = 0;
                } else if (iy < 0) {
                    iy = 0;
                    fy = 0;
                } else if (iy >= tilesY - 1) {
                    iy = tilesY - 2;
                    fy = 1;
                }

                int ix1 = Math.min(tilesX - 1, ix + 1);
                int iy1 = Math.min(tilesY - 1, iy + 1);
                double m00 = lutAt(lut, tilesX, ix, iy, i0, i1, fv);
                double m10 = lutAt(lut, tilesX, ix1, iy, i0, i1, fv);
                double m01 = lutAt(lut, tilesX, ix, iy1, i0, i1, fv);
                double m11 = lutAt(lut, tilesX, ix1, iy1, i0, i1, fv);
                double m0 = m00 + (m10 - m00) * fx;
                double m1 = m01 + (m11 - m01) * fx;
                double mapped = m0 + (m1 - m0) * fy;

                double target = y[i] + (mapped - y[i]) * strength
                        * (0.20 + 0.80 * Color.smoothstep(0.12, 0.55, y[i]));
                double scale = target / Math.max(1e-4, y[i]);
                if (scale < 0.55) scale = 0.55;
                else if (scale > 1.8) scale = 1.8;
                r[i] = Color.clamp01(r[i] * scale);
                g[i] = Color.clamp01(g[i] * scale);
                b[i] = Color.clamp01(b[i] * scale);
            }
        });
    }

    private static double lutAt(float[] lut, int tilesX, int tx, int ty, int i0, int i1, double f) {
        int base = (ty * tilesX + tx) * 256;
        double a = lut[base + i0];
        double b = lut[base + i1];
        return a + (b - a) * f;
    }

    /**
     * Two-scale detail boost with a hard anti-halo clamp and a gentle
     * line-deepen pass.  The clamp keeps white ink halos from forming, which
     * is the usual failure mode of naive unsharp masking on line art.
     */
    public static void detail(float[] r, float[] g, float[] b, int w, int h, double strength) {
        if (strength <= 0.001) return;
        float[] y = luma(r, g, b, w, h);
        float[] soft1 = gaussian(y, w, h, 1.0);
        float[] soft2 = gaussian(y, w, h, 2.6);
        float[] target = new float[w * h];

        Parallel.rows(h, row -> {
            for (int x = 0; x < w; x++) {
                int i = row * w + x;
                double base = y[i];
                double detail = (base - soft1[i]) * 0.62 + (base - soft2[i]) * 0.38;
                double ny = base + detail * strength;

                float lo = y[i];
                float hi = y[i];
                for (int dy = -1; dy <= 1; dy++) {
                    int sy = row + dy;
                    if (sy < 0) sy = 0;
                    else if (sy >= h) sy = h - 1;
                    for (int dx = -1; dx <= 1; dx++) {
                        int sx = x + dx;
                        if (sx < 0) sx = 0;
                        else if (sx >= w) sx = w - 1;
                        float v = y[sy * w + sx];
                        if (v < lo) lo = v;
                        if (v > hi) hi = v;
                    }
                }
                if (ny < lo - 0.012) ny = lo - 0.012;
                else if (ny > hi + 0.012) ny = hi + 0.012;
                if (base < 0.30 && base < soft1[i] - 0.025) {
                    ny *= 1.0 - 0.12 * strength;
                }
                target[i] = (float) ny;
            }
        });

        Parallel.rows(h, row -> {
            for (int x = 0; x < w; x++) {
                int i = row * w + x;
                double scale = target[i] / Math.max(1e-4, y[i]);
                if (scale < 0.5) scale = 0.5;
                else if (scale > 1.8) scale = 1.8;
                r[i] = Color.clamp01(r[i] * scale);
                g[i] = Color.clamp01(g[i] * scale);
                b[i] = Color.clamp01(b[i] * scale);
            }
        });
    }

    public static float[] luma(float[] r, float[] g, float[] b, int w, int h) {
        float[] y = new float[w * h];
        Parallel.rows(h, row -> {
            for (int x = 0; x < w; x++) {
                int i = row * w + x;
                y[i] = Color.luma601(r[i], g[i], b[i]);
            }
        });
        return y;
    }

    private static double[] buildSpatial() {
        double[] s = new double[25];
        double sigma = 1.35;
        double sum = 0;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                double v = Math.exp(-(dx * dx + dy * dy) / (2 * sigma * sigma));
                s[(dy + 2) * 5 + (dx + 2)] = v;
                sum += v;
            }
        }
        for (int i = 0; i < s.length; i++) s[i] /= sum;
        return s;
    }
}
