package app.bxanimator.render;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.LruCache;

import app.bxanimator.core.AnimMath;
import app.bxanimator.core.Layer;
import app.bxanimator.core.LayerType;
import app.bxanimator.core.Mat2D;
import app.bxanimator.core.Project;
import app.bxanimator.core.Scene;
import app.bxanimator.core.SceneSampler;
import app.bxanimator.core.Stroke;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a scene into any canvas: the editor viewport and the exporter share it,
 * so the preview and the rendered file cannot drift apart.
 */
public final class SceneRenderer {

    private final RigPainter rigPainter = new RigPainter();
    private final Paint bgPaint = new Paint();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF frame = new RectF();
    private final Path shapePath = new Path();
    private final Map<Long, Path> strokePaths = new HashMap<>();
    private final LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };
    private File assetsDir;

    public SceneRenderer() {
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clearPaint.setStyle(Paint.Style.STROKE);
        clearPaint.setStrokeCap(Paint.Cap.ROUND);
        clearPaint.setStrokeJoin(Paint.Join.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        textOutline.setTextAlign(Paint.Align.CENTER);
        textOutline.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        textOutline.setStyle(Paint.Style.STROKE);
        textOutline.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setAssetsDir(File dir) {
        this.assetsDir = dir;
    }

    public void clearCache() {
        imageCache.evictAll();
        strokePaths.clear();
    }

    // ------------------------------------------------------------- camera

    public static Matrix cameraMatrix(Project project, SceneSampler.SampledScene sampled,
                                      float viewW, float viewH, float zoom, float panX, float panY) {
        float fit = Math.min(viewW / Math.max(1f, project.width), viewH / Math.max(1f, project.height));
        Matrix m = new Matrix();
        m.preTranslate(-sampled.camX, -sampled.camY);
        float s = sampled.camScale * fit;
        m.preScale(s, s);
        m.preRotate(-sampled.camRotation);
        m.preScale(zoom, zoom);
        m.preTranslate(viewW / 2f + panX, viewH / 2f + panY);
        return m;
    }

    public static void frameRect(Matrix camera, Project project, RectF out) {
        out.set(0, 0, project.width, project.height);
        camera.mapRect(out);
    }

    // ------------------------------------------------------------- drawing

    /**
     * Draws the scene at a time. {@code alpha} scales every layer (used by the
     * exporter and by onion skin), {@code onionStep} is the frame duration when
     * onion skin is enabled, 0 otherwise.
     */
    public void draw(Canvas canvas, Project project, Scene scene, double time,
                     float viewW, float viewH, float zoom, float panX, float panY,
                     boolean onion, double onionStep, float alpha) {
        SceneSampler.SampledScene sampled = SceneSampler.sample(scene, time);
        Matrix camera = cameraMatrix(project, sampled, viewW, viewH, zoom, panX, panY);
        frameRect(camera, project, frame);

        if (onion && onionStep > 0) {
            drawFrameAt(canvas, project, scene, Math.max(0, time - onionStep), camera, alpha * 0.20f, false);
            drawFrameAt(canvas, project, scene, time + onionStep, camera, alpha * 0.13f, false);
        }
        drawFrameAt(canvas, project, scene, time, camera, alpha, true);
    }

    private void drawFrameAt(Canvas canvas, Project project, Scene scene, double time, Matrix camera,
                             float alphaScale, boolean background) {
        SceneSampler.SampledScene sampled = SceneSampler.sample(scene, time);
        canvas.save();
        canvas.clipRect(frame);
        if (background) {
            bgPaint.setShader(new LinearGradient(0, frame.top, 0, frame.bottom,
                    scene.bgTop, scene.bgBottom, Shader.TileMode.CLAMP));
            bgPaint.setAlpha(Math.round(255 * AnimMath.clamp(alphaScale, 0f, 1f)));
            canvas.drawRect(frame, bgPaint);
        }
        canvas.concat(camera);
        for (SceneSampler.SampledLayer sl : sampled.layers) {
            drawLayer(canvas, sl, alphaScale, (int) Math.round(time * project.fps));
        }
        canvas.restore();
    }

    private void drawLayer(Canvas canvas, SceneSampler.SampledLayer sl, float alphaScale, int frameNumber) {
        Layer layer = sl.layer;
        if (!layer.visible) return;
        float opacity = AnimMath.clamp(sl.opacity * alphaScale, 0f, 1f);
        if (opacity <= 0.003f) return;
        canvas.save();
        Mat2D lm = Mat2D.compose(sl.x, sl.y, sl.rotation, sl.scale, sl.scale);
        matrix.setValues(new float[]{lm.a, lm.c, lm.tx, lm.b, lm.d, lm.ty, 0, 0, 1});
        canvas.concat(matrix);
        switch (layer.type) {
            case RIG:
                rigPainter.draw(canvas, layer.rig, sl.pose, sl.blink, sl.brow, sl.mouth, sl.look, opacity);
                break;
            case SHAPE:
                drawShape(canvas, layer, opacity);
                break;
            case TEXT:
                drawText(canvas, layer, opacity);
                break;
            case DRAW:
                drawStrokes(canvas, layer, opacity, frameNumber);
                break;
            case IMAGE:
                drawImage(canvas, layer, opacity);
                break;
            default:
                break;
        }
        canvas.restore();
    }

    private void drawShape(Canvas canvas, Layer layer, float opacity) {
        float w = layer.shapeWidth;
        float h = layer.shapeHeight;
        int color = withAlpha(layer.shapeFill, opacity);
        fill.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        line.setColor(withAlpha(darken(layer.shapeFill, 0.7f), opacity));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(2f, Math.min(w, h) * 0.012f));
        switch (layer.shape == null ? "rect" : layer.shape) {
            case "ellipse":
                canvas.drawOval(new RectF(-w / 2f, -h / 2f, w / 2f, h / 2f), fill);
                break;
            case "triangle":
                shapePath.reset();
                shapePath.moveTo(0, -h / 2f);
                shapePath.lineTo(w / 2f, h / 2f);
                shapePath.lineTo(-w / 2f, h / 2f);
                shapePath.close();
                canvas.drawPath(shapePath, fill);
                break;
            case "star":
            case "burst":
                shapePath.reset();
                int points = layer.shape.equals("burst") ? 14 : 5;
                float outer = Math.min(w, h) / 2f;
                float inner = outer * (layer.shape.equals("burst") ? 0.62f : 0.42f);
                for (int i = 0; i < points * 2; i++) {
                    double a = Math.PI * i / points - Math.PI / 2;
                    float r = i % 2 == 0 ? outer : inner;
                    float x = (float) Math.cos(a) * r;
                    float y = (float) Math.sin(a) * r;
                    if (i == 0) shapePath.moveTo(x, y);
                    else shapePath.lineTo(x, y);
                }
                shapePath.close();
                canvas.drawPath(shapePath, fill);
                break;
            case "line":
                line.setStrokeWidth(Math.max(2f, h));
                line.setColor(color);
                canvas.drawLine(-w / 2f, 0, w / 2f, 0, line);
                break;
            case "frame":
                line.setStrokeWidth(Math.max(2f, h * 0.1f));
                line.setColor(color);
                canvas.drawRoundRect(new RectF(-w / 2f, -h / 2f, w / 2f, h / 2f), h * 0.12f, h * 0.12f, line);
                break;
            case "ring":
                line.setStrokeWidth(Math.max(2f, h * 0.12f));
                line.setColor(color);
                canvas.drawOval(new RectF(-w / 2f, -h / 2f, w / 2f, h / 2f), line);
                break;
            case "rect":
            default:
                float radius = Math.min(w, h) * 0.04f;
                canvas.drawRoundRect(new RectF(-w / 2f, -h / 2f, w / 2f, h / 2f), radius, radius, fill);
                break;
        }
    }

    private void drawText(Canvas canvas, Layer layer, float opacity) {
        String content = layer.text == null ? "" : layer.text;
        if (content.isEmpty()) return;
        textPaint.setTextSize(layer.textSize);
        textOutline.setTextSize(layer.textSize);
        textOutline.setStrokeWidth(Math.max(2f, layer.textSize * 0.11f));
        textPaint.setColor(withAlpha(layer.color, opacity));
        textOutline.setColor(withAlpha(0xCC0A0C12, opacity * 0.8f));
        String[] lines = content.split("\n", -1);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float lineHeight = (fm.descent - fm.ascent) * 1.08f;
        float total = lineHeight * lines.length;
        float y = -total / 2f + (lineHeight - (fm.descent - fm.ascent)) / 2f - fm.ascent;
        for (String text : lines) {
            canvas.drawText(text, 0, y, textOutline);
            canvas.drawText(text, 0, y, textPaint);
            y += lineHeight;
        }
    }

    private void drawStrokes(Canvas canvas, Layer layer, float opacity, int frame) {
        List<Stroke> strokes = layer.strokesAt(frame);
        if (strokes.isEmpty()) return;
        boolean hasErase = false;
        for (Stroke s : strokes) {
            if (s.erase) {
                hasErase = true;
                break;
            }
        }
        if (hasErase) canvas.saveLayer(null, null);
        for (Stroke s : strokes) {
            if (s.points.size() < 2) continue;
            Path path = pathFor(s);
            if (path == null) continue;
            Paint paint = s.erase ? clearPaint : strokePaint;
            paint.setStrokeWidth(s.width);
            if (!s.erase) paint.setColor(withAlpha(s.color, opacity));
            canvas.drawPath(path, paint);
        }
        if (hasErase) canvas.restore();
    }

    private Path pathFor(Stroke stroke) {
        long key = ((long) System.identityHashCode(stroke) << 20) | (stroke.points.size() & 0xFFFFF);
        Path cached = strokePaths.get(key);
        if (cached != null) return cached;
        if (strokePaths.size() > 3000) strokePaths.clear();
        Path path = new Path();
        List<float[]> pts = stroke.points;
        path.moveTo(pts.get(0)[0], pts.get(0)[1]);
        for (int i = 1; i < pts.size(); i++) {
            float[] p = pts.get(i);
            path.lineTo(p[0], p[1]);
        }
        strokePaths.put(key, path);
        return path;
    }

    private void drawImage(Canvas canvas, Layer layer, float opacity) {
        if (layer.asset == null) return;
        Bitmap bmp = image(layer.asset);
        if (bmp == null) return;
        float w = layer.shapeWidth > 1 ? layer.shapeWidth : bmp.getWidth();
        float h = layer.shapeHeight > 1 ? layer.shapeHeight : bmp.getHeight();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setAlpha(Math.round(255 * opacity));
        p.setFilterBitmap(true);
        RectF dst = new RectF(-w / 2f, -h / 2f, w / 2f, h / 2f);
        canvas.drawBitmap(bmp, null, dst, p);
    }

    private Bitmap image(String asset) {
        Bitmap cached = imageCache.get(asset);
        if (cached != null) return cached;
        File file = new File(asset);
        if (!file.isAbsolute() && assetsDir != null) file = new File(assetsDir, asset);
        if (!file.isFile()) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bmp != null) imageCache.put(asset, bmp);
        return bmp;
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(Color.alpha(color) * AnimMath.clamp(alpha, 0f, 1f));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static int darken(int color, float factor) {
        int r = Math.min(255, (int) (Color.red(color) * factor));
        int g = Math.min(255, (int) (Color.green(color) * factor));
        int b = Math.min(255, (int) (Color.blue(color) * factor));
        return Color.argb(Color.alpha(color), r, g, b);
    }
}
