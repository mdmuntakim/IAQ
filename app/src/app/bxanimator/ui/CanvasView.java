package app.bxanimator.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import app.bxanimator.BxApp;
import app.bxanimator.core.AnimMath;
import app.bxanimator.core.Bone;
import app.bxanimator.core.Easing;
import app.bxanimator.core.Layer;
import app.bxanimator.core.LayerType;
import app.bxanimator.core.Mat2D;
import app.bxanimator.core.Pose;
import app.bxanimator.core.PoseSolver;
import app.bxanimator.core.Project;
import app.bxanimator.core.Scene;
import app.bxanimator.core.SceneSampler;
import app.bxanimator.core.Stroke;
import app.bxanimator.core.Track;
import app.bxanimator.render.SceneRenderer;
import app.bxanimator.render.ShaderPipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * The animation viewport. One finger poses bones, moves layers, paints or flies
 * the camera; two fingers zoom the view or the camera; double tap fits.
 */
public final class CanvasView extends View {

    public enum Tool {
        SELECT("Select", "Drag to move, corner to scale, top handle to rotate"),
        POSE("Pose", "Drag any limb to rotate it. A key is recorded at the playhead"),
        DRAW("Draw", "Draw on the selected drawing layer"),
        CAMERA("Camera", "Drag to pan, pinch to zoom, twist to roll the camera");

        public final String label;
        public final String hint;

        Tool(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }

    public interface Listener {
        void onSelect(Layer layer);

        void onBeforeEdit();

        void onEdited();

        void onMessage(String message);

        void onRequestDrawingLayer();

        void onBoneSelected(Bone bone);
    }

    private static final int NONE = 0;
    private static final int MOVE = 1;
    private static final int ROTATE = 2;
    private static final int SCALE = 3;
    private static final int VIEW_GESTURE = 4;
    private static final int POSE = 5;
    private static final int PAINT = 6;
    private static final int CAMERA_GESTURE = 7;

    private final SceneRenderer renderer = new SceneRenderer();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = Ui.fill(0x88000000);
    private final Paint framePaint = Ui.stroke(0xFFFFFFFF, 1.4f);
    private final Paint accentPaint = Ui.stroke(0xFFFFFFFF, 1.6f);
    private final Paint gridPaint = Ui.stroke(0x33FFFFFF, 1f);
    private final Paint handleFill = Ui.fill(0xFFFFFFFF);
    private final Paint handleStroke = Ui.stroke(0xFF000000, 1.2f);
    private final Paint bonePaint = Ui.stroke(0x88FFB347, 2.4f);
    private final Paint boneActivePaint = Ui.stroke(0xFFFFC857, 4f);
    private final Paint jointPaint = Ui.fill(0xCCFF7A18);
    private final Paint hudBg = Ui.fill(0xCC101620);
    private final Paint hudText;
    private final Paint hudDim;
    private final Paint shadow = Ui.fill(0x66000000);
    private final RectF rect = new RectF();
    private final RectF dstRect = new RectF();
    private final RectF frameRect = new RectF();
    private final RectF gizmoRect = new RectF();
    private final RectF localRect = new RectF();
    private final Matrix camera = new Matrix();
    private final Matrix cameraInverse = new Matrix();
    private final Matrix tmpMatrix = new Matrix();
    private final Matrix layerMatrix = new Matrix();
    private final Path dashPath = new Path();
    private final Paint dashPaint = Ui.stroke(0xFFFF7A18, 1.6f);
    private final float[] point = new float[2];

    private Project project;
    private Scene scene;
    private double playhead;
    private boolean playing;
    private Tool tool = Tool.SELECT;
    private Layer selected;
    private String selectedBoneId;
    private Listener listener;

    private float viewZoom = 1f;
    private float panX;
    private float panY;
    private boolean onion;
    private boolean fxEnabled = true;
    private boolean grid;
    private int quality = 1;

    private Bitmap buffer;
    private Canvas bufferCanvas;
    private boolean dirty = true;
    private boolean fitted;
    private SceneSampler.SampledScene sampled;

    private int mode = NONE;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private float startX;
    private float startY;
    private float startRotation;
    private float startScale;
    private float startDistance;
    private float startViewZoom;
    private float startPanX;
    private float startPanY;
    private float startCamX;
    private float startCamY;
    private float startCamScale;
    private float startCamRotation;
    private float startAngle;
    private float pointerStart;
    private float boneStart;
    private float editDx;
    private float editDy;
    private float startLayerX;
    private float startLayerY;
    private Bone dragBone;
    private Stroke stroke;
    private boolean editStarted;
    private boolean moved;
    private long downTime;
    private long lastTapTime;
    private float lastTapX;
    private float lastTapY;
    private float cursorX = -1;
    private float cursorY = -1;

    private int brushColor = 0xFF101018;
    private float brushWidth = 8f;
    private boolean eraser;

