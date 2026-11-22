package iaq;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Minimal, dependency free PNG encoder.
 *
 * <p>ImageIO can only write 8 bit PNG, and it offers no control over the
 * quantiser.  IAQ writes 8 and 16 bit RGB/RGBA itself so the dither that the
 * pipeline adds survives into the file, which is what keeps large anime
 * gradients (skies, glow) free of banding.</p>
 */
public final class Png {

    private static final byte[] SIGNATURE = {
            (byte) 137, 80, 78, 71, 13, 10, 26, 10
    };

    private Png() {
    }

    public static void write(File file, int w, int h,
                             short[] r, short[] g, short[] b, short[] a,
                             int depth) throws IOException {
        if (depth != 8 && depth != 16) throw new IllegalArgumentException("depth must be 8 or 16");
        int channels = a != null ? 4 : 3;
        int bytesPerSample = depth == 16 ? 2 : 1;
        int bpp = channels * bytesPerSample;
        int rowBytes = w * bpp;

        byte[] raw = new byte[rowBytes];
        byte[] prev = new byte[rowBytes];
        byte[] candidate = new byte[rowBytes];
        byte[] best = new byte[rowBytes];
        byte[] filtered = new byte[rowBytes + 1];

        ByteArrayOutputStream compressed = new ByteArrayOutputStream(Math.max(1024, rowBytes));
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try (DeflaterOutputStream z = new DeflaterOutputStream(compressed, deflater, 1 << 16)) {
            for (int y = 0; y < h; y++) {
                int o = 0;
                int row = y * w;
                for (int x = 0; x < w; x++) {
                    int i = row + x;
                    if (bytesPerSample == 1) {
                        raw[o++] = (byte) r[i];
                        raw[o++] = (byte) g[i];
                        raw[o++] = (byte) b[i];
                        if (channels == 4) raw[o++] = (byte) a[i];
                    } else {
                        raw[o++] = (byte) ((r[i] & 0xFFFF) >>> 8);
                        raw[o++] = (byte) r[i];
                        raw[o++] = (byte) ((g[i] & 0xFFFF) >>> 8);
                        raw[o++] = (byte) g[i];
                        raw[o++] = (byte) ((b[i] & 0xFFFF) >>> 8);
                        raw[o++] = (byte) b[i];
                        if (channels == 4) {
                            raw[o++] = (byte) ((a[i] & 0xFFFF) >>> 8);
                            raw[o++] = (byte) a[i];
                        }
                    }
                }

                int bestFilter = 0;
                long bestScore = Long.MAX_VALUE;
                for (int f = 0; f <= 4; f++) {
                    filter(f, raw, prev, candidate, rowBytes, bpp);
                    long score = 0;
                    for (int i = 0; i < rowBytes; i++) {
                        int v = candidate[i];
                        score += v < 0 ? -v : v;
                        if (score >= bestScore) break;
                    }
                    if (score < bestScore) {
                        bestScore = score;
                        bestFilter = f;
                        System.arraycopy(candidate, 0, best, 0, rowBytes);
                    }
                }
                filtered[0] = (byte) bestFilter;
                System.arraycopy(best, 0, filtered, 1, rowBytes);
                z.write(filtered);
                System.arraycopy(raw, 0, prev, 0, rowBytes);
            }
        }

        try (OutputStream out = new FileOutputStream(file)) {
            out.write(SIGNATURE);
            ByteArrayOutputStream header = new ByteArrayOutputStream(13);
            DataOutputStream data = new DataOutputStream(header);
            data.writeInt(w);
            data.writeInt(h);
            data.writeByte(depth);
            data.writeByte(channels == 4 ? 6 : 2);
            data.writeByte(0);
            data.writeByte(0);
            data.writeByte(0);
            chunk(out, "IHDR", header.toByteArray());
            chunk(out, "IDAT", compressed.toByteArray());
            chunk(out, "IEND", new byte[0]);
        }
    }

    private static void filter(int type, byte[] raw, byte[] prev, byte[] out, int n, int bpp) {
        switch (type) {
            case 0:
                System.arraycopy(raw, 0, out, 0, n);
                break;
            case 1:
                for (int i = 0; i < n; i++) {
                    int left = i >= bpp ? raw[i - bpp] & 0xFF : 0;
                    out[i] = (byte) ((raw[i] & 0xFF) - left);
                }
                break;
            case 2:
                for (int i = 0; i < n; i++) {
                    out[i] = (byte) ((raw[i] & 0xFF) - (prev[i] & 0xFF));
                }
                break;
            case 3:
                for (int i = 0; i < n; i++) {
                    int left = i >= bpp ? raw[i - bpp] & 0xFF : 0;
                    int up = prev[i] & 0xFF;
                    out[i] = (byte) ((raw[i] & 0xFF) - ((left + up) >>> 1));
                }
                break;
            default:
                for (int i = 0; i < n; i++) {
                    int left = i >= bpp ? raw[i - bpp] & 0xFF : 0;
                    int up = prev[i] & 0xFF;
                    int upLeft = i >= bpp ? prev[i - bpp] & 0xFF : 0;
                    out[i] = (byte) ((raw[i] & 0xFF) - paeth(left, up, upLeft));
                }
                break;
        }
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) return a;
        if (pb <= pc) return b;
        return c;
    }

    private static void chunk(OutputStream out, String type, byte[] data) throws IOException {
        byte[] name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        DataOutputStream header = new DataOutputStream(out);
        header.writeInt(data.length);
        out.write(name);
        out.write(data);
        header.writeInt((int) crc.getValue());
        header.flush();
    }
}
