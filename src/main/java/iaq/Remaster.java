package iaq;

/**
 * The IAQ remaster pipeline.
 *
 * <pre>
 *   decode (linear RGB)
 *     -> centre crop / aspect
 *     -> Lanczos-3 upscale with anti-ringing clamp
 *     -> joint bilateral chroma denoise
 *     -> contrast limited adaptive histogram equalisation on luma
 *     -> two scale detail boost with anti-halo clamp and line deepen
 *     -> filmic tone curve, vibrance, warm split tone, highlight shoulder
 *     -> multi scale highlight bloom
 *     -> optical vignette
 *     -> triangular dither + fine grain -> 8 or 16 bit output
 * </pre>
 */
public final class Remaster {

    public static final class Options {
        public double scale = 2.0;
        public int width = 0;
        public int maxWidth = 3840;
        public double strength = 1.0;
        public double grain = 0.35;
        public boolean glow = true;
        public int depth = 8;
        public int cropX = -1;
        public int cropY = -1;
        public int cropW = 0;
        public int cropH = 0;
        public double aspect = 0;
        public long seed = 0x1A9L;
        public boolean dither = true;
    }

    public static final class Result {
        public int width;
        public int height;
        public int depth;
        public short[] r;
        public short[] g;
        public short[] b;
        public short[] a;
        public Metrics.Report report;
        public long millis;
        public double appliedScale;
    }

    private Remaster() {
    }

    public static Result process(ImageData input, Options options) {
        long started = System.nanoTime();
        Options o = options;
        ImageData src = input;

        if (o.cropX >= 0) {
            src = src.crop(o.cropX, o.cropY, o.cropW, o.cropH);
        } else if (o.aspect > 0) {
            int cw;
            int ch;
            double current = src.width / (double) src.height;
            if (current > o.aspect) {
                ch = src.height;
                cw = Math.max(1, (int) Math.round(ch * o.aspect));
            } else {
                cw = src.width;
                ch = Math.max(1, (int) Math.round(cw / o.aspect));
            }
            src = src.crop((src.width - cw) / 2, (src.height - ch) / 2, cw, ch);
        }

        int dw;
        int dh;
        if (o.width > 0) {
            dw = o.width;
            dh = Math.max(2, (int) Math.round(dw * src.height / (double) src.width));
        } else {
            dw = Math.max(2, (int) Math.round(src.width * o.scale));
            dh = Math.max(2, (int) Math.round(src.height * o.scale));
        }
        if (o.maxWidth > 0 && dw > o.maxWidth) {
            dh = Math.max(2, (int) Math.round(dh * o.maxWidth / (double) dw));
            dw = o.maxWidth;
        }
        long maxPixels = 48_000_000L;
        if ((long) dw * dh > maxPixels) {
            double k = Math.sqrt(maxPixels / ((double) dw * dh));
            dw = Math.max(2, (int) Math.round(dw * k));
            dh = Math.max(2, (int) Math.round(dh * k));
        }

        ImageData img = Resample.lanczos(src, dw, dh, 0.9);
        final int w = dw;
        final int h = dh;
        final int n = w * h;

        // Linear light is only needed for resampling; every creative stage is
        // perceptual, so encode once and stay in sRGB.
        Parallel.rows(h, y -> {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int i = row + x;
                img.r[i] = Color.linearToSrgb(img.r[i]);
                img.g[i] = Color.linearToSrgb(img.g[i]);
                img.b[i] = Color.linearToSrgb(img.b[i]);
            }
        });

        double strength = Color.clamp(o.strength, 0.0, 1.0);
        Filters.chromaDenoise(img.r, img.g, img.b, w, h, strength);
        Filters.clahe(img.r, img.g, img.b, w, h, strength);
        Filters.detail(img.r, img.g, img.b, w, h, strength);
        Grade.apply(img.r, img.g, img.b, w, h, strength);
        if (o.glow) Grade.bloom(img.r, img.g, img.b, w, h, strength);
        Grade.vignette(img.r, img.g, img.b, w, h, strength);
        Grade.finish(img.r, img.g, img.b, w, h, strength);

        Result result = new Result();
        result.width = w;
        result.height = h;
        result.depth = o.depth == 16 ? 16 : 8;
        result.report = Metrics.measure(img.r, img.g, img.b, w, h);
        result.appliedScale = w / (double) src.width;

        int max = result.depth == 16 ? 65535 : 255;
        short[] qr = new short[n];
        short[] qg = new short[n];
        short[] qb = new short[n];
        short[] qa = img.hasAlpha() ? new short[n] : null;
        final double grain = Color.clamp(o.grain, 0.0, 1.0);
        final boolean dither = o.dither;
        final long seed = o.seed;

        Parallel.rows(h, y -> {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                double lumaNoise = grain > 0
                        ? (rand(x, y, seed, 7) - 0.5) * grain * 1.7 / 255.0
                        : 0;
                double dr = dither ? (rand(x, y, seed, 1) + rand(x, y, seed, 2) - 1.0) / max : 0;
                double dg = dither ? (rand(x, y, seed, 3) + rand(x, y, seed, 4) - 1.0) / max : 0;
                double db = dither ? (rand(x, y, seed, 5) + rand(x, y, seed, 6) - 1.0) / max : 0;
                qr[i] = (short) Math.round(Color.clamp01(img.r[i] + lumaNoise + dr) * max);
                qg[i] = (short) Math.round(Color.clamp01(img.g[i] + lumaNoise + dg) * max);
                qb[i] = (short) Math.round(Color.clamp01(img.b[i] + lumaNoise + db) * max);
                if (qa != null) qa[i] = (short) Math.round(Color.clamp01(img.a[i]) * max);
            }
        });

        result.r = qr;
        result.g = qg;
        result.b = qb;
        result.a = qa;
        result.millis = (System.nanoTime() - started) / 1_000_000L;
        return result;
    }

    /** Deterministic value noise in [0,1). */
    private static double rand(int x, int y, long seed, int channel) {
        long h = seed;
        h = h * 6364136223846793005L + 1442695040888963407L;
        h ^= (x + 0x9E3779B9L) * 0xBF58476D1CE4E5B9L;
        h ^= (y + 0x85EBCA6BL) * 0xC2B2AE3D27D4EB4FL;
        h ^= channel * 0x27D4EB2F165667C5L;
        h ^= h >>> 31;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }
}
