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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes every rendered frame as a PNG plus the project file into one zip. */
public final class FrameExporter {

    public interface Progress {
        void onProgress(float fraction, String label);

        boolean cancelled();
    }

    private FrameExporter() {
    }

    public static File exportZip(Context context, Project project, File assetsDir, int fps,
                                 float scale, Progress progress) throws Exception {
        int width = Math.max(2, Math.round(project.width * scale));
        int height = Math.max(2, Math.round(project.height * scale));
        File output = MediaOut.newExport(context, project.name + "-frames", "zip");

        SceneRenderer renderer = new SceneRenderer();
        renderer.setAssetsDir(assetsDir);

        int totalFrames = 0;
        for (Scene scene : project.scenes) {
            totalFrames += Math.max(1, (int) Math.round(scene.duration * fps));
        }

        Bitmap frame = null;
        ZipOutputStream zip = null;
        try {
            zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)));
            zip.putNextEntry(new ZipEntry("project.bxanim"));
            zip.write(project.toJsonString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("README.txt"));
            String readme = "BXAnimator PNG sequence\n"
                    + "Project: " + project.name + "\n"
                    + "Resolution: " + width + " x " + height + "\n"
                    + "Frame rate: " + fps + " fps\n"
                    + "Frames: " + totalFrames + "\n\n"
                    + "Import project.bxanim into BXAnimator to keep editing.\n";
            zip.write(readme.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

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
                    ShaderPipeline.apply(frame, scene.effects, (float) time);
                    frameIndex++;
                    zip.putNextEntry(new ZipEntry(String.format(java.util.Locale.US,
                            "frames/frame_%05d.png", frameIndex)));
                    frame.compress(Bitmap.CompressFormat.PNG, 100, zip);
                    zip.closeEntry();
                    if (progress != null && (frameIndex % 4 == 0 || frameIndex == totalFrames)) {
                        progress.onProgress(frameIndex / (float) Math.max(1, totalFrames),
                                "Frame " + frameIndex + " / " + totalFrames);
                    }
                }
            }
            zip.finish();
            zip.close();
            zip = null;
            return output;
        } catch (Exception e) {
            if (output.exists()) output.delete();
            throw e;
        } finally {
            if (frame != null) frame.recycle();
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
