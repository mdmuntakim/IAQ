package app.bxanimator.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import app.bxanimator.core.Theme;

/** The editor's top bar: project, scene, undo/redo, play, export and menu. */
public final class TopBar extends View {

    public interface Listener {
        void onBack();

        void onUndo();

        void onRedo();

        void onPlay();

        void onExport();

        void onMenu();

        void onScene();

        void onInspector();
    }

    private final Theme theme;
    private Listener listener;
    private String title = "BXAnimator";
    private String scene = "Scene";
    private String timecode = "00:00:00";
    private boolean playing;
    private boolean canUndo = true;
    private boolean canRedo = true;
    private boolean inspectorOpen;
    private final Paint bg = new Paint();
    private final Paint line = new Paint();
    private final Paint titlePaint;
    private final Paint subPaint;
    private final Paint badgePaint;
    private final RectF backRect = new RectF();
    private final RectF sceneRect = new RectF();
    private final RectF undoRect = new RectF();
    private final RectF redoRect = new RectF();
    private final RectF playRect = new RectF();
    private final RectF exportRect = new RectF();
    private final RectF menuRect = new RectF();
    private final RectF inspectorRect = new RectF();
    private final RectF playFill = new RectF();
    private int pressed = -1;

    public TopBar(Context context, Theme theme) {
        super(context);
        this.theme = theme;
        bg.setColor(theme.header);
        line.setColor(theme.border);
        line.setStrokeWidth(Math.max(1, Ui.dp(1)));
        titlePaint = Ui.text(theme.text, Ui.sp(15), true);
        subPaint = Ui.text(theme.textDim, Ui.sp(10.5f), false);
        badgePaint = Ui.fill(theme.accent);
        setClickable(true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setState(String title, String scene, String timecode, boolean playing,
                         boolean canUndo, boolean canRedo, boolean inspectorOpen) {
        this.title = title;
        this.scene = scene;
        this.timecode = timecode;
        this.playing = playing;
        this.canUndo = canUndo;
        this.canRedo = canRedo;
        this.inspectorOpen = inspectorOpen;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(Ui.dp(360), widthMeasureSpec), Ui.dp(56));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, bg);
        canvas.drawLine(0, h - 1, w, h - 1, line);

        int cy = h / 2;
        float iconSize = Ui.dp(21);
        backRect.set(Ui.dp(4), 0, Ui.dp(46), h);
        Icons.draw(canvas, "back", backRect.centerX(), cy, iconSize, theme.text);

        float titleX = Ui.dp(46);
        canvas.drawText(ellipsize(title, titlePaint, w - titleX - Ui.dp(250)), titleX, cy - Ui.dp(2), titlePaint);
        String sub = scene + "  \u00b7  " + timecode;
        canvas.drawText(ellipsize(sub, subPaint, w - titleX - Ui.dp(250)), titleX, cy + Ui.dp(14), subPaint);
        sceneRect.set(titleX, cy + Ui.dp(2), titleX + Math.min(subPaint.measureText(sub), w - titleX - Ui.dp(250)), h);

        float right = w - Ui.dp(6);
        menuRect.set(right - Ui.dp(38), cy - Ui.dp(19), right, cy + Ui.dp(19));
        right -= Ui.dp(40);
        exportRect.set(right - Ui.dp(38), cy - Ui.dp(19), right, cy + Ui.dp(19));
        right -= Ui.dp(42);
        playRect.set(right - Ui.dp(44), cy - Ui.dp(18), right, cy + Ui.dp(18));
        playFill.set(playRect);
        playFill.inset(Ui.dp(2), Ui.dp(2));
        badgePaint.setColor(playing ? Ui.alpha(theme.accent, 0.22f) : Ui.alpha(theme.text, 0.07f));
        canvas.drawRoundRect(playFill, Ui.dp(10), Ui.dp(10), badgePaint);
        Icons.draw(canvas, playing ? "pause" : "play", playRect.centerX(), playRect.centerY(),
                Ui.dp(18), playing ? theme.accent : theme.text);
        right -= Ui.dp(44);
        redoRect.set(right - Ui.dp(34), cy - Ui.dp(17), right, cy + Ui.dp(17));
        right -= Ui.dp(36);
        undoRect.set(right - Ui.dp(34), cy - Ui.dp(17), right, cy + Ui.dp(17));
        right -= Ui.dp(36);
        inspectorRect.set(right - Ui.dp(34), cy - Ui.dp(17), right, cy + Ui.dp(17));

        Icons.draw(canvas, "undo", undoRect.centerX(), cy, iconSize,
                canUndo ? theme.text : Ui.alpha(theme.textDim, 0.3f));
        Icons.draw(canvas, "redo", redoRect.centerX(), cy, iconSize,
                canRedo ? theme.text : Ui.alpha(theme.textDim, 0.3f));
        Icons.draw(canvas, "export", exportRect.centerX(), cy, Ui.dp(20), theme.accentSoft);
        Icons.draw(canvas, "more", menuRect.centerX(), cy, Ui.dp(20), theme.text);

        if (w > Ui.dp(420)) {
            Icons.draw(canvas, "layers", inspectorRect.centerX(), cy, Ui.dp(19),
                    inspectorOpen ? theme.accent : theme.textDim);
        }

        if (pressed >= 0) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Ui.alpha(theme.text, 0.08f));
            RectF r = rectAt(pressed);
            canvas.drawRoundRect(r, Ui.dp(9), Ui.dp(9), p);
        }
    }

    private RectF rectAt(int index) {
        switch (index) {
            case 0: return backRect;
            case 1: return undoRect;
            case 2: return redoRect;
            case 3: return playRect;
            case 4: return exportRect;
            case 5: return menuRect;
            case 6: return inspectorRect;
            case 7: return sceneRect;
            default: return new RectF();
        }
    }

    private int hit(float x, float y) {
        if (x <= backRect.right) return 0;
        if (undoRect.contains(x, y)) return 1;
        if (redoRect.contains(x, y)) return 2;
        if (playRect.contains(x, y)) return 3;
        if (exportRect.contains(x, y)) return 4;
        if (menuRect.contains(x, y)) return 5;
        if (getWidth() > Ui.dp(420) && inspectorRect.contains(x, y)) return 6;
        if (sceneRect.contains(x, y)) return 7;
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = hit(event.getX(), event.getY());
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP: {
                int down = pressed;
                pressed = -1;
                invalidate();
                if (down < 0 || down != hit(event.getX(), event.getY()) || listener == null) return true;
                switch (down) {
                    case 0: listener.onBack(); break;
                    case 1: if (canUndo) listener.onUndo(); break;
                    case 2: if (canRedo) listener.onRedo(); break;
                    case 3: listener.onPlay(); break;
                    case 4: listener.onExport(); break;
                    case 5: listener.onMenu(); break;
                    case 6: listener.onInspector(); break;
                    case 7: listener.onScene(); break;
                    default: break;
                }
                return true;
            }
            default:
                return true;
        }
    }

    private static String ellipsize(String text, Paint paint, float maxWidth) {
        if (text == null) return "";
        if (paint.measureText(text) <= maxWidth) return text;
        String ellipsis = "\u2026";
        int end = text.length();
        while (end > 0 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) end--;
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }
}
