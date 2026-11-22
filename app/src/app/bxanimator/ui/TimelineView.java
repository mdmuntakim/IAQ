package app.bxanimator.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import app.bxanimator.core.AnimMath;
import app.bxanimator.core.Easing;
import app.bxanimator.core.Key;
import app.bxanimator.core.Layer;
import app.bxanimator.core.LayerType;
import app.bxanimator.core.Project;
import app.bxanimator.core.Scene;
import app.bxanimator.core.Stroke;
import app.bxanimator.core.Track;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The timeline: layer rows, expandable channels, draggable keyframes, a ruler
 * and a compact toolbar. One finger scrubs, dragging a key retimes it, pinch
 * zooms the time axis.
 */
public final class TimelineView extends View {

    public interface Listener {
        void onSelectLayer(Layer layer);

        void onKeySelected(Layer layer, String channel, double time);

        void onBeforeEdit();

        void onEdited();

        void onTogglePlay();

        void onAddKey();

        void onDeleteKey();

        void onEasing();

        void onToggleAutoKey();

        void onToggleOnion();

        void onScrub(double time);

        void onMessage(String message);
    }

    private static final class Row {
        Layer layer;
        String channel;
        String label;
        int top;
        int height;
        List<Double> times = new ArrayList<>();
        boolean strokeRow;
    }

    private final Paint bg = new Paint();
    private final Paint panel = new Paint();
    private final Paint border = new Paint();
    private final Paint textPaint;
    private final Paint dimPaint;
    private final Paint smallPaint;
    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyStroke = Ui.stroke(0xFF10141C, 1.2f);
    private final Paint playheadPaint = Ui.stroke(0xFFFF4D6D, 1.6f);
    private final Paint rowPaint = new Paint();
    private final Paint selPaint = new Paint();
    private final Paint toolbarPaint = new Paint();
    private final Path diamond = new Path();
    private final Path playTriangle = new Path();
    private final RectF rect = new RectF();
    private final List<Row> rows = new ArrayList<>();
    private final List<RectF> toolbarRects = new ArrayList<>();
    private final List<String> toolbarIds = new ArrayList<>();
    private final Set<String> expanded = new LinkedHashSet<>();

    private Project project;
    private Scene scene;
    private Layer selectedLayer;
    private String selectedChannel;
    private double selectedKeyTime = -1;
    private double playhead;
    private boolean playing;
    private boolean autoKey = true;
    private boolean onion;
    private Listener listener;

    private float headerWidth;
    private float toolbarHeight;
    private float rulerHeight;
    private float pxPerSecond = 80f;
    private double viewStart;
    private float scrollY;
    private float maxScroll;

    private int pressedToolbar = -1;
    private boolean scrubbing;
    private boolean draggingKey;
    private boolean draggingPlayhead;
    private boolean scrolling;
    private boolean moved;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private float dragStartTime;
    private Row dragRow;
    private double dragKeyTime;
    private float pinchStartDistance;
    private float pinchStartPx;
    private double pinchStartView;
    private float pinchMidX;
    private boolean pinch;
    private boolean editStarted;
    private long lastTapTime;
    private double lastTapTimeValue;
    private String toast;

