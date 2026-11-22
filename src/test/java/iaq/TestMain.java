package iaq;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

/**
 * JVM test suite.  No framework: a missing dependency would defeat the point
 * of a zero-dependency build.
 */
public final class TestMain {

    private static int passed;
    private static int failed;

    private TestMain() {
    }

    public static void main(String[] args) throws Exception {
        File tmp = Files.createTempDirectory("iaq-test").toFile();
        tmp.deleteOnExit();

        testColour();
        testPngRoundTrip8(tmp);
        testPngRoundTrip16(tmp);
        testDeterminism();
        testAlpha();
        testCropAndAspect();
        testWidthOption();
        testMetrics();
        testCli(tmp);
        testBatch(tmp);
        testPerformance();

        System.out.println();
        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ tests

    private static void testColour() {
        check("srgb round trip", Math.abs(Color.linearToSrgb(Color.srgbToLinear(0.42)) - 0.42) < 1e-5);
        check("linear black", Color.srgbToLinear(0) == 0);
        check("linear white", Math.abs(Color.srgbToLinear(1) - 1) < 1e-5);
        check("shoulder keeps range", Color.shoulder(1.4f, 0.86f) <= 1.0f);
        check("shoulder keeps knee", Math.abs(Color.shoulder(0.5f, 0.86f) - 0.5f) < 1e-6);
    }

    private static void testPngRoundTrip8(File tmp) throws Exception {
        int w = 37;
        int h = 23;
        short[] r = new short[w * h];
        short[] g = new short[w * h];
        short[] b = new short[w * h];
        short[] a = new short[w * h];
        for (int i = 0; i < r.length; i++) {
            r[i] = (short) (i % 256);
            g[i] = (short) ((i * 7) % 256);
            b[i] = (short) ((i * 13) % 256);
            a[i] = (short) ((i * 3) % 256);
        }
        File file = new File(tmp, "roundtrip8.png");
        Png.write(file, w, h, r, g, b, a, 8);
        BufferedImage read = ImageIO.read(file);
        check("8 bit png dimensions", read != null && read.getWidth() == w && read.getHeight() == h);
        check("8 bit png alpha", read != null && read.getColorModel().hasAlpha());
        check("8 bit png pixels", read != null
                && (read.getRGB(5, 7) & 0xFF) == b[7 * w + 5]);
    }

    private static void testPngRoundTrip16(File tmp) throws Exception {
        int w = 19;
        int h = 11;
        short[] r = new short[w * h];
        short[] g = new short[w * h];
        short[] b = new short[w * h];
        for (int i = 0; i < r.length; i++) {
            r[i] = (short) ((i * 257) & 0xFFFF);
            g[i] = (short) ((i * 911) & 0xFFFF);
            b[i] = (short) ((i * 4243) & 0xFFFF);
        }
        File file = new File(tmp, "roundtrip16.png");
        Png.write(file, w, h, r, g, b, null, 16);
        BufferedImage read = ImageIO.read(file);
        check("16 bit png dimensions", read != null && read.getWidth() == w && read.getHeight() == h);
        check("16 bit png depth", read != null && read.getColorModel().getComponentSize(0) == 16);
        int sample = read.getRaster().getSample(3, 4, 0);
        check("16 bit png sample", Math.abs(sample - (r[4 * w + 3] & 0xFFFF)) <= 1);
    }

    private static void testDeterminism() {
        ImageData image = synthetic(160, 90, true);
        Remaster.Options options = new Remaster.Options();
        options.scale = 2;
        options.seed = 42;
        Remaster.Result a = Remaster.process(image, options);
        Remaster.Result b = Remaster.process(image, options);
        boolean same = a.width == b.width && a.height == b.height;
        for (int i = 0; same && i < a.r.length; i++) {
            same = a.r[i] == b.r[i] && a.g[i] == b.g[i] && a.b[i] == b.b[i];
        }
        check("deterministic output", same);
    }

    private static void testAlpha() {
        ImageData image = synthetic(64, 64, true);
        for (int i = 0; i < image.pixels(); i++) {
            image.a[i] = i % 5 == 0 ? 0f : 1f;
        }
        Remaster.Options options = new Remaster.Options();
        options.scale = 2;
        Remaster.Result result = Remaster.process(image, options);
        check("alpha plane exists", result.a != null);
        check("alpha opaque", result.a[20 * result.width + 22] > 200);
        check("alpha transparent", result.a[0] < 20);
    }

    private static void testCropAndAspect() {
        ImageData image = synthetic(200, 100, false);
        Remaster.Options options = new Remaster.Options();
        options.scale = 1;
        options.aspect = 4.0 / 3.0;
        Remaster.Result result = Remaster.process(image, options);
        check("aspect crop width", result.width == 133);
        check("aspect crop height", result.height == 100);

        Remaster.Options crop = new Remaster.Options();
        crop.scale = 1;
        crop.cropX = 10;
        crop.cropY = 10;
        crop.cropW = 50;
        crop.cropH = 20;
        Remaster.Result cropped = Remaster.process(image, crop);
        check("explicit crop", cropped.width == 50 && cropped.height == 20);
    }

    private static void testWidthOption() {
        ImageData image = synthetic(300, 150, false);
        Remaster.Options options = new Remaster.Options();
        options.width = 640;
        options.depth = 8;
        Remaster.Result result = Remaster.process(image, options);
        check("width option", result.width == 640 && result.height == 320);
    }

    private static void testMetrics() {
        ImageData image = synthetic(256, 144, false);
        Metrics.Report before = Metrics.measure(image);
        Remaster.Options options = new Remaster.Options();
        options.scale = 2;
        Remaster.Result result = Remaster.process(image, options);
        Metrics.Report after = result.report;
        check("contrast rises", after.contrast > before.contrast);
        check("saturation rises", after.saturation > before.saturation);
        check("sharpness is real", after.sharpness > 0.002);
        check("highlights not blown", after.clippedHigh < 0.06);
        check("shadows not crushed", after.clippedLow < 0.35);
        check("output range", allInRange(result.r) && allInRange(result.g) && allInRange(result.b));
    }

    private static void testCli(File tmp) throws Exception {
        File input = new File(tmp, "cli.png");
        ImageIO.write(toImage(synthetic(120, 80, false)), "png", input);
        File output = new File(tmp, "cli-iaq.png");
        Main.main(new String[]{"remaster", input.getPath(), "-o", output.getPath(), "--scale", "2", "--quiet"});
        check("cli wrote output", output.isFile() && output.length() > 0);
        BufferedImage read = ImageIO.read(output);
        check("cli output size", read.getWidth() == 240 && read.getHeight() == 160);
    }

    private static void testBatch(File tmp) throws Exception {
        File dir = new File(tmp, "batch");
        check("batch dir", dir.mkdirs());
        ImageIO.write(toImage(synthetic(40, 30, false)), "png", new File(dir, "a.png"));
        ImageIO.write(toImage(synthetic(40, 30, false)), "png", new File(dir, "b.png"));
        File out = new File(tmp, "batch-out");
        Main.main(new String[]{"batch", dir.getPath(), "-o", out.getPath(), "--scale", "2", "--quiet"});
        check("batch output a", new File(out, "a-iaq.png").isFile());
        check("batch output b", new File(out, "b-iaq.png").isFile());
    }

    private static void testPerformance() {
        ImageData image = synthetic(1800, 900, false);
        Remaster.Options options = new Remaster.Options();
        options.scale = 2;
        Remaster.Result result = Remaster.process(image, options);
        System.out.printf(java.util.Locale.ROOT,
                "    performance: 1800x900 -> %dx%d in %d ms (%d cores)%n",
                result.width, result.height, result.millis,
                Runtime.getRuntime().availableProcessors());
        check("performance under 60 s", result.millis < 60_000);
    }

    // ------------------------------------------------------------------ helpers

    private static ImageData synthetic(int w, int h, boolean alpha) {
        ImageData image = new ImageData(w, h, alpha);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                double u = x / (double) Math.max(1, w - 1);
                double v = y / (double) Math.max(1, h - 1);
                double sky = 0.55 + 0.25 * v;
                double rr = sky;
                double gg = sky * 0.72;
                double bb = sky * 0.30;
                if (x > w * 0.15 && x < w * 0.45 && y > h * 0.35 && y < h * 0.9) {
                    rr = 0.09;
                    gg = 0.08;
                    bb = 0.10;
                }
                if (x > w * 0.6 && x < w * 0.9 && y > h * 0.2 && y < h * 0.8) {
                    rr = 0.72;
                    gg = 0.16;
                    bb = 0.12;
                }
                if (Math.abs(x - w * 0.55) < 2 || Math.abs(y - h * 0.5) < 2) {
                    rr = gg = bb = 0.02;
                }
                rr += (u - 0.5) * 0.02;
                image.r[i] = Color.srgbToLinear(Color.clamp01(rr));
                image.g[i] = Color.srgbToLinear(Color.clamp01(gg));
                image.b[i] = Color.srgbToLinear(Color.clamp01(bb));
                if (alpha) image.a[i] = 1f;
            }
        }
        return image;
    }

    private static BufferedImage toImage(ImageData data) {
        BufferedImage image = new BufferedImage(data.width, data.height, BufferedImage.TYPE_INT_RGB);
        int[] px = new int[data.width * data.height];
        for (int i = 0; i < px.length; i++) {
            int r = Math.round(Color.linearToSrgb(data.r[i]) * 255);
            int g = Math.round(Color.linearToSrgb(data.g[i]) * 255);
            int b = Math.round(Color.linearToSrgb(data.b[i]) * 255);
            px[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        image.setRGB(0, 0, data.width, data.height, px, 0, data.width);
        return image;
    }

    private static boolean allInRange(short[] values) {
        for (short v : values) {
            int i = v & 0xFFFF;
            if (i < 0 || i > 255) return false;
        }
        return true;
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("ok   " + name);
        } else {
            failed++;
            System.out.println("FAIL " + name);
        }
    }
}
