package app.bxanimator.remaster;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import app.bxanimator.core.Effect;
import app.bxanimator.core.EffectPresets;
import app.bxanimator.export.ExportCancelledException;
import app.bxanimator.export.Mp4Writer;
import app.bxanimator.io.MediaOut;
import app.bxanimator.render.ShaderPipeline;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Rebuilds an old clip frame by frame at a modern resolution and runs the
 * remaster shader stack on every frame. Frames come from the platform decoder
 * through MediaMetadataRetriever, so no codec specific setup is needed.
 */
public final class VideoRemaster {

    public static final class Options {
        public int height = 720;
        public int fps = 24;
        /** Seconds to remaster, 0 means the whole clip. */
        public float seconds = 30f;
        public List<Effect> effects = EffectPresets.build("modern-remaster");
    }

    public static final class Info {
        public long durationMs;
        public int width;
        public int height;
        public int rotation;
    }

    public interface Progress {
        void onProgress(float fraction, String label);

        boolean cancelled();
    }

    private VideoRemaster() {
    }

    public static Info probe(Context context, Uri uri) {
        Info info = new Info();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            info.durationMs = parseLong(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION), 0);
            info.width = (int) parseLong(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH), 0);
            info.height = (int) parseLong(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT), 0);
            info.rotation = (int) parseLong(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION), 0);
            if (info.rotation == 90 || info.rotation == 270) {
                int swap = info.width;
                info.width = info.height;
                info.height = swap;
            }
        } catch (Exception ignored) {
            // A broken clip simply shows zeroes in the picker.
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
        return info;
    }

    public static File remaster(Context context, Uri uri, Options options, Progress progress) throws Exception {
        Info info = probe(context, uri);
        if (info.width <= 0 || info.height <= 0) {
            throw new java.io.IOException("the video has no readable video track");
        }
        int outHeight = Math.max(240, options.height & ~1);
        int outWidth = Math.max(2, Math.round(outHeight * (float) info.width / info.height / 2f) * 2);
        if (outWidth > 1920) {
            float shrink = 1920f / outWidth;
            outWidth = 1920;
            outHeight = Math.max(2, Math.round(outHeight * shrink / 2f) * 2);
        }
        int fps = Math.max(1, options.fps);
        float available = info.durationMs / 1000f;
        float duration = options.seconds > 0 ? Math.min(options.seconds, available) : available;
        if (duration <= 0) duration = 1f;
        int totalFrames = Math.max(1, Math.round(duration * fps));

        File output = MediaOut.newExport(context, "remaster", "mp4");
        int bitrate = Math.max(2_000_000, Math.round(outWidth * outHeight * 4.5f * 0.35f));
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(context, uri);

        Mp4Writer writer = null;
        Bitmap canvasBitmap = null;
        try {
            writer = new Mp4Writer(output, outWidth, outHeight, fps, bitrate);
            canvasBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(canvasBitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            RectF dst = new RectF();
            for (int frameIndex = 0; frameIndex < totalFrames; frameIndex++) {
                if (progress != null && progress.cancelled()) throw new ExportCancelledException();
                long timeUs = (long) frameIndex * 1_000_000L / fps;
                Bitmap source = retriever.getFrameAtTime(timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST);
                canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC);
                if (source != null) {
                    Bitmap oriented = source;
                    if (info.rotation != 0) {
                        Matrix matrix = new Matrix();
                        matrix.postRotate(info.rotation);
                        try {
                            oriented = Bitmap.createBitmap(source, 0, 0,
                                    source.getWidth(), source.getHeight(), matrix, true);
                        } catch (Exception ignored) {
                            oriented = source;
                        }
                    }
                    float scale = Math.min(outWidth / (float) oriented.getWidth(),
                            outHeight / (float) oriented.getHeight());
                    float dw = oriented.getWidth() * scale;
                    float dh = oriented.getHeight() * scale;
                    dst.set((outWidth - dw) / 2f, (outHeight - dh) / 2f,
                            (outWidth + dw) / 2f, (outHeight + dh) / 2f);
                    canvas.drawBitmap(oriented, null, dst, paint);
                    if (oriented != source) oriented.recycle();
                    source.recycle();
                }
                ShaderPipeline.apply(canvasBitmap, options.effects, frameIndex / (float) fps);
                writer.writeFrame(canvasBitmap, timeUs);
                if (progress != null && (frameIndex % 3 == 0 || frameIndex == totalFrames - 1)) {
                    progress.onProgress((frameIndex + 1) / (float) totalFrames,
                            "Frame " + (frameIndex + 1) + " / " + totalFrames);
                }
            }
            writer.finish();
            writer = null;
            return output;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
            if (canvasBitmap != null) canvasBitmap.recycle();
            if (writer != null) {
                writer.abort();
                if (output.exists()) output.delete();
            }
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null) return fallback;
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Presets offered by the remaster screen, modern remaster first. */
    public static List<String> presetIds() {
        List<String> ids = new ArrayList<>();
        ids.add("modern-remaster");
        ids.add("clean-cel");
        ids.add("cinematic-night");
        ids.add("retro-tape");
        ids.add("soft-memory");
        ids.add("dream-bloom");
        return ids;
    }
}
