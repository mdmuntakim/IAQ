package app.bxanimator.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import app.bxanimator.core.Theme;

/** A flat, theme aware icon button with an optional caption. */
public final class IconButton extends View {

    public enum Style {RAIL, BAR, TALL}

    private final Theme theme;
    private final Style style;
    private String icon;
    private String label;
    private boolean active;
    private boolean badge;
    private int iconColor;
    private int activeTint;
    private boolean pressed;
    private Paint bgPaint;
    private Paint iconPaint;
    private Paint labelPaint;

    public IconButton(Context context, Theme theme, String icon, String label, Style style) {
        super(context);
        this.theme = theme;
        this.icon = icon;
        this.label = label;
        this.style = style;
        this.iconColor = theme.textDim;
        this.activeTint = theme.accent;
        setClickable(true);
        setFocusable(true);
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint = Ui.text(theme.textDim, Ui.sp(8.5f), false);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setIcon(String icon) {
        this.icon = icon;
        invalidate();
    }

    public void setLabel(String label) {
        this.label = label;
        invalidate();
    }

    public void setActive(boolean active) {
        this.active = active;
        invalidate();
    }

    public boolean isActive() {
        return active;
    }

    public void setIconColor(int color) {
        this.iconColor = color;
        invalidate();
    }

    public void setActiveTint(int color) {
        this.activeTint = color;
        invalidate();
    }

    public void setBadge(boolean badge) {
        this.badge = badge;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w;
        int h;
        switch (style) {
            case RAIL:
                w = Ui.dp(54);
                h = Ui.dp(56);
                break;
            case TALL:
                w = Ui.dp(52);
                h = Ui.dp(44);
                break;
            case BAR:
            default:
                w = Ui.dp(38);
                h = Ui.dp(38);
                break;
        }
        int width = resolveSize(w, widthMeasureSpec);
        int height = resolveSize(h, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float radius = Ui.dp(style == Style.RAIL ? 12 : 9);
        if (active || pressed) {
            bgPaint.setColor(active ? Ui.alpha(activeTint, 0.16f) : Ui.alpha(theme.text, 0.08f));
            canvas.drawRoundRect(0, 0, w, h, radius, radius, bgPaint);
        }
        int color = isEnabled() ? (active ? activeTint : iconColor) : Ui.alpha(theme.textDim, 0.35f);
        if (style == Style.RAIL && label != null) {
            float iconSize = Ui.dp(21);
            Icons.draw(canvas, icon, w / 2f, h * 0.38f, iconSize, color);
            labelPaint.setColor(isEnabled() ? (active ? activeTint : theme.textDim) : Ui.alpha(theme.textDim, 0.35f));
            canvas.drawText(label, w / 2f, h - Ui.dp(9), labelPaint);
        } else {
            Icons.draw(canvas, icon, w / 2f, h / 2f, Ui.dp(style == Style.TALL ? 20 : 20), color);
        }
        if (badge) {
            bgPaint.setColor(theme.accent);
            canvas.drawCircle(w - Ui.dp(11), Ui.dp(11), Ui.dp(3), bgPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                pressed = false;
                invalidate();
                if (event.getX() >= 0 && event.getX() <= getWidth() && event.getY() >= 0 && event.getY() <= getHeight()) {
                    performClick();
                }
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
