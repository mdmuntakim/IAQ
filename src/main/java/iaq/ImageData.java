package iaq;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.Raster;

/**
 * An image in linear RGB float, one plane per channel, alpha optional.
 * Planes keep every filter trivially parallel and cache friendly.
 */
public final class ImageData {

    public final int width;
    public final int height;
    public final float[] r;
    public final float[] g;
    public final float[] b;
    /** Null when the image is fully opaque. */
    public final float[] a;

    public ImageData(int width, int height, boolean alpha) {
        this.width = width;
        this.height = height;
        int n = width * height;
        this.r = new float[n];
        this.g = new float[n];
        this.b = new float[n];
        this.a = alpha ? new float[n] : null;
    }

    public int pixels() {
        return width * height;
    }

    public boolean hasAlpha() {
        return a != null;
    }

    /** Decodes 8 and 16 bit PNG/JPEG (and anything else ImageIO knows). */
    public static ImageData load(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        ColorModel cm = image.getColorModel();
        boolean hasAlpha = cm.hasAlpha();
        ImageData out = new ImageData(w, h, hasAlpha);
        Raster raster = image.getRaster();

        if (cm instanceof ComponentColorModel && raster.getNumBands() <= 4) {
            int bits = cm.getComponentSize(0);
            double max = bits >= 16 ? 65535.0 : 255.0;
            int bands = raster.getNumBands();
            boolean gray = bands - (hasAlpha ? 1 : 0) == 1;
            boolean premultiplied = cm.isAlphaPremultiplied();
            int[] sample = new int[bands];
            for (int y = 0; y < h; y++) {
                int row = y * w;
                for (int x = 0; x < w; x++) {
                    raster.getPixel(x, y, sample);
                    float rr;
                    float gg;
                    float bb;
                    float aa = 1f;
                    if (gray) {
                        rr = gg = bb = (float) (sample[0] / max);
                        if (hasAlpha) aa = (float) (sample[1] / max);
                    } else {
                        rr = (float) (sample[0] / max);
                        gg = (float) (sample[1] / max);
                        bb = (float) (sample[2] / max);
                        if (hasAlpha) aa = (float) (sample[3] / max);
                    }
                    if (premultiplied && aa > 0f) {
                        rr /= aa;
                        gg /= aa;
                        bb /= aa;
                    }
                    int i = row + x;
                    out.r[i] = Color.srgbToLinear(rr);
                    out.g[i] = Color.srgbToLinear(gg);
                    out.b[i] = Color.srgbToLinear(bb);
                    if (hasAlpha) out.a[i] = aa;
                }
            }
            return out;
        }

        // Packed, indexed, CMYK and friends: let the colour model do the work.
        int[] argb = new int[w];
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, argb, 0, w);
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int p = argb[x];
                int i = row + x;
                out.r[i] = Color.srgbToLinear(((p >> 16) & 0xFF) / 255.0);
                out.g[i] = Color.srgbToLinear(((p >> 8) & 0xFF) / 255.0);
                out.b[i] = Color.srgbToLinear((p & 0xFF) / 255.0);
                if (hasAlpha) out.a[i] = ((p >>> 24) & 0xFF) / 255f;
            }
        }
        return out;
    }

    /** Centre crop to the given pixel rectangle; coordinates are clamped. */
    public ImageData crop(int x, int y, int cw, int ch) {
        cw = Math.max(1, Math.min(cw, width));
        ch = Math.max(1, Math.min(ch, height));
        x = Math.max(0, Math.min(x, width - cw));
        y = Math.max(0, Math.min(y, height - ch));
        if (x == 0 && y == 0 && cw == width && ch == height) return this;
        ImageData out = new ImageData(cw, ch, hasAlpha());
        for (int row = 0; row < ch; row++) {
            int src = (y + row) * width + x;
            int dst = row * cw;
            System.arraycopy(r, src, out.r, dst, cw);
            System.arraycopy(g, src, out.g, dst, cw);
            System.arraycopy(b, src, out.b, dst, cw);
            if (hasAlpha()) System.arraycopy(a, src, out.a, dst, cw);
        }
        return out;
    }
}
