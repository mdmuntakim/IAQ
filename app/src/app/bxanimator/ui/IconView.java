package app.bxanimator.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/** A non interactive vector icon, for rows and cards. */
public final class IconView extends View {

    private String icon;
    private int color;
    private float sizeDp;

    public IconView(Context context, String icon, int color, float sizeDp) {
        super(context);
        this.icon = icon;
        this.color = color;
        this.sizeDp = sizeDp;
    }

    public void setIcon(String icon) {
        this.icon = icon;
        invalidate();
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Ui.dp(sizeDp + 8);
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Icons.draw(canvas, icon, getWidth() / 2f, getHeight() / 2f, Ui.dp(sizeDp), color);
    }
}
