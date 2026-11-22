package app.bxanimator.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Vector icon set drawn with primitives in a 24 x 24 grid. No image assets, so
 * the icons stay sharp at every density and follow the theme colour.
 */
public final class Icons {

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Icons() {
    }

    public static void draw(Canvas c, String id, float cx, float cy, float size, int color) {
        if (id == null) return;
        P.reset();
        P.setAntiAlias(true);
        P.setColor(color);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(1.9f);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeJoin(Paint.Join.ROUND);
        c.save();
        c.translate(cx - size / 2f, cy - size / 2f);
        c.scale(size / 24f, size / 24f);
        drawIcon(c, id);
        c.restore();
    }

    private static void drawIcon(Canvas c, String id) {
        switch (id) {
            case "back": {
                Path p = new Path();
                p.moveTo(15, 5);
                p.lineTo(8, 12);
                p.lineTo(15, 19);
                c.drawPath(p, P);
                break;
            }
            case "forward": {
                Path p = new Path();
                p.moveTo(9, 5);
                p.lineTo(16, 12);
                p.lineTo(9, 19);
                c.drawPath(p, P);
                break;
            }
            case "up": {
                Path p = new Path();
                p.moveTo(12, 19);
                p.lineTo(12, 5);
                p.moveTo(6, 11);
                p.lineTo(12, 5);
                p.lineTo(18, 11);
                c.drawPath(p, P);
                break;
            }
            case "down": {
                Path p = new Path();
                p.moveTo(12, 5);
                p.lineTo(12, 19);
                p.moveTo(6, 13);
                p.lineTo(12, 19);
                p.lineTo(18, 13);
                c.drawPath(p, P);
                break;
            }
            case "play": {
                P.setStyle(Paint.Style.FILL);
                Path p = new Path();
                p.moveTo(8, 5);
                p.lineTo(19, 12);
                p.lineTo(8, 19);
                p.close();
                c.drawPath(p, P);
                break;
            }
            case "pause": {
                P.setStyle(Paint.Style.FILL);
                c.drawRoundRect(new RectF(7, 5, 10.5f, 19), 1, 1, P);
                c.drawRoundRect(new RectF(13.5f, 5, 17, 19), 1, 1, P);
                break;
            }
            case "record": {
                P.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 12, 6, P);
                break;
            }
            case "undo": {
                Path p = new Path();
                p.moveTo(9, 6);
                p.cubicTo(4.5f, 8, 3.5f, 13, 6.5f, 16.5f);
                p.cubicTo(8.5f, 18.7f, 12, 19, 14.5f, 17.5f);
                c.drawPath(p, P);
                Path head = new Path();
                head.moveTo(5, 4);
                head.lineTo(9, 6.5f);
                head.lineTo(8.5f, 11);
                c.drawPath(head, P);
                break;
            }
            case "redo": {
                c.save();
                c.scale(-1, 1);
                c.translate(-24, 0);
                drawIcon(c, "undo");
                c.restore();
                break;
            }
            case "export": {
                Path p = new Path();
                p.moveTo(5, 15);
                p.lineTo(5, 19.5f);
                p.lineTo(19, 19.5f);
                p.lineTo(19, 15);
                p.moveTo(12, 16);
                p.lineTo(12, 4);
                p.moveTo(7, 9);
                p.lineTo(12, 4);
                p.lineTo(17, 9);
                c.drawPath(p, P);
                break;
            }
            case "import": {
                Path p = new Path();
                p.moveTo(5, 15);
                p.lineTo(5, 19.5f);
                p.lineTo(19, 19.5f);
                p.lineTo(19, 15);
                p.moveTo(12, 4);
                p.lineTo(12, 16);
                p.moveTo(7, 11);
                p.lineTo(12, 16);
                p.lineTo(17, 11);
                c.drawPath(p, P);
                break;
            }
            case "menu": {
                c.drawLine(4, 7, 20, 7, P);
                c.drawLine(4, 12, 20, 12, P);
                c.drawLine(4, 17, 20, 17, P);
                break;
            }
            case "more": {
                P.setStyle(Paint.Style.FILL);
                c.drawCircle(6, 12, 1.6f, P);
                c.drawCircle(12, 12, 1.6f, P);
                c.drawCircle(18, 12, 1.6f, P);
                break;
            }
            case "select": {
                P.setStyle(Paint.Style.FILL);
                Path p = new Path();
                p.moveTo(6, 3);
                p.lineTo(6, 19);
                p.lineTo(10, 15);
                p.lineTo(13, 21);
                p.lineTo(16, 19.4f);
                p.lineTo(13, 13.8f);
                p.lineTo(18, 13.8f);
                p.close();
                c.drawPath(p, P);
                break;
            }
            case "pose": {
                c.drawCircle(12, 5.5f, 2.6f, P);
                Path p = new Path();
                p.moveTo(12, 8.5f);
                p.lineTo(12, 15);
                p.moveTo(12, 10.5f);
                p.lineTo(7, 13.5f);
                p.moveTo(12, 10.5f);
                p.lineTo(17, 13.5f);
                p.moveTo(12, 15);
                p.lineTo(8, 21);
                p.moveTo(12, 15);
                p.lineTo(16, 21);
                c.drawPath(p, P);
                break;
            }
            case "draw": {
                Path p = new Path();
                p.moveTo(4, 20);
                p.lineTo(5, 15.5f);
                p.lineTo(15.5f, 5);
                p.lineTo(19, 8.5f);
                p.lineTo(8.5f, 19);
                p.close();
                c.drawPath(p, P);
                c.drawLine(14, 6.5f, 17.5f, 10, P);
                break;
            }
            case "brush": {
                Path p = new Path();
                p.moveTo(6, 21);
                p.cubicTo(4, 18, 6, 16, 9, 16);
                p.lineTo(14, 11);
                p.lineTo(18, 15);
                p.lineTo(13, 20);
                p.cubicTo(11, 22, 8, 22, 6, 21);
                c.drawPath(p, P);
                break;
            }
            case "eraser": {
                c.save();
                c.rotate(-35, 12, 12);
                c.drawRoundRect(new RectF(5, 9, 19, 15), 2, 2, P);
                c.drawLine(10, 9, 10, 15, P);
                c.restore();
                break;
            }
            case "camera": {
                c.drawRoundRect(new RectF(3, 7, 21, 19.5f), 2.5f, 2.5f, P);
                c.drawCircle(12, 13, 4, P);
                Path p = new Path();
                p.moveTo(8.5f, 7);
                p.lineTo(10, 4.5f);
                p.lineTo(14, 4.5f);
                p.lineTo(15.5f, 7);
                c.drawPath(p, P);
                break;
            }
            case "add": {
                c.drawLine(12, 5, 12, 19, P);
                c.drawLine(5, 12, 19, 12, P);
                break;
            }
            case "minus": {
                c.drawLine(5, 12, 19, 12, P);
                break;
            }
            case "effects": {
                P.setStyle(Paint.Style.FILL);
                Path p = new Path();
                p.moveTo(11, 2.5f);
                p.lineTo(13.2f, 8.8f);
                p.lineTo(19.5f, 11);
                p.lineTo(13.2f, 13.2f);
                p.lineTo(11, 19.5f);
                p.lineTo(8.8f, 13.2f);
                p.lineTo(2.5f, 11);
                p.lineTo(8.8f, 8.8f);
                p.close();
                c.drawPath(p, P);
                Path q = new Path();
                q.moveTo(18.5f, 15);
                q.lineTo(19.6f, 18);
                q.lineTo(22.5f, 19);
                q.lineTo(19.6f, 20);
                q.lineTo(18.5f, 23);
                q.lineTo(17.4f, 20);
                q.lineTo(14.5f, 19);
                q.lineTo(17.4f, 18);
                q.close();
                c.drawPath(q, P);
                break;
            }
            case "eye": {
                Path p = new Path();
                p.moveTo(2.5f, 12);
                p.cubicTo(6, 6.2f, 18, 6.2f, 21.5f, 12);
                p.cubicTo(18, 17.8f, 6, 17.8f, 2.5f, 12);
                p.close();
                c.drawPath(p, P);
                P.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 12, 2.6f, P);
                break;
            }
            case "eyeoff": {
                Path p = new Path();
                p.moveTo(2.5f, 12);
                p.cubicTo(6, 6.2f, 18, 6.2f, 21.5f, 12);
                p.cubicTo(18, 17.8f, 6, 17.8f, 2.5f, 12);
                p.close();
                c.drawPath(p, P);
                c.drawLine(4, 4, 20, 20, P);
                break;
            }
            case "lock": {
                c.drawRoundRect(new RectF(5.5f, 10.5f, 18.5f, 20), 2, 2, P);
                Path p = new Path();
                p.moveTo(8.5f, 10.5f);
                p.lineTo(8.5f, 8);
                p.cubicTo(8.5f, 4.5f, 15.5f, 4.5f, 15.5f, 8);
                p.lineTo(15.5f, 10.5f);
                c.drawPath(p, P);
                break;
            }
            case "unlock": {
                c.drawRoundRect(new RectF(5.5f, 10.5f, 18.5f, 20), 2, 2, P);
                Path p = new Path();
                p.moveTo(8.5f, 10.5f);
                p.lineTo(8.5f, 8);
                p.cubicTo(8.5f, 4.5f, 15.5f, 4.5f, 15.5f, 8);
                c.drawPath(p, P);
                break;
            }
            case "key": {
                Path p = new Path();
                p.moveTo(12, 3);
                p.lineTo(19, 10);
                p.lineTo(12, 17);
                p.lineTo(5, 10);
                p.close();
                c.drawPath(p, P);
                break;
            }
            case "keyfill": {
                P.setStyle(Paint.Style.FILL);
                Path p = new Path();
                p.moveTo(12, 3);
                p.lineTo(19, 10);
                p.lineTo(12, 17);
                p.lineTo(5, 10);
                p.close();
                c.drawPath(p, P);
                break;
            }
            case "trash": {
                Path p = new Path();
                p.moveTo(4, 6.5f);
                p.lineTo(20, 6.5f);
                p.moveTo(9, 6.5f);
                p.lineTo(9.5f, 3.5f);
                p.lineTo(14.5f, 3.5f);
                p.lineTo(15, 6.5f);
                p.moveTo(6, 6.5f);
                p.lineTo(7.5f, 21);
                p.lineTo(16.5f, 21);
                p.lineTo(18, 6.5f);
                p.moveTo(10, 10);
                p.lineTo(10.5f, 17.5f);
                p.moveTo(14, 10);
                p.lineTo(13.5f, 17.5f);
                c.drawPath(p, P);
                break;
            }
            case "duplicate": {
                c.drawRoundRect(new RectF(3.5f, 3.5f, 14, 14), 2, 2, P);
                c.drawRoundRect(new RectF(10, 10, 20.5f, 20.5f), 2, 2, P);
                break;
            }
            case "copy": {
                c.drawRoundRect(new RectF(8, 8, 20, 20), 2, 2, P);
                Path p = new Path();
                p.moveTo(16, 8);
                p.lineTo(16, 4);
                p.lineTo(4, 4);
                p.lineTo(4, 16);
                p.lineTo(8, 16);
                c.drawPath(p, P);
                break;
            }
            case "settings": {
                c.drawCircle(12, 12, 3.2f, P);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    float x0 = (float) (12 + Math.cos(a) * 6.2);
                    float y0 = (float) (12 + Math.sin(a) * 6.2);
                    float x1 = (float) (12 + Math.cos(a) * 8.6);
                    float y1 = (float) (12 + Math.sin(a) * 8.6);
                    c.drawLine(x0, y0, x1, y1, P);
                }
                break;
            }
            case "theme": {
                c.drawCircle(12, 12, 8.5f, P);
                P.setStyle(Paint.Style.FILL);
                Path p = new Path();
                p.addArc(new RectF(3.5f, 3.5f, 20.5f, 20.5f), 90, 180);
                p.close();
                c.drawPath(p, P);
                break;
            }
            case "chevron_right": {
                Path p = new Path();
                p.moveTo(9, 6);
                p.lineTo(15, 12);
                p.lineTo(9, 18);
                c.drawPath(p, P);
                break;
            }
            case "chevron_down": {
                Path p = new Path();
                p.moveTo(6, 9);
                p.lineTo(12, 15);
                p.lineTo(18, 9);
                c.drawPath(p, P);
                break;
            }
            case "onion": {
                c.drawCircle(9, 12, 5.5f, P);
                c.drawCircle(15, 12, 5.5f, P);
                break;
            }
            case "folder": {
                Path p = new Path();
                p.moveTo(3, 6.5f);
                p.lineTo(9, 6.5f);
                p.lineTo(11, 9);
                p.lineTo(21, 9);
                p.lineTo(21, 19.5f);
                p.lineTo(3, 19.5f);
                p.close();
                c.drawPath(p, P);
                break;
            }
            case "help": {
                c.drawCircle(12, 12, 8.5f, P);
                P.setStyle(Paint.Style.FILL);
                P.setTextSize(12);
                P.setTextAlign(Paint.Align.CENTER);
                P.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
                c.drawText("?", 12, 16.2f, P);
                break;
            }
            case "text": {
                P.setTextSize(17);
                P.setTextAlign(Paint.Align.CENTER);
                P.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
                c.drawText("T", 12, 18, P);
                break;
            }
            case "shape": {
                c.drawRoundRect(new RectF(3, 3, 13, 13), 2, 2, P);
                c.drawCircle(16, 16, 5, P);
                break;
            }
            case "film": {
                c.drawRoundRect(new RectF(3, 5, 21, 19), 2, 2, P);
                c.drawLine(8, 5, 8, 19, P);
                c.drawLine(16, 5, 16, 19, P);
                c.drawLine(3, 12, 8, 12, P);
                c.drawLine(16, 12, 21, 12, P);
                break;
            }
            case "shader": {
                Path p = new Path();
                p.moveTo(3, 8);
                p.cubicTo(7, 5, 11, 11, 21, 7);
                p.moveTo(3, 13);
                p.cubicTo(7, 10, 11, 16, 21, 12);
                p.moveTo(3, 18);
                p.cubicTo(7, 15, 11, 21, 21, 17);
                c.drawPath(p, P);
                break;
            }
            case "check": {
                Path p = new Path();
                p.moveTo(5, 12.5f);
                p.lineTo(10, 17.5f);
                p.lineTo(19, 6.5f);
                c.drawPath(p, P);
                break;
            }
            case "close": {
                c.drawLine(6, 6, 18, 18, P);
                c.drawLine(18, 6, 6, 18, P);
                break;
            }
            case "fit": {
                Path p = new Path();
                p.moveTo(4, 9);
                p.lineTo(4, 4);
                p.lineTo(9, 4);
                p.moveTo(15, 4);
                p.lineTo(20, 4);
                p.lineTo(20, 9);
                p.moveTo(20, 15);
                p.lineTo(20, 20);
                p.lineTo(15, 20);
                p.moveTo(9, 20);
                p.lineTo(4, 20);
                p.lineTo(4, 15);
                c.drawPath(p, P);
                break;
            }
            case "zoomin":
            case "zoomout": {
                c.drawCircle(10.5f, 10.5f, 6.5f, P);
                c.drawLine(15.5f, 15.5f, 21, 21, P);
                c.drawLine(7.5f, 10.5f, 13.5f, 10.5f, P);
                if (id.equals("zoomin")) c.drawLine(10.5f, 7.5f, 10.5f, 13.5f, P);
                break;
            }
            case "layers": {
                Path p = new Path();
                p.moveTo(12, 3);
                p.lineTo(21, 8);
                p.lineTo(12, 13);
                p.lineTo(3, 8);
                p.close();
                c.drawPath(p, P);
                Path q = new Path();
                q.moveTo(4, 12);
                q.lineTo(12, 16.5f);
                q.lineTo(20, 12);
                c.drawPath(q, P);
                Path r = new Path();
                r.moveTo(4, 16);
                r.lineTo(12, 20.5f);
                r.lineTo(20, 16);
                c.drawPath(r, P);
                break;
            }
            case "image": {
                c.drawRoundRect(new RectF(3, 4, 21, 20), 2, 2, P);
                c.drawCircle(8, 9, 1.8f, P);
                Path p = new Path();
                p.moveTo(4, 18);
                p.lineTo(10, 12);
                p.lineTo(14, 16);
                p.lineTo(17, 13);
                p.lineTo(20, 16.5f);
                c.drawPath(p, P);
                break;
            }
            case "drag": {
                P.setStyle(Paint.Style.FILL);
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 2; col++) {
                        c.drawCircle(9 + col * 6, 6 + row * 6, 1.5f, P);
                    }
                }
                break;
            }
            case "grid": {
                c.drawRoundRect(new RectF(4, 4, 11, 11), 1.5f, 1.5f, P);
                c.drawRoundRect(new RectF(13, 4, 20, 11), 1.5f, 1.5f, P);
                c.drawRoundRect(new RectF(4, 13, 11, 20), 1.5f, 1.5f, P);
                c.drawRoundRect(new RectF(13, 13, 20, 20), 1.5f, 1.5f, P);
                break;
            }
            case "palette": {
                Path p = new Path();
                p.addOval(new RectF(3, 3, 21, 21), Path.Direction.CW);
                c.drawPath(p, P);
                P.setStyle(Paint.Style.FILL);
                c.drawCircle(8, 9, 1.6f, P);
                c.drawCircle(15, 8, 1.6f, P);
                c.drawCircle(17, 15, 1.6f, P);
                c.drawCircle(9, 16, 1.6f, P);
                break;
            }
            case "sun": {
                c.drawCircle(12, 12, 4.5f, P);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    float x0 = (float) (12 + Math.cos(a) * 7);
                    float y0 = (float) (12 + Math.sin(a) * 7);
                    float x1 = (float) (12 + Math.cos(a) * 9.5);
                    float y1 = (float) (12 + Math.sin(a) * 9.5);
                    c.drawLine(x0, y0, x1, y1, P);
                }
                break;
            }
            case "flip": {
                Path p = new Path();
                p.moveTo(3, 12);
                p.lineTo(9, 6);
                p.lineTo(9, 18);
                p.close();
                c.drawPath(p, P);
                Path q = new Path();
                q.moveTo(21, 12);
                q.lineTo(15, 6);
                q.lineTo(15, 18);
                q.close();
                c.drawPath(q, P);
                break;
            }
            case "rotate": {
                c.drawArc(new RectF(4, 4, 20, 20), -60, 280, false, P);
                Path p = new Path();
                p.moveTo(17.5f, 2.5f);
                p.lineTo(19.5f, 8.5f);
                p.lineTo(13.5f, 8f);
                c.drawPath(p, P);
                break;
            }
            case "clock": {
                c.drawCircle(12, 12, 8.5f, P);
                Path p = new Path();
                p.moveTo(12, 7);
                p.lineTo(12, 12);
                p.lineTo(15.5f, 14);
                c.drawPath(p, P);
                break;
            }
            case "star": {
                P.setStyle(Paint.Style.FILL);
                Path p = new Path();
                p.moveTo(12, 3);
                p.lineTo(14.6f, 9.2f);
                p.lineTo(21, 9.8f);
                p.lineTo(16.2f, 14);
                p.lineTo(17.6f, 20.5f);
                p.lineTo(12, 17.2f);
                p.lineTo(6.4f, 20.5f);
                p.lineTo(7.8f, 14);
                p.lineTo(3, 9.8f);
                p.lineTo(9.4f, 9.2f);
                p.close();
                c.drawPath(p, P);
                break;
            }
            case "dot": {
                P.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 12, 3.2f, P);
                break;
            }
            default: {
                c.drawCircle(12, 12, 8, P);
                break;
            }
        }
    }
}