    public CanvasView(Context context) {
        super(context);
        hudText = Ui.text(0xFFF2F5FA, Ui.sp(10.5f), true);
        hudDim = Ui.text(0xFFB7C0CF, Ui.sp(9.5f), false);
        dashPaint.setPathEffect(new DashPathEffect(new float[]{Ui.dp(5), Ui.dp(4)}, 0));
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setFocusable(true);
    }

    // ------------------------------------------------------------ state

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setProject(Project project) {
        this.project = project;
        dirty = true;
        invalidate();
    }

    public void setScene(Scene scene) {
        this.scene = scene;
        dirty = true;
        invalidate();
    }

    public Scene getScene() {
        return scene;
    }

    public void setPlayhead(double time) {
        this.playhead = Math.max(0, time);
        dirty = true;
        invalidate();
    }

    public double getPlayhead() {
        return playhead;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
        invalidate();
    }

    public void setSelected(Layer layer) {
        this.selected = layer;
        this.selectedBoneId = null;
        dirty = true;
        invalidate();
    }

    public Layer getSelected() {
        return selected;
    }

    public String getSelectedBoneId() {
        return selectedBoneId;
    }

    public void setTool(Tool tool) {
        this.tool = tool;
        cursorX = -1;
        dirty = true;
        invalidate();
    }

    public Tool getTool() {
        return tool;
    }

    public void setOnion(boolean onion) {
        this.onion = onion;
        dirty = true;
        invalidate();
    }

    public boolean isOnion() {
        return onion;
    }

    public void setFxEnabled(boolean enabled) {
        this.fxEnabled = enabled;
        dirty = true;
        invalidate();
    }

    public boolean isFxEnabled() {
        return fxEnabled;
    }

    public void setQuality(int quality) {
        this.quality = Math.max(0, Math.min(2, quality));
        dirty = true;
        invalidate();
    }

    public void setGrid(boolean grid) {
        this.grid = grid;
        invalidate();
    }

    public boolean isGrid() {
        return grid;
    }

    public void setBrush(int color, float width, boolean erase) {
        this.brushColor = color;
        this.brushWidth = width;
        this.eraser = erase;
        invalidate();
    }

    public int getBrushColor() {
        return brushColor;
    }

    public float getBrushWidth() {
        return brushWidth;
    }

    public boolean isEraser() {
        return eraser;
    }

    public void invalidateScene() {
        dirty = true;
        invalidate();
    }

    public void fit() {
        viewZoom = 1f;
        panX = 0;
        panY = 0;
        dirty = true;
        invalidate();
    }

    public void zoomBy(float factor) {
        viewZoom = AnimMath.clamp(viewZoom * factor, 0.2f, 8f);
        dirty = true;
        invalidate();
    }

    public float getViewZoom() {
        return viewZoom;
    }

    public void setAssetsDir(java.io.File dir) {
        renderer.setAssetsDir(dir);
    }

    // ------------------------------------------------------------ drawing

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buffer = null;
        bufferCanvas = null;
        dirty = true;
        if (!fitted && w > 0 && h > 0) {
            fitted = true;
            fit();
        }
    }

