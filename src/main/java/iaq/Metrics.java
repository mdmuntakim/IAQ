package iaq;

/**
 * Objective quality report.  These numbers are what the pipeline is tuned
 * against: they show that sharpness, local contrast and colour depth go up
 * without clipping the highlights or crushing the shadows.
 */
public final class Metrics {

    public static final class Report {
        public int width;
        public int height;
        public double meanLuma;
        public double contrast;
        public double saturation;
        public double sharpness;
        public double clippedHigh;
        public double clippedLow;

        public String line() {
            return String.format(java.util.Locale.ROOT,
                    "%dx%d  luma %.3f  contrast %.3f  saturation %.3f  sharpness %.4f  clip %.2f%%/%.2f%%",
                    width, height, meanLuma, contrast, saturation, sharpness,
                    clippedHigh * 100.0, clippedLow * 100.0);
        }
    }

    private Metrics() {
    }

    public static Report measure(ImageData img) {
        int n = img.pixels();
        float[] r = new float[n];
        float[] g = new float[n];
        float[] b = new float[n];
        for (int i = 0; i < n; i++) {
            r[i] = Color.linearToSrgb(img.r[i]);
            g[i] = Color.linearToSrgb(img.g[i]);
            b[i] = Color.linearToSrgb(img.b[i]);
        }
        return measure(r, g, b, img.width, img.height);
    }

    public static Report measure(float[] r, float[] g, float[] b, int w, int h) {
        Report report = new Report();
        report.width = w;
        report.height = h;
        int n = w * h;
        float[] y = new float[n];
        double sum = 0;
        double sumSat = 0;
        long high = 0;
        long low = 0;
        for (int i = 0; i < n; i++) {
            float rr = r[i];
            float gg = g[i];
            float bb = b[i];
            float l = Color.luma601(rr, gg, bb);
            y[i] = l;
            sum += l;
            float mx = Math.max(rr, Math.max(gg, bb));
            float mn = Math.min(rr, Math.min(gg, bb));
            sumSat += mx > 1e-5f ? (mx - mn) / mx : 0;
            if (mx >= 0.999f) high++;
            if (l <= 0.001f) low++;
        }
        report.meanLuma = sum / n;
        report.saturation = sumSat / n;
        report.clippedHigh = high / (double) n;
        report.clippedLow = low / (double) n;

        double var = 0;
        for (int i = 0; i < n; i++) {
            double d = y[i] - report.meanLuma;
            var += d * d;
        }
        report.contrast = Math.sqrt(var / n);

        double lap = 0;
        long count = 0;
        for (int yy = 1; yy < h - 1; yy++) {
            int row = yy * w;
            for (int xx = 1; xx < w - 1; xx++) {
                int i = row + xx;
                double v = 4.0 * y[i] - y[i - 1] - y[i + 1] - y[i - w] - y[i + w];
                lap += Math.abs(v);
                count++;
            }
        }
        report.sharpness = count > 0 ? lap / count : 0;
        return report;
    }

    /** Very small structural similarity, used by the tests for determinism. */
    public static double ssim(float[] ar, float[] ag, float[] ab, float[] br, float[] bg, float[] bb, int w, int h) {
        double c1 = 0.01 * 0.01;
        double c2 = 0.03 * 0.03;
        int n = w * h;
        double ma = 0;
        double mb = 0;
        for (int i = 0; i < n; i++) {
            ma += Color.luma601(ar[i], ag[i], ab[i]);
            mb += Color.luma601(br[i], bg[i], bb[i]);
        }
        ma /= n;
        mb /= n;
        double va = 0;
        double vb = 0;
        double cov = 0;
        for (int i = 0; i < n; i++) {
            double a = Color.luma601(ar[i], ag[i], ab[i]) - ma;
            double b = Color.luma601(br[i], bg[i], bb[i]) - mb;
            va += a * a;
            vb += b * b;
            cov += a * b;
        }
        va /= n;
        vb /= n;
        cov /= n;
        return ((2 * ma * mb + c1) * (2 * cov + c2)) / ((ma * ma + mb * mb + c1) * (va + vb + c2));
    }
}