    public TimelineView(Context context) {
        super(context);
        textPaint = Ui.text(0xFFE8EDF6, Ui.sp(11), false);
        dimPaint = Ui.text(0xFF8A94A6, Ui.sp(9.5f), false);
        smallPaint = Ui.text(0xFF8A94A6, Ui.sp(8.5f), false);
        headerWidth = Ui.dp(148);
        toolbarHeight = Ui.dp(34);
        rulerHeight = Ui.dp(20);
        setFocusable(true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setState(Project project, Scene scene, Layer selectedLayer, double playhead,
                         boolean playing, boolean autoKey, boolean onion) {
        boolean sceneChanged = this.scene != scene;
        this.project = project;
        this.scene = scene;
        this.selectedLayer = selectedLayer;
        this.playhead = playhead;
        this.playing = playing;
        this.autoKey = autoKey;
        this.onion = onion;
        if (sceneChanged) {
            expanded.clear();
            if (selectedLayer != null && needsExpand(selectedLayer)) expanded.add(selectedLayer.id);
            fitTime();
        }
        rebuildRows();
        invalidate();
    }

    public void setPlayhead(double time) {
        this.playhead = time;
        invalidate();
    }

    public void setSelectedKey(Layer layer, String channel, double time) {
        this.selectedLayer = layer;
        this.selectedChannel = channel;
        this.selectedKeyTime = time;
        if (channel != null && layer != null) expanded.add(layer.id);
        rebuildRows();
        invalidate();
    }

    public Layer getSelectedLayer() {
        return selectedLayer;
    }

    public String getSelectedChannel() {
        return selectedChannel;
    }

    public double getSelectedKeyTime() {
        return selectedKeyTime;
    }

    public boolean hasKeySelection() {
        return selectedLayer != null && selectedKeyTime >= 0 && selectedChannel != null;
    }

    public void refresh() {
        rebuildRows();
        invalidate();
    }

    public void fitTime() {
        if (scene == null) return;
        float usable = Math.max(1, getWidth() - headerWidth - Ui.dp(14));
        pxPerSecond = usable / (float) Math.max(0.5, scene.duration);
        viewStart = 0;
        invalidate();
    }

    public void zoomTime(float factor) {
        float center = headerWidth + (getWidth() - headerWidth) / 2f;
        double centerTime = xToTime(center);
        pxPerSecond = AnimMath.clamp(pxPerSecond * factor, 6f, 900f);
        viewStart = centerTime - (center - headerWidth) / pxPerSecond;
        clampView();
        invalidate();
    }

    public void setToolbarMessage(String message) {
        this.toast = message;
        invalidate();
    }

    // ------------------------------------------------------------- layout

    private boolean needsExpand(Layer layer) {
        if (layer.type == LayerType.CAMERA) return true;
        int animated = 0;
        for (Track t : layer.channels.values()) {
            if (!t.isEmpty()) animated++;
        }
        return animated > 1;
    }

    private void rebuildRows() {
        rows.clear();
        if (scene == null) return;
        float y = toolbarHeight + rulerHeight;
        for (Layer layer : scene.layers) {
            Row row = new Row();
            row.layer = layer;
            row.top = Math.round(y);
            row.height = Ui.dp(28);
            row.label = layer.name;
            row.times = aggregateTimes(layer);
            rows.add(row);
            y += row.height;
            if (expanded.contains(layer.id)) {
                for (String channel : animatedChannels(layer)) {
                    Row ch = new Row();
                    ch.layer = layer;
                    ch.channel = channel;
                    ch.label = channelLabel(layer, channel);
                    ch.top = Math.round(y);
                    ch.height = Ui.dp(22);
                    Track t = layer.channels.get(channel);
                    if (t != null) {
                        for (Key k : t.keys) ch.times.add(k.time);
                    }
                    rows.add(ch);
                    y += ch.height;
                }
                if (layer.type == LayerType.DRAW) {
                    Row ch = new Row();
                    ch.layer = layer;
                    ch.channel = null;
                    ch.label = "Drawn frames";
                    ch.top = Math.round(y);
                    ch.height = Ui.dp(22);
                    ch.strokeRow = true;
                    Set<Integer> frames = new TreeSet<>();
                    for (Stroke s : layer.strokes) frames.add(s.frame);
                    for (int f : frames) ch.times.add((double) f / Math.max(1, project == null ? 24 : project.fps));
                    rows.add(ch);
                    y += ch.height;
                }
            }
        }
        maxScroll = Math.max(0, y - getHeight());
        scrollY = AnimMath.clamp(scrollY, 0, maxScroll);
    }

    private List<String> animatedChannels(Layer layer) {
        List<String> list = new ArrayList<>();
        for (String ch : layer.channels.keySet()) {
            Track t = layer.channels.get(ch);
            if (t != null && !t.isEmpty()) list.add(ch);
        }
        return list;
    }

    private List<Double> aggregateTimes(Layer layer) {
        Set<Double> set = new TreeSet<>();
        if (layer.type == LayerType.DRAW) {
            for (Stroke s : layer.strokes) {
                set.add((double) s.frame / Math.max(1, project == null ? 24 : project.fps));
            }
            return new ArrayList<>(set);
        }
        set.addAll(layer.keyTimes());
        return new ArrayList<>(set);
    }

    public static String channelLabel(Layer layer, String channel) {
        if (channel == null) return "";
        if (channel.startsWith("b.") && channel.endsWith(".rot")) {
            String bone = channel.substring(2, channel.length() - 4);
            if (layer.rig != null && layer.rig.bone(bone) != null) {
                return layer.rig.bone(bone).name;
            }
            return bone;
        }
        switch (channel) {
            case Layer.X: return "Position X";
            case Layer.Y: return "Position Y";
            case Layer.SCALE: return "Scale";
            case Layer.ROT: return "Rotation";
            case Layer.OPACITY: return "Opacity";
            case Layer.FACE_BLINK: return "Blink";
            case Layer.FACE_BROW: return "Brow";
            case Layer.FACE_MOUTH: return "Mouth";
            case Layer.FACE_LOOK: return "Eye look";
            default: return channel;
        }
    }

    private float timeToX(double time) {
        return headerWidth + (float) ((time - viewStart) * pxPerSecond);
    }

    private double xToTime(float x) {
        return viewStart + (x - headerWidth) / pxPerSecond;
    }

    private double snap(double time) {
        if (project == null) return time;
        return AnimMath.timeAt(AnimMath.frameAt(time, project.fps), project.fps);
    }

    private void clampView() {
        if (scene == null) return;
        double visible = (getWidth() - headerWidth) / pxPerSecond;
        double maxStart = Math.max(0, scene.duration - visible);
        viewStart = AnimMath.clamp(viewStart, 0, maxStart);
    }

    // ------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        bg.setColor(0xFF0F141E);
        canvas.drawRect(0, 0, w, h, bg);
        if (scene == null || project == null) return;

        if (headerWidth != Ui.dp(Ui.isLandscape(getContext()) ? 190 : 148)) {
            headerWidth = Ui.dp(Ui.isLandscape(getContext()) ? 190 : 148);
        }
        if (rows.isEmpty()) rebuildRows();

        // Ruler
        float rulerTop = toolbarHeight;
        panel.setColor(0xFF0C111A);
        canvas.drawRect(0, rulerTop, w, rulerTop + rulerHeight, panel);
        border.setColor(0xFF1F2836);
        border.setStrokeWidth(Math.max(1, Ui.dp(1)));
        canvas.drawLine(0, rulerTop + rulerHeight, w, rulerTop + rulerHeight, border);
        canvas.drawLine(headerWidth, rulerTop, headerWidth, h, border);
        drawRuler(canvas, rulerTop);

        // Rows
        canvas.save();
        canvas.clipRect(0, rulerTop + rulerHeight, w, h);
        drawRows(canvas, rulerTop + rulerHeight);
        canvas.restore();

        // Playhead
        drawPlayhead(canvas);

        // Toolbar last so it sits above everything
        drawToolbar(canvas, w);
        if (toast != null) {
            float tw = dimPaint.measureText(toast);
            rect.set(w - tw - Ui.dp(20), toolbarHeight + Ui.dp(4), w - Ui.dp(8), toolbarHeight + Ui.dp(22));
            Paint p = Ui.fill(0xCC101620);
            canvas.drawRoundRect(rect, Ui.dp(7), Ui.dp(7), p);
            canvas.drawText(toast, rect.left + Ui.dp(7), rect.centerY() + Ui.dp(3), dimPaint);
        }
    }

    private void drawToolbar(Canvas canvas, int w) {
        toolbarPaint.setColor(0xFF121722);
        canvas.drawRect(0, 0, w, toolbarHeight, toolbarPaint);
        border.setColor(0xFF1F2836);
        canvas.drawLine(0, toolbarHeight - 1, w, toolbarHeight - 1, border);

        toolbarRects.clear();
        toolbarIds.clear();
        String[] ids = {"prev", "play", "next", "key", "trash", "easing", "autokey", "onion", "fit"};
        float x = Ui.dp(2);
        float size = Ui.dp(30);
        float cy = toolbarHeight / 2f;
        for (String id : ids) {
            RectF r = new RectF(x, (toolbarHeight - size) / 2f, x + size, (toolbarHeight + size) / 2f);
            boolean active = (id.equals("autokey") && autoKey) || (id.equals("onion") && onion);
            boolean enabled = true;
            if (id.equals("key") || id.equals("trash") || id.equals("easing")) enabled = hasKeySelection();
            if (active) {
                Paint p = Ui.fill(0x26FF7A18);
                canvas.drawRoundRect(r, Ui.dp(7), Ui.dp(7), p);
            }
            int color = active ? 0xFFFFB347 : (enabled ? 0xFFE8EDF6 : 0x558A94A6);
            String icon = id.equals("key") ? "keyfill" : id.equals("trash") ? "trash"
                    : id.equals("easing") ? "shader" : id.equals("autokey") ? "key"
                    : id.equals("onion") ? "onion" : id;
            if (id.equals("play")) icon = playing ? "pause" : "play";
            Icons.draw(canvas, icon, r.centerX(), cy, Ui.dp(17), color);
            toolbarRects.add(r);
            toolbarIds.add(id);
            x += size;
        }
        String time = AnimMath.timecode(playhead, project.fps);
        float tw = dimPaint.measureText(time);
        if (w - x > tw + Ui.dp(16)) {
            canvas.drawText(time, w - tw - Ui.dp(8), cy + Ui.dp(3.5f), dimPaint);
        }
    }

    private void drawRuler(Canvas canvas, float top) {
        float step = 1f;
        while (step * pxPerSecond < Ui.dp(52)) step *= 2;
        int max = (int) Math.ceil(scene.duration / step);
        for (int i = 0; i <= max; i++) {
            double t = i * step;
            float x = timeToX(t);
            if (x < headerWidth - 1 || x > getWidth()) continue;
            border.setColor(0xFF2A3444);
            canvas.drawLine(x, top + rulerHeight - Ui.dp(5), x, top + rulerHeight, border);
            String label = step >= 1 ? (int) Math.floor(t) + "s" : Ui.fixed((float) t, 1) + "s";
            canvas.drawText(label, x + Ui.dp(3), top + Ui.dp(10), smallPaint);
            if (step >= 1) {
                for (int f = 1; f < project.fps && pxPerSecond > 90; f++) {
                    float fx = timeToX(t + (double) f / project.fps);
                    border.setColor(0x331F2836);
                    canvas.drawLine(fx, top + rulerHeight - Ui.dp(3), fx, top + rulerHeight, border);
                }
            }
        }
        // Scene end marker
        float endX = timeToX(scene.duration);
        border.setColor(0xFF3A4658);
        canvas.drawLine(endX, top, endX, top + rulerHeight, border);
    }

    private void drawRows(Canvas canvas, float areaTop) {
        int h = getHeight();
        for (Row row : rows) {
            float top = row.top - scrollY;
            if (top + row.height < areaTop || top > h) continue;
            boolean selected = selectedLayer != null && row.layer.id.equals(selectedLayer.id);
            rowPaint.setColor(selected ? 0x1AFF7A18 : (row.channel != null ? 0xFF111722 : 0xFF151B27));
            canvas.drawRect(0, top, getWidth(), top + row.height, rowPaint);
            border.setColor(0xFF1B2432);
            canvas.drawLine(0, top + row.height, getWidth(), top + row.height, border);
            if (selected && row.channel == null) {
                Paint accent = Ui.fill(0xFFFF7A18);
                canvas.drawRect(0, top, Ui.dp(2.5f), top + row.height, accent);
            }
            drawHeader(canvas, row, top);
            drawKeys(canvas, row, top);
        }
    }

    private void drawHeader(Canvas canvas, Row row, float top) {
        float cy = top + row.height / 2f;
        if (row.channel != null || row.strokeRow) {
            String label = row.label;
            canvas.drawText(ellipsize(label, smallPaint, headerWidth - Ui.dp(30)), Ui.dp(26), cy + Ui.dp(3), smallPaint);
            return;
        }
        Layer layer = row.layer;
        boolean hasChildren = needsExpand(layer) || layer.type == LayerType.DRAW;
        float iconX = Ui.dp(10);
        if (hasChildren) {
            Icons.draw(canvas, expanded.contains(layer.id) ? "chevron_down" : "chevron_right",
                    iconX, cy, Ui.dp(11), 0xFF8A94A6);
        }
        String typeIcon = layer.type == LayerType.RIG ? "pose" : layer.type == LayerType.DRAW ? "brush"
                : layer.type == LayerType.TEXT ? "text" : layer.type == LayerType.IMAGE ? "image"
                : layer.type == LayerType.CAMERA ? "camera" : "shape";
        Icons.draw(canvas, typeIcon, Ui.dp(23), cy, Ui.dp(13), selectedColor(layer));
        canvas.drawText(ellipsize(layer.name, textPaint, headerWidth - Ui.dp(88)),
                Ui.dp(34), cy + Ui.dp(4), textPaint);
        // Eye and lock, right aligned inside the header.
        float eyeX = headerWidth - Ui.dp(40);
        float lockX = headerWidth - Ui.dp(18);
        Icons.draw(canvas, layer.visible ? "eye" : "eyeoff", eyeX, cy, Ui.dp(14),
                layer.visible ? 0xFFB7C0CF : 0x55B7C0CF);
        Icons.draw(canvas, layer.locked ? "lock" : "unlock", lockX, cy, Ui.dp(13),
                layer.locked ? 0xFFFFB347 : 0x55B7C0CF);
    }

    private int selectedColor(Layer layer) {
        return layer.type == LayerType.CAMERA ? 0xFF6EA8FE : 0xFFFFB347;
    }

    private void drawKeys(Canvas canvas, Row row, float top) {
        float cy = top + row.height / 2f;
        float left = headerWidth + Ui.dp(2);
        float right = getWidth() - Ui.dp(2);
        for (double time : row.times) {
            float x = timeToX(time);
            if (x < left - 6 || x > right + 6) continue;
            boolean selected = selectedLayer != null && row.layer.id.equals(selectedLayer.id)
                    && selectedChannel != null && Math.abs(selectedKeyTime - time) < 1e-4
                    && (row.channel == null || row.channel.equals(selectedChannel));
            float size = row.strokeRow ? Ui.dp(4) : (row.channel == null ? Ui.dp(5.2f) : Ui.dp(4.6f));
            if (row.strokeRow) {
                keyPaint.setColor(0xCC6EA8FE);
                canvas.drawRect(x - size, cy - size, x + size, cy + size, keyPaint);
            } else {
                diamond.reset();
                diamond.moveTo(x, cy - size);
                diamond.lineTo(x + size, cy);
                diamond.lineTo(x, cy + size);
                diamond.lineTo(x - size, cy);
                diamond.close();
                keyPaint.setColor(selected ? 0xFFFFC857 : (row.channel == null ? 0xFFFF9F43 : 0xFFFFD166));
                canvas.drawPath(diamond, keyPaint);
                if (selected) {
                    keyStroke.setColor(0xFF10141C);
                    keyStroke.setStrokeWidth(Ui.dp(1.6f));
                    canvas.drawPath(diamond, keyStroke);
                }
            }
        }
        // A subtle line joining the aggregate keys of a collapsed row.
        if (row.channel == null && !row.strokeRow && row.times.size() > 1) {
            border.setColor(0x33FFB347);
            border.setStrokeWidth(1);
            for (int i = 1; i < row.times.size(); i++) {
                float x0 = timeToX(row.times.get(i - 1));
                float x1 = timeToX(row.times.get(i));
                if (x1 < left || x0 > right) continue;
                canvas.drawLine(x0, cy, x1, cy, border);
            }
        }
    }

    private void drawPlayhead(Canvas canvas) {
        float x = timeToX(playhead);
        if (x < headerWidth) return;
        playheadPaint.setColor(0xFFFF4D6D);
        playheadPaint.setStrokeWidth(Ui.dp(1.5f));
        canvas.drawLine(x, toolbarHeight, x, getHeight(), playheadPaint);
        playTriangle.reset();
        playTriangle.moveTo(x - Ui.dp(5), toolbarHeight);
        playTriangle.lineTo(x + Ui.dp(5), toolbarHeight);
        playTriangle.lineTo(x, toolbarHeight + Ui.dp(7));
        playTriangle.close();
        keyPaint.setColor(0xFFFF4D6D);
        canvas.drawPath(playTriangle, keyPaint);
    }

    // ------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (project == null || scene == null) return false;
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return onDown(x, y);
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() >= 2) {
                    pinch = true;
                    pinchStartDistance = Math.max(1f, Ui.distance(event.getX(0), event.getY(0), event.getX(1), event.getY(1)));
                    pinchMidX = (event.getX(0) + event.getX(1)) / 2f;
                    pinchStartPx = pxPerSecond;
                    pinchStartView = viewStart;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                return onMove(event);
            case MotionEvent.ACTION_UP:
                return onUp();
            case MotionEvent.ACTION_CANCEL:
                resetGesture();
                return true;
            default:
                return true;
        }
    }

    private boolean onDown(float x, float y) {
        downX = lastX = x;
        downY = lastY = y;
        moved = false;
        editStarted = false;
        draggingKey = false;
        dragRow = null;
        scrubbing = false;
        draggingPlayhead = false;
        scrolling = false;
        pressedToolbar = -1;
        if (y < toolbarHeight) {
            for (int i = 0; i < toolbarRects.size(); i++) {
                if (toolbarRects.get(i).contains(x, y)) {
                    pressedToolbar = i;
                    invalidate();
                    return true;
                }
            }
            return true;
        }
        if (y < toolbarHeight + rulerHeight) {
            scrubbing = true;
            scrub(x);
            return true;
        }
        float areaTop = toolbarHeight + rulerHeight;
        if (x < headerWidth) {
            Row row = rowAt(y);
            if (row != null) {
                float cy = row.top - scrollY + row.height / 2f;
                if (Math.abs(y - cy) < Ui.dp(16)) {
                    if (!row.strokeRow && row.channel == null) {
                        float eyeX = headerWidth - Ui.dp(40);
                        float lockX = headerWidth - Ui.dp(18);
                        if (Math.abs(x - eyeX) < Ui.dp(14)) {
                            row.layer.visible = !row.layer.visible;
                            if (listener != null) {
                                listener.onBeforeEdit();
                                listener.onEdited();
                            }
                            refresh();
                            return true;
                        }
                        if (Math.abs(x - lockX) < Ui.dp(14)) {
                            row.layer.locked = !row.layer.locked;
                            refresh();
                            return true;
                        }
                        if (x < Ui.dp(20) && (needsExpand(row.layer) || row.layer.type == LayerType.DRAW)) {
                            if (expanded.contains(row.layer.id)) expanded.remove(row.layer.id);
                            else expanded.add(row.layer.id);
                            rebuildRows();
                            invalidate();
                            return true;
                        }
                    }
                }
            }
            scrolling = true;
            return true;
        }
        if (x >= getWidth() - Ui.dp(10)) {
            scrolling = true;
            return true;
        }
        // Key hit?
        Row row = rowAt(y);
        if (row != null && !row.strokeRow) {
            double best = -1;
            float bestDx = Ui.dp(14);
            for (double time : row.times) {
                float kx = timeToX(time);
                float d = Math.abs(kx - x);
                if (d < bestDx) {
                    bestDx = d;
                    best = time;
                }
            }
            if (best >= 0) {
                draggingKey = true;
                dragRow = row;
                dragKeyTime = best;
                selectedLayer = row.layer;
                selectedChannel = row.channel;
                selectedKeyTime = best;
                dragStartTime = (float) best;
                playhead = best;
                if (listener != null) {
                    listener.onSelectLayer(row.layer);
                    listener.onKeySelected(row.layer, row.channel, best);
                    listener.onScrub(best);
                }
                invalidate();
                return true;
            }
        }
        if (row != null) {
            selectedLayer = row.layer;
            selectedChannel = row.channel;
            selectedKeyTime = -1;
            if (listener != null) {
                listener.onSelectLayer(row.layer);
                listener.onKeySelected(row.layer, null, -1);
            }
            refresh();
        }
        scrubbing = true;
        scrub(x);
        return true;
    }

    private Row rowAt(float y) {
        float local = y - toolbarHeight - rulerHeight + scrollY;
        for (Row row : rows) {
            if (local >= row.top - toolbarHeight - rulerHeight && local < row.top - toolbarHeight - rulerHeight + row.height) {
                return row;
            }
        }
        return null;
    }

    private void scrub(float x) {
        double t = snap(AnimMath.clamp(xToTime(x), 0, scene.duration));
        playhead = t;
        if (listener != null) listener.onScrub(t);
        invalidate();
    }

    private boolean onMove(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        if (pinch && event.getPointerCount() >= 2) {
            float dist = Math.max(1f, Ui.distance(event.getX(0), event.getY(0), event.getX(1), event.getY(1)));
            float factor = dist / pinchStartDistance;
            double anchor = pinchStartView + (pinchMidX - headerWidth) / pinchStartPx;
            pxPerSecond = AnimMath.clamp(pinchStartPx * factor, 6f, 900f);
            viewStart = anchor - (pinchMidX - headerWidth) / pxPerSecond;
            clampView();
            invalidate();
            return true;
        }
        if (Math.abs(x - downX) > Ui.dp(4) || Math.abs(y - downY) > Ui.dp(4)) moved = true;
        if (pressedToolbar >= 0) {
            lastX = x;
            lastY = y;
            return true;
        }
        if (draggingKey) {
            double t = snap(AnimMath.clamp(xToTime(x), 0, scene.duration));
            if (Math.abs(t - dragKeyTime) > 1e-6) {
                if (!editStarted) {
                    if (listener != null) listener.onBeforeEdit();
                    editStarted = true;
                }
                moveKeys(dragRow, dragKeyTime, t);
                dragKeyTime = t;
                selectedKeyTime = t;
                playhead = t;
                if (listener != null) {
                    listener.onScrub(t);
                    listener.onKeySelected(dragRow.layer, dragRow.channel, t);
                }
                refresh();
            }
            lastX = x;
            lastY = y;
            return true;
        }
        if (scrubbing) {
            scrub(x);
            lastX = x;
            lastY = y;
            return true;
        }
        if (scrolling) {
            scrollY = AnimMath.clamp(scrollY - (y - lastY), 0, maxScroll);
            lastX = x;
            lastY = y;
            invalidate();
            return true;
        }
        lastX = x;
        lastY = y;
        return true;
    }

    private void moveKeys(Row row, double from, double to) {
        Layer layer = row.layer;
        if (row.channel != null) {
            Track t = layer.channels.get(row.channel);
            if (t != null) {
                int index = t.indexAt(from);
                if (index >= 0) t.move(index, to);
            }
            return;
        }
        for (Track t : layer.channels.values()) {
            int index = t.indexAt(from);
            if (index >= 0) t.move(index, to);
        }
    }

    private boolean onUp() {
        if (pressedToolbar >= 0 && pressedToolbar < toolbarIds.size()) {
            if (!moved) fireToolbar(toolbarIds.get(pressedToolbar));
            pressedToolbar = -1;
            invalidate();
            return true;
        }
        if (draggingKey && editStarted && listener != null) listener.onEdited();
        if (!moved && !scrubbing && !draggingKey && !scrolling && downY > toolbarHeight + rulerHeight
                && downX > headerWidth) {
            double t = snap(AnimMath.clamp(xToTime(downX), 0, scene.duration));
            long now = System.currentTimeMillis();
            if (now - lastTapTime < 300 && Math.abs(t - lastTapTimeValue) < 0.05) {
                if (listener != null) listener.onMessage("Frame " + AnimMath.frameAt(t, project.fps));
                lastTapTime = 0;
            } else {
                lastTapTime = now;
                lastTapTimeValue = t;
            }
        }
        resetGesture();
        return true;
    }

    private void resetGesture() {
        pressedToolbar = -1;
        scrubbing = false;
        draggingKey = false;
        scrolling = false;
        pinch = false;
        dragRow = null;
        editStarted = false;
        invalidate();
    }

    private void fireToolbar(String id) {
        switch (id) {
            case "prev":
                if (listener != null) listener.onScrub(Math.max(0, playhead - 1.0 / project.fps));
                break;
            case "next":
                if (listener != null) listener.onScrub(Math.min(scene.duration, playhead + 1.0 / project.fps));
                break;
            case "play":
                if (listener != null) listener.onTogglePlay();
                break;
            case "key":
                if (listener != null) listener.onAddKey();
                break;
            case "trash":
                if (listener != null) listener.onDeleteKey();
                break;
            case "easing":
                if (listener != null) listener.onEasing();
                break;
            case "autokey":
                if (listener != null) listener.onToggleAutoKey();
                break;
            case "onion":
                if (listener != null) listener.onToggleOnion();
                break;
            case "fit":
                fitTime();
                break;
            default:
                break;
        }
    }

    private static String ellipsize(String text, Paint paint, float maxWidth) {
        if (text == null) return "";
        if (paint.measureText(text) <= maxWidth) return text;
        int end = text.length();
        while (end > 0 && paint.measureText(text.substring(0, end) + "\u2026") > maxWidth) end--;
        return text.substring(0, Math.max(0, end)) + "\u2026";
    }

    /** Applies the easing to the selected key, used by the inspector. */
    public void setSelectedEasing(Easing easing) {
        if (selectedLayer == null || selectedKeyTime < 0) return;
        if (selectedChannel != null) {
            Track t = selectedLayer.channels.get(selectedChannel);
            if (t != null) {
                Key k = t.keyAt(selectedKeyTime);
                if (k != null) k.ease = easing;
            }
        } else {
            for (Track t : selectedLayer.channels.values()) {
                Key k = t.keyAt(selectedKeyTime);
                if (k != null) k.ease = easing;
            }
        }
        refresh();
    }
}
