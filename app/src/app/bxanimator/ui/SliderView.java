package app.bxanimator.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import app.bxanimator.core.AnimMath;

/**
 * A slim, theme aware horizontal slider. The inspector pairs it with a label
 * and a keyframe diamond, which keeps every animatable value one gesture away.
 */
public final class SliderView extends View {

    public interface Listener {
        void onStart(float value);

        void onChanging(float value);

        void onCommit(float value);
    }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbRing = Ui.stroke(0x66000000, 1f);
    private final RectF rect = new RectF();

    private float min;
    private float max;
    private float value;
    private float step;
    private int decimals = 2;
    private int trackColor = 0xFF1F2836;
    private int fillColor = 0xFFFF7A18;
    private int thumbColor = 0xFFF2F5FA;
    private boolean dragging;
    private Listener listener;

    public SliderView(Context context) {
        super(context);
        setFocusable(true);
        setClickable(true);
    }

    public void configure(float min, float max, float value, float step, int decimals,
                          int trackColor, int fillColor, int thumbColor) {
        this.min = min;
        this.max = max;
        this.step = Math.max(0f, step);
        this.decimals = decimals;
        this.trackColor = trackColor;
        this.fillColor = fillColor;
        this.thumbColor = thumbColor;
        setValue(value);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setValue(float value) {
        this.value = AnimMath.clamp(value, min, max);
        invalidate();
    }

    public float getValue() {
        return value;
    }

    public float getMin() {
        return min;
    }

    public float getMax() {
        return max;
    }

    public int getDecimals() {
        return decimals;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(Ui.dp(120), widthMeasureSpec);
        int height = resolveSize(Ui.dp(30), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cy = h / 2f;
        float radius = Math.max(2f, Ui.dp(2.2f));
        float left = Ui.dp(9);
        float right = w - Ui.dp(9);
        float span = Math.max(1f, right - left);
        float fraction = max > min ? (value - min) / (max - min) : 0f;
        fraction = AnimMath.clamp(fraction, 0f, 1f);

        track.setColor(trackColor);
        rect.set(left, cy - radius, right, cy + radius);
        canvas.drawRoundRect(rect, radius, radius, track);

        fill.setColor(fillColor);
        rect.set(left, cy - radius, left + span * fraction, cy + radius);
        canvas.drawRoundRect(rect, radius, radius, fill);

        float tx = left + span * fraction;
        thumb.setColor(thumbColor);
        canvas.drawCircle(tx, cy, Ui.dp(7.5f), thumb);
        canvas.drawCircle(tx, cy, Ui.dp(7.5f), thumbRing);
        fill.setColor(fillColor);
        canvas.drawCircle(tx, cy, Ui.dp(3.2f), fill);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = true;
                if (listener != null) listener.onStart(value);
                updateFrom(event.getX(), true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                updateFrom(event.getX(), true);
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) return true;
                dragging = false;
                updateFrom(event.getX(), false);
                if (listener != null) listener.onCommit(value);
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void updateFrom(float x, boolean changing) {
        float left = Ui.dp(9);
        float right = getWidth() - Ui.dp(9);
        float fraction = AnimMath.clamp((x - left) / Math.max(1f, right - left), 0f, 1f);
        float next = min + (max - min) * fraction;
        if (step > 0f) next = Math.round(next / step) * step;
        next = AnimMath.clamp(next, min, max);
        if (Math.abs(next - value) > 1e-6f) {
            value = next;
            invalidate();
            if (changing && listener != null) listener.onChanging(value);
        }
    }

    public String formatted() {
        if (decimals <= 0) return String.valueOf(Math.round(value));
        return String.format(java.util.Locale.US, "%." + decimals + "f", value);
    }
}
