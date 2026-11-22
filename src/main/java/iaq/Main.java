package iaq;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** IAQ command line entry point. */
public final class Main {

    public static final String VERSION = "1.0.0";

    private Main() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        if (args.length == 0) {
            usage();
            System.exit(2);
        }
        String command = args[0];
        Parsed parsed;
        try {
            parsed = parse(args, 1);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(2);
            return;
        }
        Parallel.configure(parsed.threads);
        try {
            switch (command) {
                case "remaster":
                    remaster(parsed);
                    break;
                case "batch":
                    batch(parsed);
                    break;
                case "probe":
                    probe(parsed);
                    break;
                case "version":
                case "--version":
                    System.out.println("IAQ " + VERSION);
                    break;
                case "help":
                case "-h":
                case "--help":
                    usage();
                    break;
                default:
                    System.err.println("error: unknown command '" + command + "'");
                    usage();
                    System.exit(2);
            }
        } catch (IOException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            System.err.println("error: " + e.getMessage());
            if (System.getenv("IAQ_DEBUG") != null) e.printStackTrace();
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ commands

    private static void remaster(Parsed p) throws IOException {
        if (p.files.isEmpty()) throw new IllegalArgumentException("remaster needs an input image");
        if (p.help) {
            usage();
            return;
        }
        File out = p.out == null ? null : new File(p.out);
        boolean single = p.files.size() == 1;
        if (out != null && single && !out.isDirectory() && !p.out.endsWith("/")) {
            File parent = out.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("cannot create " + parent);
            }
            process(new File(p.files.get(0)), out, p);
        } else {
            File dir = out != null ? out : null;
            if (dir != null && !dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
            for (String name : p.files) {
                File input = new File(name);
                File targetDir = dir != null ? dir : input.getAbsoluteFile().getParentFile();
                File target = new File(targetDir, outputName(input, p, null));
                process(input, target, p);
            }
        }
    }

    private static void batch(Parsed p) throws IOException {
        if (p.files.isEmpty()) throw new IllegalArgumentException("batch needs a directory");
        if (p.help) {
            usage();
            return;
        }
        File root = new File(p.files.get(0));
        if (!root.isDirectory()) throw new IOException(root + " is not a directory");
        File outDir = p.out != null ? new File(p.out) : new File(root, "iaq-out");
        if (!outDir.exists() && !outDir.mkdirs()) throw new IOException("cannot create " + outDir);

        List<Path> inputs = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = p.recursive
                ? Files.walk(root.toPath())
                : Files.list(root.toPath())) {
            stream.filter(Files::isRegularFile)
                    .filter(Main::isImage)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(inputs::add);
        }
        if (inputs.isEmpty()) throw new IOException("no images found in " + root);
        int done = 0;
        for (Path input : inputs) {
            File in = input.toFile();
            String relative = p.recursive
                    ? root.toPath().relativize(input).toString()
                    : in.getName();
            File target = new File(outDir, relative);
            File parent = target.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("cannot create " + parent);
            }
            target = new File(parent, outputName(in, p, p.format));
            process(in, target, p);
            done++;
        }
        if (!p.quiet) System.out.println("IAQ: " + done + " image(s) remastered into " + outDir);
    }

    private static void probe(Parsed p) throws IOException {
        if (p.files.isEmpty()) throw new IllegalArgumentException("probe needs an image");
        for (String name : p.files) {
            File file = new File(name);
            BufferedImage image = read(file);
            ImageData data = ImageData.load(image);
            Metrics.Report report = Metrics.measure(data);
            System.out.println(file.getPath());
            System.out.println("  " + report.line());
        }
    }

    private static void process(File input, File output, Parsed p) throws IOException {
        if (!input.isFile()) throw new IOException(input + " does not exist");
        if (output.exists() && !p.overwrite) {
            throw new IOException(output + " already exists (use --overwrite)");
        }
        String format = resolveFormat(input, output, p.format);
        BufferedImage image = read(input);
        ImageData data = ImageData.load(image);
        Metrics.Report before = Metrics.measure(data);

        Remaster.Options options = p.options;
        if (format.equals("jpg")) options.depth = 8;
        Remaster.Result result = Remaster.process(data, options);

        write(result, output, format);

        if (p.json) {
            System.out.println(json(input, output, format, before, result));
        } else if (!p.quiet) {
            System.out.printf(Locale.ROOT, "%s  %dx%d  ->  %s  %dx%d  %.2fx  %d-bit  %d ms%n",
                    input.getPath(), data.width, data.height,
                    output.getPath(), result.width, result.height,
                    result.appliedScale, result.depth, result.millis);
            if (p.report) {
                System.out.println("  before  " + before.line());
                System.out.println("  after   " + result.report.line());
            }
        }
    }

    private static BufferedImage read(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) throw new IOException("unsupported image: " + file);
        return image;
    }

    private static void write(Remaster.Result r, File output, String format) throws IOException {
        File parent = output.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        if (format.equals("jpg")) {
            BufferedImage image = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_RGB);
            int[] px = new int[r.width * r.height];
            for (int i = 0; i < px.length; i++) {
                int rr = r.depth == 16 ? (r.r[i] & 0xFFFF) >> 8 : r.r[i];
                int gg = r.depth == 16 ? (r.g[i] & 0xFFFF) >> 8 : r.g[i];
                int bb = r.depth == 16 ? (r.b[i] & 0xFFFF) >> 8 : r.b[i];
                px[i] = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
            }
            image.setRGB(0, 0, r.width, r.height, px, 0, r.width);
            writeJpeg(image, output);
        } else {
            Png.write(output, r.width, r.height, r.r, r.g, r.b, r.a, r.depth);
        }
    }

    private static void writeJpeg(BufferedImage image, File output) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("no JPEG writer available");
        ImageWriter writer = writers.next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.95f);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String resolveFormat(File input, File output, String explicit) {
        if (explicit != null) return explicit;
        String ext = extension(output.getName());
        if (ext.equals("jpg") || ext.equals("jpeg")) return "jpg";
        if (ext.equals("png")) return "png";
        String in = extension(input.getName());
        return in.equals("jpg") || in.equals("jpeg") ? "jpg" : "png";
    }

    private static String outputName(File input, Parsed p, String forcedFormat) {
        String format = forcedFormat != null ? forcedFormat : p.format;
        if (format == null) {
            format = extension(input.getName());
            if (!format.equals("jpg") && !format.equals("png")) format = "png";
        }
        return base(input.getName()) + "-iaq." + format;
    }

    private static String base(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static boolean isImage(Path path) {
        String ext = extension(path.getFileName().toString());
        return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg")
                || ext.equals("bmp") || ext.equals("gif");
    }

    private static String json(File input, File output, String format,
                               Metrics.Report before, Remaster.Result after) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        field(sb, "input", input.getPath());
        field(sb, "output", output.getPath());
        field(sb, "format", format);
        sb.append(",\"width\":").append(after.width);
        sb.append(",\"height\":").append(after.height);
        sb.append(",\"scale\":").append(String.format(Locale.ROOT, "%.4f", after.appliedScale));
        sb.append(",\"depth\":").append(after.depth);
        sb.append(",\"millis\":").append(after.millis);
        sb.append(",\"before\":").append(metricsJson(before));
        sb.append(",\"after\":").append(metricsJson(after.report));
        sb.append('}');
        return sb.toString();
    }

    private static String metricsJson(Metrics.Report r) {
        return String.format(Locale.ROOT,
                "{\"width\":%d,\"height\":%d,\"luma\":%.5f,\"contrast\":%.5f,"
                        + "\"saturation\":%.5f,\"sharpness\":%.5f,\"clipHigh\":%.5f,\"clipLow\":%.5f}",
                r.width, r.height, r.meanLuma, r.contrast, r.saturation, r.sharpness,
                r.clippedHigh, r.clippedLow);
    }

    private static void field(StringBuilder sb, String name, String value) {
        if (sb.length() > 1) sb.append(',');
        sb.append('"').append(name).append("\":\"").append(escape(value)).append('"');
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ------------------------------------------------------------------ parsing

    private static Parsed parse(String[] args, int start) {
        Parsed p = new Parsed();
        for (int i = start; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-o":
                case "--out":
                    p.out = value(args, ++i, a);
                    break;
                case "--scale":
                    p.options.scale = number(value(args, ++i, a), a);
                    break;
                case "--width":
                    p.options.width = integer(value(args, ++i, a), a);
                    break;
                case "--max-width":
                    p.options.maxWidth = integer(value(args, ++i, a), a);
                    break;
                case "--strength":
                    p.options.strength = number(value(args, ++i, a), a);
                    break;
                case "--grain":
                    p.options.grain = number(value(args, ++i, a), a);
                    break;
                case "--depth":
                    p.options.depth = integer(value(args, ++i, a), a);
                    if (p.options.depth != 8 && p.options.depth != 16) {
                        throw new IllegalArgumentException("--depth must be 8 or 16");
                    }
                    break;
                case "--format":
                    p.format = format(value(args, ++i, a));
                    break;
                case "--aspect": {
                    String v = value(args, ++i, a);
                    String[] parts = v.split("[:/x]");
                    if (parts.length != 2) throw new IllegalArgumentException("--aspect wants W:H");
                    double aw = Double.parseDouble(parts[0]);
                    double ah = Double.parseDouble(parts[1]);
                    if (aw <= 0 || ah <= 0) throw new IllegalArgumentException("--aspect wants positive numbers");
                    p.options.aspect = aw / ah;
                    break;
                }
                case "--crop": {
                    String v = value(args, ++i, a);
                    String[] parts = v.split(",");
                    if (parts.length != 4) throw new IllegalArgumentException("--crop wants x,y,w,h");
                    p.options.cropX = Integer.parseInt(parts[0].trim());
                    p.options.cropY = Integer.parseInt(parts[1].trim());
                    p.options.cropW = Integer.parseInt(parts[2].trim());
                    p.options.cropH = Integer.parseInt(parts[3].trim());
                    break;
                }
                case "--no-glow":
                    p.options.glow = false;
                    break;
                case "--no-dither":
                    p.options.dither = false;
                    break;
                case "--threads":
                    p.threads = integer(value(args, ++i, a), a);
                    break;
                case "--report":
                    p.report = true;
                    break;
                case "--json":
                    p.json = true;
                    break;
                case "--overwrite":
                    p.overwrite = true;
                    break;
                case "--recursive":
                    p.recursive = true;
                    break;
                case "--quiet":
                    p.quiet = true;
                    break;
                case "-h":
                case "--help":
                    p.help = true;
                    break;
                default:
                    if (a.startsWith("-") && !a.equals("-")) {
                        throw new IllegalArgumentException("unknown option '" + a + "'");
                    }
                    p.files.add(a);
                    break;
            }
        }
        if (p.options.scale < 0.1 || p.options.scale > 8) {
            throw new IllegalArgumentException("--scale must be between 0.1 and 8");
        }
        return p;
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length) throw new IllegalArgumentException(option + " needs a value");
        return args[i];
    }

    private static double number(String value, String option) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " wants a number, got '" + value + "'");
        }
    }

    private static int integer(String value, String option) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " wants an integer, got '" + value + "'");
        }
    }

    private static String format(String value) {
        String f = value.toLowerCase(Locale.ROOT);
        if (f.equals("jpeg")) f = "jpg";
        if (!f.equals("png") && !f.equals("jpg")) {
            throw new IllegalArgumentException("--format must be png or jpg");
        }
        return f;
    }

    private static void usage() {
        System.out.println("IAQ " + VERSION + " - high fidelity anime remaster");
        System.out.println();
        System.out.println("usage:");
        System.out.println("  java -jar IAQ.jar remaster <image> [-o out] [options]");
        System.out.println("  java -jar IAQ.jar batch <dir> [-o outdir] [options]");
        System.out.println("  java -jar IAQ.jar probe <image...>");
        System.out.println("  java -jar IAQ.jar version | help");
        System.out.println();
        System.out.println("options:");
        System.out.println("  -o, --out PATH      output file, or directory for several inputs");
        System.out.println("  --scale N           upscale factor, default 2 (0.1 .. 8)");
        System.out.println("  --width N           exact output width, keeps the aspect ratio");
        System.out.println("  --max-width N       cap the output width, default 3840");
        System.out.println("  --strength N        remaster amount, 0 .. 1, default 1");
        System.out.println("  --grain N           fine grain, 0 .. 1, default 0.35");
        System.out.println("  --depth N           8 or 16 bit PNG, default 8");
        System.out.println("  --format png|jpg    output format, inferred from -o otherwise");
        System.out.println("  --aspect W:H        centre crop before remastering");
        System.out.println("  --crop X,Y,W,H      explicit crop before remastering");
        System.out.println("  --no-glow           disable the highlight bloom");
        System.out.println("  --no-dither         disable the output dither");
        System.out.println("  --threads N         worker threads, default all cores");
        System.out.println("  --report            print the quality metrics");
        System.out.println("  --json              one JSON object per output file");
        System.out.println("  --overwrite         replace an existing output file");
        System.out.println("  --recursive         recurse into subdirectories (batch)");
        System.out.println("  --quiet             only print errors");
        System.out.println();
        System.out.println("examples:");
        System.out.println("  java -jar IAQ.jar remaster before.png -o after.png --scale 2 --report");
        System.out.println("  java -jar IAQ.jar remaster art.jpg -o art-4k.png --width 3840 --depth 16");
        System.out.println("  java -jar IAQ.jar batch frames/ -o out/ --recursive --format png");
    }

    private static final class Parsed {
        final List<String> files = new ArrayList<>();
        final Remaster.Options options = new Remaster.Options();
        String out;
        String format;
        int threads;
        boolean report;
        boolean json;
        boolean overwrite;
        boolean recursive;
        boolean quiet;
        boolean help;
    }
}
