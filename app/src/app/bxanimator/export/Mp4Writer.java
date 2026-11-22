package app.bxanimator.export;

import android.graphics.Bitmap;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A small H.264 writer: MediaCodec encodes, MediaMuxer wraps the samples into
 * an MP4. Frames are pushed as YUV420 from an ARGB bitmap, so no OpenGL or
 * vendor specific input surface is required.
 */
public final class Mp4Writer {

    private static final String MIME = "video/avc";
    private static final long TIMEOUT_US = 20_000;

    private final int width;
    private final int height;
    private final MediaCodec codec;
    private final MediaMuxer muxer;
    private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    private boolean muxerStarted;
    private int track = -1;
    private long lastPts;
    private boolean finished;

    public Mp4Writer(File output, int width, int height, int fps, int bitrate) throws IOException {
        this.width = Math.max(2, width & ~1);
        this.height = Math.max(2, height & ~1);
        if (output.getParentFile() != null && !output.getParentFile().exists()) output.getParentFile().mkdirs();
        muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        MediaFormat format = MediaFormat.createVideoFormat(MIME, this.width, this.height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        format.setInteger(MediaFormat.KEY_BIT_RATE, Math.max(500_000, bitrate));
        format.setInteger(MediaFormat.KEY_FRAME_RATE, Math.max(1, fps));
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        codec = MediaCodec.createEncoderByType(MIME);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void writeFrame(Bitmap bitmap, long presentationTimeUs) throws IOException {
        if (finished) throw new IOException("writer already finished");
        Bitmap source = bitmap;
        if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
            source = Bitmap.createScaledBitmap(bitmap, width, height, true);
        }
        feed(source, presentationTimeUs);
        if (source != bitmap) source.recycle();
        lastPts = presentationTimeUs;
        drain(false);
    }

    private void feed(Bitmap bitmap, long pts) throws IOException {
        int index = codec.dequeueInputBuffer(TIMEOUT_US);
        if (index < 0) {
            drain(true);
            index = codec.dequeueInputBuffer(TIMEOUT_US * 4);
        }
        if (index < 0) throw new IOException("the encoder is not accepting frames");
        Image image = codec.getInputImage(index);
        if (image == null) {
            codec.queueInputBuffer(index, 0, 0, pts, 0);
            throw new IOException("the encoder did not provide an input image");
        }
        fill(image, bitmap);
        codec.queueInputBuffer(index, 0, width * height * 3 / 2, pts, 0);
    }

    private void fill(Image image, Bitmap bitmap) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] argb = new int[w * h];
        bitmap.getPixels(argb, 0, w, 0, 0, w, h);
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yPlane = planes[0].getBuffer();
        int yRow = planes[0].getRowStride();
        int yPix = planes[0].getPixelStride();
        ByteBuffer uPlane = planes[1].getBuffer();
        int uRow = planes[1].getRowStride();
        int uPix = planes[1].getPixelStride();
        ByteBuffer vPlane = planes[2].getBuffer();
        int vRow = planes[2].getRowStride();
        int vPix = planes[2].getPixelStride();

        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int c = argb[row + x];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                int luma = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                yPlane.put(y * yRow + x * yPix, (byte) clamp(luma));
            }
        }
        int cw = w / 2;
        int ch = h / 2;
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                int r = 0;
                int g = 0;
                int b = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int px = Math.min(w - 1, x * 2 + dx);
                        int py = Math.min(h - 1, y * 2 + dy);
                        int c = argb[py * w + px];
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                    }
                }
                r /= 4;
                g /= 4;
                b /= 4;
                int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                uPlane.put(y * uRow + x * uPix, (byte) clamp(u));
                vPlane.put(y * vRow + x * vPix, (byte) clamp(v));
            }
        }
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }

    private void drain(boolean endOfStream) throws IOException {
        while (true) {
            int index = codec.dequeueOutputBuffer(bufferInfo, endOfStream ? TIMEOUT_US * 5 : 0);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) return;
                continue;
            }
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    track = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                }
                continue;
            }
            if (index < 0) continue;
            ByteBuffer buffer = codec.getOutputBuffer(index);
            if (buffer != null && bufferInfo.size > 0
                    && (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    && muxerStarted) {
                buffer.position(bufferInfo.offset);
                buffer.limit(bufferInfo.offset + bufferInfo.size);
                muxer.writeSampleData(track, buffer, bufferInfo);
            }
            boolean eos = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            codec.releaseOutputBuffer(index, false);
            if (eos) return;
        }
    }

    public void finish() throws IOException {
        if (finished) return;
        finished = true;
        try {
            int index = codec.dequeueInputBuffer(TIMEOUT_US * 5);
            if (index >= 0) {
                codec.queueInputBuffer(index, 0, 0, lastPts + 1000,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
            drain(true);
        } finally {
            try {
                codec.stop();
            } catch (Exception ignored) {
            }
            codec.release();
            try {
                if (muxerStarted) muxer.stop();
            } finally {
                muxer.release();
            }
        }
    }

    public void abort() {
        finished = true;
        try {
            codec.stop();
        } catch (Exception ignored) {
        }
        codec.release();
        try {
            if (muxerStarted) muxer.stop();
        } catch (Exception ignored) {
        }
        muxer.release();
    }
}