    private void ensureBuffer() {
        float scale = quality == 0 ? 0.55f : (quality == 1 ? 0.78f : 1f);
        int w = Math.max(2, Math.round(getWidth() * scale));
        int h = Math.max(2, Math.round(getHeight() * scale));
        if (buffer == null || buffer.getWidth() != w || buffer.getHeight() != h) {
            if (buffer != null) buffer.recycle();
            buffer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bufferCanvas = new Canvas(buffer);
            dirty = true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (project == null || scene == null) return;
        ensureBuffer();
        if (dirty) {
            renderBuffer();
            dirty = false;
        }
        if (buffer != null) {
            dstRect.set(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(buffer, null, dstRect, bitmapPaint);
        }
        drawOverlay(canvas);
    }

    private void renderBuffer() {
        if (buffer == null) return;
        float scale = buffer.getWidth() / (float) Math.max(1, getWidth());
        int bw = buffer.getWidth();
        int bh = buffer.getHeight();
        bufferCanvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
        sampled = SceneSampler.sample(scene, playhead);
        renderer.draw(bufferCanvas, project, scene, playhead, bw, bh, viewZoom,
                panX * scale, panY * scale, onion, 1.0 / Math.max(1, project.fps), 1f);
        if (fxEnabled) {
            ShaderPipeline.apply(buffer, scene.effects, (float) playhead);
        }
        camera.set(SceneRenderer.cameraMatrix(project, sampled, getWidth(), getHeight(), viewZoom, panX, panY));
        cameraInverse.set(camera);
        cameraInverse.invert(cameraInverse);
    }

    private void drawOverlay(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        frameRect.set(0, 0, project.width, project.height);
        camera.mapRect(frameRect);
        dimPaint.setColor(0x77000000);
        if (frameRect.left > 0) canvas.drawRect(0, 0, frameRect.left, h, dimPaint);
        if (frameRect.right < w) canvas.drawRect(frameRect.right, 0, w, h, dimPaint);
        if (frameRect.top > 0) canvas.drawRect(Math.max(0, frameRect.left), 0, Math.min(w, frameRect.right), frameRect.top, dimPaint);
        if (frameRect.bottom < h) canvas.drawRect(Math.max(0, frameRect.left), frameRect.bottom, Math.min(w, frameRect.right), h, dimPaint);

        framePaint.setColor(0x99FFFFFF);
        framePaint.setStrokeWidth(Math.max(1f, Ui.dp(1)));
        canvas.drawRect(frameRect, framePaint);

        if (grid) {
            gridPaint.setColor(0x33FFFFFF);
            for (int i = 1; i <= 2; i++) {
                float x = frameRect.left + frameRect.width() * i / 3f;
                float y = frameRect.top + frameRect.height() * i / 3f;
                canvas.drawLine(x, frameRect.top, x, frameRect.bottom, gridPaint);
                canvas.drawLine(frameRect.left, y, frameRect.right, y, gridPaint);
            }
        }

        if (tool == Tool.SELECT && selected != null && selected.type != LayerType.CAMERA) {
            drawGizmo(canvas);
        }
        if (tool == Tool.POSE) {
            drawBones(canvas);
        }
        if (tool == Tool.CAMERA) {
            drawCameraOverlay(canvas);
        }
        if (tool == Tool.DRAW && cursorX >= 0) {
            float r = brushWidth * 0.5f * (camera.mapRadius(1f));
            Paint p = Ui.stroke(0xCCFFFFFF, 1.5f);
            canvas.drawCircle(cursorX, cursorY, Math.max(Ui.dp(3), r), p);
            canvas.drawCircle(cursorX, cursorY, Math.max(Ui.dp(3), r) + 1, Ui.stroke(0x88000000, 1f));
        }
        drawHud(canvas);
    }

    private void drawHud(Canvas canvas) {
        int w = getWidth();
        String time = AnimMath.timecode(playhead, project.fps);
        String label = scene.name + "  \u00b7  " + time;
        float pad = Ui.dp(9);
        float tw = hudText.measureText(label);
        rect.set(Ui.dp(10), Ui.dp(10), Ui.dp(10) + tw + pad * 2, Ui.dp(10) + Ui.dp(26));
        canvas.drawRoundRect(rect, Ui.dp(8), Ui.dp(8), hudBg);
        if (playing) {
            Paint dot = Ui.fill(0xFFFF4D6D);
            canvas.drawCircle(rect.left + pad, rect.centerY(), Ui.dp(3.2f), dot);
            canvas.drawText(label, rect.left + pad + Ui.dp(9), rect.centerY() + Ui.dp(3.5f), hudText);
        } else {
            canvas.drawText(label, rect.left + pad, rect.centerY() + Ui.dp(3.5f), hudText);
        }

        String zoom = Math.round(viewZoom * 100) + "%";
        float zw = hudDim.measureText(zoom);
        rect.set(w - Ui.dp(10) - zw - Ui.dp(18), Ui.dp(10), w - Ui.dp(10), Ui.dp(10) + Ui.dp(24));
        canvas.drawRoundRect(rect, Ui.dp(8), Ui.dp(8), hudBg);
        canvas.drawText(zoom, rect.left + Ui.dp(9), rect.centerY() + Ui.dp(3.2f), hudDim);

        String hint = tool.hint;
        if (tool == Tool.POSE && selectedBoneId != null) hint = "Bone: " + selectedBoneId;
        if (tool == Tool.DRAW && selected != null && selected.type == LayerType.DRAW) {
            hint = eraser ? "Eraser \u00b7 " + Math.round(brushWidth) + " px" : "Brush \u00b7 " + Math.round(brushWidth) + " px";
        }
        float hw = hudDim.measureText(hint);
        float hx = Ui.dp(10);
        float hy = getHeight() - Ui.dp(12) - Ui.dp(22);
        rect.set(hx, hy, hx + hw + Ui.dp(18), hy + Ui.dp(22));
        canvas.drawRoundRect(rect, Ui.dp(8), Ui.dp(8), hudBg);
        canvas.drawText(hint, rect.left + Ui.dp(9), rect.centerY() + Ui.dp(3.2f), hudDim);
    }

    private void drawGizmo(Canvas canvas) {
        SceneSampler.SampledLayer sl = sampled == null ? null : sampled.find(selected.id);
        if (sl == null) return;
        if (!screenBounds(sl, gizmoRect)) return;
        accentPaint.setColor(0xFFFFB347);
        accentPaint.setStrokeWidth(Ui.dp(1.5f));
        canvas.drawRect(gizmoRect, accentPaint);
        float hs = Ui.dp(4.5f);
        handleFill.setColor(0xFFFFB347);
        handleStroke.setColor(0xFF161018);
        for (float[] corner : new float[][]{
                {gizmoRect.left, gizmoRect.top}, {gizmoRect.right, gizmoRect.top},
                {gizmoRect.left, gizmoRect.bottom}, {gizmoRect.right, gizmoRect.bottom}}) {
            rect.set(corner[0] - hs, corner[1] - hs, corner[0] + hs, corner[1] + hs);
            canvas.drawRect(rect, handleFill);
            canvas.drawRect(rect, handleStroke);
        }
        // Rotate handle above the frame.
        float cx = gizmoRect.centerX();
        float cy = gizmoRect.top - Ui.dp(28);
        canvas.drawLine(cx, gizmoRect.top, cx, cy + Ui.dp(7), Ui.stroke(0xFFFFB347, Ui.dp(1.2f)));
        handleFill.setColor(0xFFFF7A18);
        canvas.drawCircle(cx, cy, Ui.dp(8), handleFill);
        canvas.drawCircle(cx, cy, Ui.dp(8), handleStroke);
        Icons.draw(canvas, "rotate", cx, cy, Ui.dp(10), 0xFF161018);

        // Name tag
        String name = selected.name + (selected.locked ? "  (locked)" : "");
        float tw = hudDim.measureText(name);
        rect.set(gizmoRect.left, gizmoRect.top - Ui.dp(24), gizmoRect.left + tw + Ui.dp(14), gizmoRect.top - Ui.dp(6));
        canvas.drawRoundRect(rect, Ui.dp(6), Ui.dp(6), hudBg);
        canvas.drawText(name, rect.left + Ui.dp(7), rect.bottom - Ui.dp(5), hudDim);
    }

    private void drawBones(Canvas canvas) {
        if (selected == null || selected.type != LayerType.RIG || selected.rig == null) {
            return;
        }
        SceneSampler.SampledLayer sl = sampled == null ? null : sampled.find(selected.id);
        if (sl == null || sl.pose == null) return;
        Matrix m = layerToScreen(sl);
        Pose pose = sl.pose;
        for (Bone bone : selected.rig.bones) {
            Mat2D bm = pose.of(bone.id);
            point[0] = bm.tx;
            point[1] = bm.ty;
            m.mapPoints(point);
            float x0 = point[0];
            float y0 = point[1];
            point[0] = bm.mapX(0, -bone.length);
            point[1] = bm.mapY(0, -bone.length);
            m.mapPoints(point);
            boolean active = bone.id.equals(selectedBoneId);
            canvas.drawLine(x0, y0, point[0], point[1], active ? boneActivePaint : bonePaint);
            jointPaint.setColor(active ? 0xFFFFC857 : 0xCCFF7A18);
            canvas.drawCircle(x0, y0, active ? Ui.dp(5) : Ui.dp(3.2f), jointPaint);
        }
    }

    private void drawCameraOverlay(Canvas canvas) {
        Layer cam = scene.camera();
        if (cam == null) return;
        float cx = cam.x(playhead);
        float cy = cam.y(playhead);
        point[0] = cx;
        point[1] = cy;
        camera.mapPoints(point);
        Paint cross = Ui.stroke(0xFF6EA8FE, Ui.dp(1.3f));
        canvas.drawLine(point[0] - Ui.dp(12), point[1], point[0] + Ui.dp(12), point[1], cross);
        canvas.drawLine(point[0], point[1] - Ui.dp(12), point[0], point[1] + Ui.dp(12), cross);
        canvas.drawCircle(point[0], point[1], Ui.dp(20), Ui.stroke(0x556EA8FE, Ui.dp(1f)));
        framePaint.setColor(0xAA6EA8FE);
        canvas.drawRect(frameRect, framePaint);
    }

    // ------------------------------------------------------------ geometry

    private Matrix layerToScreen(SceneSampler.SampledLayer sl) {
        tmpMatrix.set(camera);
        layerMatrix.setValues(new float[]{
                sl.scale, 0, sl.x,
                0, sl.scale, sl.y,
                0, 0, 1});
        tmpMatrix.preConcat(layerMatrix);
        return tmpMatrix;
    }

    private boolean screenBounds(SceneSampler.SampledLayer sl, RectF out) {
        if (sl == null) return false;
        if (!localBounds(sl, localRect)) return false;
        Matrix m = layerToScreen(sl);
        out.set(localRect);
        m.mapRect(out);
        out.inset(-Ui.dp(2), -Ui.dp(2));
        return true;
    }

    private boolean localBounds(SceneSampler.SampledLayer sl, RectF out) {
        Layer layer = sl.layer;
        switch (layer.type) {
            case SHAPE:
            case IMAGE: {
                float w = layer.shapeWidth > 1 ? layer.shapeWidth : 400;
                float h = layer.shapeHeight > 1 ? layer.shapeHeight : 400;
                out.set(-w / 2f, -h / 2f, w / 2f, h / 2f);
                return true;
            }
            case TEXT: {
                Paint p = Ui.text(Color.WHITE, layer.textSize, true);
                String[] lines = (layer.text == null ? "" : layer.text).split("\n", -1);
                float max = 1;
                for (String line : lines) max = Math.max(max, p.measureText(line));
                Paint.FontMetrics fm = p.getFontMetrics();
                float lh = (fm.descent - fm.ascent) * 1.08f;
                float total = lh * lines.length;
                out.set(-max / 2f, -total / 2f, max / 2f, total / 2f);
                return true;
            }
            case RIG: {
                if (layer.rig == null || sl.pose == null) return false;
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
                for (Bone b : layer.rig.bones) {
                    Mat2D m = sl.pose.of(b.id);
                    float hw = b.width * 0.5f;
                    float[] xs = {m.mapX(-hw, 0), m.mapX(hw, 0), m.mapX(-hw, -b.length), m.mapX(hw, -b.length)};
                    float[] ys = {m.mapY(-hw, 0), m.mapY(hw, 0), m.mapY(-hw, -b.length), m.mapY(hw, -b.length)};
                    for (int i = 0; i < 4; i++) {
                        minX = Math.min(minX, xs[i]);
                        maxX = Math.max(maxX, xs[i]);
                        minY = Math.min(minY, ys[i]);
                        maxY = Math.max(maxY, ys[i]);
                    }
                }
                if (minX > maxX) return false;
                out.set(minX, minY, maxX, maxY);
                return true;
            }
            case DRAW: {
                int frame = (int) Math.round(playhead * project.fps);
                List<Stroke> strokes = layer.strokesAt(frame);
                if (strokes.isEmpty()) {
                    out.set(-Ui.dp(30), -Ui.dp(30), Ui.dp(30), Ui.dp(30));
                    return true;
                }
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
                for (Stroke s : strokes) {
                    for (float[] p : s.points) {
                        minX = Math.min(minX, p[0] - s.width);
                        maxX = Math.max(maxX, p[0] + s.width);
                        minY = Math.min(minY, p[1] - s.width);
                        maxY = Math.max(maxY, p[1] + s.width);
                    }
                }
                if (minX > maxX) {
                    out.set(-Ui.dp(30), -Ui.dp(30), Ui.dp(30), Ui.dp(30));
                    return true;
                }
                out.set(minX, minY, maxX, maxY);
                return true;
            }
            default:
                return false;
        }
    }

    private Layer pickLayer(float x, float y) {
        if (scene == null) return null;
        List<Layer> layers = scene.layers;
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer layer = layers.get(i);
            if (layer.type == LayerType.CAMERA || !layer.visible || layer.locked) continue;
            SceneSampler.SampledLayer sl = sampled == null ? null : sampled.find(layer.id);
            if (sl == null) continue;
            Matrix inv = layerToScreen(sl);
            if (!inv.invert(inv)) continue;
            point[0] = x;
            point[1] = y;
            inv.mapPoints(point);
            if (localBounds(sl, localRect) && localRect.contains(point[0], point[1])) return layer;
        }
        return null;
    }

    private void toLayer(SceneSampler.SampledLayer sl, float x, float y, float[] out) {
        Matrix inv = layerToScreen(sl);
        if (!inv.invert(inv)) {
            out[0] = 0;
            out[1] = 0;
            return;
        }
        out[0] = x;
        out[1] = y;
        inv.mapPoints(out);
    }

    /** Keeps the screen matrix in sync even before the next frame is drawn. */
    private void updateCamera() {
        if (project == null || scene == null) return;
        sampled = SceneSampler.sample(scene, playhead);
        camera.set(SceneRenderer.cameraMatrix(project, sampled, getWidth(), getHeight(), viewZoom, panX, panY));
        cameraInverse.set(camera);
        cameraInverse.invert(cameraInverse);
    }

    private SceneSampler.SampledLayer sampledOf(Layer layer) {
        return sampled == null || layer == null ? null : sampled.find(layer.id);
    }

    // ------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (project == null || scene == null) return false;
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return onDown(x, y);
            case MotionEvent.ACTION_POINTER_DOWN:
                return onPointerDown(event);
            case MotionEvent.ACTION_MOVE:
                return onMove(event);
            case MotionEvent.ACTION_POINTER_UP: {
                int remaining = event.getActionIndex() == 0 ? 1 : 0;
                if (remaining < event.getPointerCount()) {
                    lastX = event.getX(remaining);
                    lastY = event.getY(remaining);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                return onUp(x, y);
            case MotionEvent.ACTION_CANCEL:
                finishGesture(false);
                return true;
            default:
                return true;
        }
    }

    private boolean onDown(float x, float y) {
        updateCamera();
        downX = lastX = startX = x;
        downY = lastY = startY = y;
        downTime = System.currentTimeMillis();
        moved = false;
        editStarted = false;
        mode = NONE;
        dragBone = null;
        if (tool == Tool.DRAW) {
            cursorX = x;
            cursorY = y;
        }
        if (tool == Tool.CAMERA) {
            Layer cam = scene.camera();
            if (cam != null) {
                startCamX = cam.x(playhead);
                startCamY = cam.y(playhead);
                startCamScale = cam.scale(playhead);
                startCamRotation = cam.rotation(playhead);
                mode = CAMERA_GESTURE;
            }
            invalidate();
            return true;
        }
        if (tool == Tool.POSE) {
            Layer rigLayer = selected != null && selected.type == LayerType.RIG ? selected : pickLayer(x, y);
            if (rigLayer != null && rigLayer.type == LayerType.RIG) {
                if (rigLayer != selected) {
                    selected = rigLayer;
                    if (listener != null) listener.onSelect(rigLayer);
                }
                SceneSampler.SampledLayer sl = sampledOf(rigLayer);
                if (sl != null && sl.pose != null) {
                    float scale = Math.max(0.0001f, camera.mapRadius(1f));
                    toLayer(sl, x, y, point);
                    Bone bone = PoseSolver.pick(rigLayer.rig, sl.pose, point[0], point[1], Ui.dp(22) / scale);
                    if (bone != null) {
                        dragBone = bone;
                        selectedBoneId = bone.id;
                        boneStart = sl.pose.angle(bone.id);
                        pointerStart = PoseSolver.pointerAngle(bone, sl.pose, point[0], point[1]);
                        mode = POSE;
                        if (listener != null) listener.onBoneSelected(bone);
                        invalidate();
                        return true;
                    }
                }
            }
            mode = VIEW_GESTURE;
            return true;
        }
        if (tool == Tool.DRAW) {
            Layer drawLayer = selected != null && selected.type == LayerType.DRAW ? selected : pickDrawLayer(x, y);
            if (drawLayer == null) {
                if (listener != null) listener.onRequestDrawingLayer();
                return true;
            }
            if (drawLayer != selected) {
                selected = drawLayer;
                if (listener != null) listener.onSelect(drawLayer);
            }
            SceneSampler.SampledLayer sl = sampledOf(drawLayer);
            if (sl != null) {
                if (listener != null) listener.onBeforeEdit();
                editStarted = true;
                toLayer(sl, x, y, point);
                stroke = drawLayer.addStroke((int) Math.round(playhead * project.fps), brushColor, brushWidth, eraser);
                stroke.addPoint(point[0], point[1]);
                mode = PAINT;
                dirty = true;
                invalidate();
            }
            return true;
        }

        // Select tool
        if (selected != null && selected.type != LayerType.CAMERA && gizmoRect.contains(x, y)) {
            SceneSampler.SampledLayer sl = sampledOf(selected);
            if (sl != null) {
                float cx = gizmoRect.centerX();
                float cy = gizmoRect.top - Ui.dp(28);
                if (Ui.distance(x, y, cx, cy) < Ui.dp(22)) {
                    mode = ROTATE;
                    startRotation = selected.rotation(playhead);
                    startAngle = angleTo(cx, cy, x, y);
                    return true;
                }
                if (Ui.distance(x, y, gizmoRect.right, gizmoRect.bottom) < Ui.dp(24)) {
                    mode = SCALE;
                    startScale = selected.scale(playhead);
                    startDistance = Math.max(Ui.dp(8), Ui.distance(gizmoRect.centerX(), gizmoRect.centerY(), x, y));
                    return true;
                }
                mode = MOVE;
                startLayerX = selected.x(playhead);
                startLayerY = selected.y(playhead);
                editDx = startLayerX;
                editDy = startLayerY;
                return true;
            }
        }
        Layer hit = pickLayer(x, y);
        if (hit != null) {
            if (hit != selected) {
                selected = hit;
                selectedBoneId = null;
                if (listener != null) listener.onSelect(hit);
            }
            mode = MOVE;
            startLayerX = hit.x(playhead);
            startLayerY = hit.y(playhead);
            editDx = startLayerX;
            editDy = startLayerY;
            dirty = true;
            invalidate();
            return true;
        }
        mode = VIEW_GESTURE;
        return true;
    }

    private Layer pickDrawLayer(float x, float y) {
        Layer hit = pickLayer(x, y);
        if (hit != null && hit.type == LayerType.DRAW) return hit;
        if (hit == null) {
            for (int i = scene.layers.size() - 1; i >= 0; i--) {
                Layer l = scene.layers.get(i);
                if (l.type == LayerType.DRAW && l.visible && !l.locked) return l;
            }
        }
        return null;
    }

    private boolean onPointerDown(MotionEvent event) {
        updateCamera();
        startDistance = Math.max(1f, Ui.distance(event.getX(0), event.getY(0), event.getX(1), event.getY(1)));
        startAngle = (float) Math.toDegrees(Math.atan2(event.getY(1) - event.getY(0), event.getX(1) - event.getX(0)));
        if (mode == PAINT) {
            finishGesture(true);
        }
        if (tool == Tool.CAMERA) {
            Layer cam = scene.camera();
            if (cam != null) {
                startCamX = cam.x(playhead);
                startCamY = cam.y(playhead);
                startCamScale = cam.scale(playhead);
                startCamRotation = cam.rotation(playhead);
            }
            mode = CAMERA_GESTURE;
            return true;
        }
        startViewZoom = viewZoom;
        startPanX = panX;
        startPanY = panY;
        startX = (event.getX(0) + event.getX(1)) / 2f;
        startY = (event.getY(0) + event.getY(1)) / 2f;
        mode = VIEW_GESTURE;
        return true;
    }

    private boolean onMove(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        if (event.getPointerCount() >= 2 && (mode == VIEW_GESTURE || mode == CAMERA_GESTURE)) {
            float dist = Math.max(1f, Ui.distance(event.getX(0), event.getY(0), event.getX(1), event.getY(1)));
            float midX = (event.getX(0) + event.getX(1)) / 2f;
            float midY = (event.getY(0) + event.getY(1)) / 2f;
            float angle = (float) Math.toDegrees(Math.atan2(event.getY(1) - event.getY(0), event.getX(1) - event.getX(0)));
            if (mode == CAMERA_GESTURE) {
                Layer cam = scene.camera();
                if (cam != null) {
                    float factor = dist / startDistance;
                    float newScale = AnimMath.clamp(startCamScale * factor, 0.08f, 12f);
                    if (!editStarted) {
                        if (listener != null) listener.onBeforeEdit();
                        editStarted = true;
                    }
                    setCameraValue(cam, Layer.SCALE, newScale, newScale - startCamScale);
                    setCameraValue(cam, Layer.ROT, startCamRotation - (angle - startAngle), -(angle - startAngle));
                    float scale = Math.max(0.0001f, camera.mapRadius(1f));
                    setCameraValue(cam, Layer.X, startCamX - (midX - startX) / scale, -(midX - startX) / scale);
                    setCameraValue(cam, Layer.Y, startCamY - (midY - startY) / scale, -(midY - startY) / scale);
                    dirty = true;
                    invalidate();
                }
                return true;
            }
            viewZoom = AnimMath.clamp(startViewZoom * (dist / startDistance), 0.2f, 8f);
            panX = startPanX + (midX - startX);
            panY = startPanY + (midY - startY);
            moved = true;
            dirty = true;
            invalidate();
            return true;
        }

        float dx = x - lastX;
        float dy = y - lastY;
        if (Math.abs(x - downX) > Ui.dp(3) || Math.abs(y - downY) > Ui.dp(3)) moved = true;
        lastX = x;
        lastY = y;
        if (tool == Tool.DRAW) {
            cursorX = x;
            cursorY = y;
        }
        switch (mode) {
            case MOVE: {
                if (selected == null || selected.locked) return true;
                SceneSampler.SampledLayer sl = sampledOf(selected);
                if (sl == null) return true;
                float scale = Math.max(0.0001f, camera.mapRadius(1f));
                editDx += dx / scale;
                editDy += dy / scale;
                float nx = editDx;
                float ny = editDy;
                float snap = Ui.dp(6) / scale;
                if (Math.abs(nx - project.width / 2f) < snap) nx = project.width / 2f;
                if (Math.abs(ny - project.height / 2f) < snap) ny = project.height / 2f;
                if (!editStarted) {
                    if (listener != null) listener.onBeforeEdit();
                    editStarted = true;
                }
                apply(selected, Layer.X, nx, nx - startLayerX);
                apply(selected, Layer.Y, ny, ny - startLayerY);
                dirty = true;
                invalidate();
                return true;
            }
            case ROTATE: {
                if (selected == null || selected.locked) return true;
                SceneSampler.SampledLayer sl = sampledOf(selected);
                if (sl == null) return true;
                float scale = Math.max(0.0001f, camera.mapRadius(1f));
                point[0] = sl.x;
                point[1] = sl.y;
                camera.mapPoints(point);
                float a = angleTo(point[0], point[1], x, y);
                float delta = AnimMath.deltaDegrees(startAngle, a);
                float value = startRotation + delta;
                if (!editStarted) {
                    if (listener != null) listener.onBeforeEdit();
                    editStarted = true;
                }
                apply(selected, Layer.ROT, value, delta);
                dirty = true;
                invalidate();
                return true;
            }
            case SCALE: {
                if (selected == null || selected.locked) return true;
                float dist = Math.max(Ui.dp(8), Ui.distance(gizmoRect.centerX(), gizmoRect.centerY(), x, y));
                float value = AnimMath.clamp(startScale * (dist / startDistance), 0.05f, 24f);
                if (!editStarted) {
                    if (listener != null) listener.onBeforeEdit();
                    editStarted = true;
                }
                apply(selected, Layer.SCALE, value, value - startScale);
                dirty = true;
                invalidate();
                return true;
            }
            case POSE: {
                if (selected == null || dragBone == null || selected.locked) return true;
                SceneSampler.SampledLayer sl = sampledOf(selected);
                if (sl == null || sl.pose == null) return true;
                float scale = Math.max(0.0001f, camera.mapRadius(1f));
                toLayer(sl, x, y, point);
                Pose livePose = PoseSolver.solve(selected.rig, selected, playhead);
                float now = PoseSolver.pointerAngle(dragBone, livePose, point[0], point[1]);
                float delta = AnimMath.deltaDegrees(pointerStart, now);
                float value = boneStart + delta;
                if (!editStarted) {
                    if (listener != null) listener.onBeforeEdit();
                    editStarted = true;
                }
                apply(selected, Layer.boneChannel(dragBone.id), value, delta);
                dirty = true;
                invalidate();
                return true;
            }
            case PAINT: {
                if (stroke == null || selected == null) return true;
                SceneSampler.SampledLayer sl = sampledOf(selected);
                if (sl == null) return true;
                toLayer(sl, x, y, point);
                stroke.addPoint(point[0], point[1]);
                dirty = true;
                invalidate();
                return true;
            }
            case CAMERA_GESTURE: {
                Layer cam = scene.camera();
                if (cam == null) return true;
                float scale = Math.max(0.0001f, camera.mapRadius(1f));
                float dxCam = -(x - downX) / scale;
                float dyCam = -(y - downY) / scale;
                if (!editStarted) {
                    if (listener != null) listener.onBeforeEdit();
                    editStarted = true;
                }
                setCameraValue(cam, Layer.X, startCamX + dxCam, dxCam);
                setCameraValue(cam, Layer.Y, startCamY + dyCam, dyCam);
                dirty = true;
                invalidate();
                return true;
            }
            case VIEW_GESTURE: {
                panX += dx;
                panY += dy;
                dirty = true;
                invalidate();
                return true;
            }
            default:
                return true;
        }
    }

    private boolean onUp(float x, float y) {
        int wasMode = mode;
        if (mode == PAINT) {
            finishGesture(true);
        } else if (mode == MOVE || mode == ROTATE || mode == SCALE || mode == POSE || mode == CAMERA_GESTURE) {
            finishGesture(true);
        } else {
            mode = NONE;
            if (!moved && tool == Tool.SELECT) {
                long now = System.currentTimeMillis();
                if (now - lastTapTime < 300 && Ui.distance(x, y, lastTapX, lastTapY) < Ui.dp(28)) {
                    fit();
                    lastTapTime = 0;
                    return true;
                }
                lastTapTime = now;
                lastTapX = x;
                lastTapY = y;
                if (selected != null && selected.type != LayerType.CAMERA && gizmoRect.contains(x, y)) {
                    return true;
                }
                Layer hit = pickLayer(x, y);
                if (hit != selected) {
                    selected = hit;
                    selectedBoneId = null;
                    if (listener != null) listener.onSelect(hit);
                    dirty = true;
                    invalidate();
                }
            }
        }
        if (wasMode == VIEW_GESTURE && !moved && tool != Tool.SELECT) {
            // A tap in pose/draw mode with no hit keeps the current selection.
        }
        cursorX = tool == Tool.DRAW ? -1 : cursorX;
        invalidate();
        return true;
    }

    private void finishGesture(boolean commit) {
        if (mode == PAINT) {
            stroke = null;
        }
        if (commit && editStarted && listener != null) listener.onEdited();
        editStarted = false;
        dragBone = null;
        mode = NONE;
    }

    private void apply(Layer layer, String channel, float value, float delta) {
        boolean auto = BxApp.get(getContext()).autoKey();
        if (auto) {
            layer.setKey(channel, playhead, value, Easing.LINEAR);
        } else {
            Track t = layer.trackOrNull(channel);
            if (t == null || t.isEmpty()) {
                layer.setKey(channel, 0, value, Easing.LINEAR);
            } else {
                t.offsetValues(delta);
            }
        }
        if (listener != null) listener.onEdited();
    }

    private void setCameraValue(Layer cam, String channel, float value, float delta) {
        boolean auto = BxApp.get(getContext()).autoKey();
        if (auto) {
            cam.setKey(channel, playhead, value, Easing.LINEAR);
        } else {
            Track t = cam.trackOrNull(channel);
            if (t == null || t.isEmpty()) cam.setKey(channel, 0, value, Easing.LINEAR);
            else t.offsetValues(delta);
        }
    }

    private static float angleTo(float cx, float cy, float x, float y) {
        return (float) Math.toDegrees(Math.atan2(y - cy, x - cx));
    }
}
