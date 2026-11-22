package app.bxanimator.export;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;

import app.bxanimator.core.Project;
import app.bxanimator.core.Scene;
import app.bxanimator.io.MediaOut;
import app.bxanimator.render.SceneRenderer;
import app.bxanimator.render.ShaderPipeline;

import java.io.File;

/** Renders the whole project into an MP4 with the same engine as the preview. */
public final class VideoExporter {

    public static final class Options {
        public int width = 1280;
        public int height = 720;
        public int fps = 24;
        public int bitrate = 6_000_000;
        public boolean effects = true;
    }

    public interface Progress {
        void onProgress(float fraction, String label);

        boolean cancelled();
    }

    private VideoExporter() {
    }

    public static File export(Context context, Project project, File assetsDir,
                              Options options, Progress progress) throws Exception {
        int width = Math.max(2, options.width & ~1);
        int height = Math.max(2, options.height & ~1);
        int fps = Math.max(1, options.fps);
        File output = MediaOut.newExport(context, project.name, "mp4");

        SceneRenderer renderer = new SceneRenderer();
        renderer.setAssetsDir(assetsDir);

        int totalFrames = 0;
        for (Scene scene : project.scenes) {
            totalFrames += Math.max(1, (int) Math.round(scene.duration * fps));
        }

        Mp4Writer writer = null;
        Bitmap frame = null;
        try {
            writer = new Mp4Writer(output, width, height, fps, options.bitrate);
            frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(frame);
            int frameIndex = 0;
            for (Scene scene : project.scenes) {
                int frames = Math.max(1, (int) Math.round(scene.duration * fps));
                for (int f = 0; f < frames; f++) {
                    if (progress != null && progress.cancelled()) throw new ExportCancelledException();
                    double time = (double) f / fps;
                    canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC);
                    renderer.draw(canvas, project, scene, time, width, height, 1f, 0, 0,
                            false, 0, 1f);
                    if (options.effects) ShaderPipeline.apply(frame, scene.effects, (float) time);
                    long pts = (long) frameIndex * 1_000_000L / fps;
                    writer.writeFrame(frame, pts);
                    frameIndex++;
                    if (progress != null && (frameIndex % 4 == 0 || frameIndex == totalFrames)) {
                        progress.onProgress(frameIndex / (float) Math.max(1, totalFrames),
                                "Frame " + frameIndex + " / " + totalFrames);
                    }
                }
            }
            writer.finish();
            writer = null;
            return output;
        } finally {
            if (frame != null) frame.recycle();
            if (writer != null) {
                writer.abort();
                if (output.exists()) output.delete();
            }
        }
    }
}
