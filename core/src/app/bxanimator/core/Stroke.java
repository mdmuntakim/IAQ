package app.bxanimator.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A freehand stroke on a drawing layer. Strokes are stored on the frame they
 * belong to, which makes frame by frame animation possible without one bitmap
 * per frame.
 */
public final class Stroke {

    public int frame;
    public int color = 0xFF101018;
    public float width = 6f;
    public boolean erase;
    public final List<float[]> points = new ArrayList<>();

    public Stroke() {
    }

    public Stroke(int frame, int color, float width, boolean erase) {
        this.frame = frame;
        this.color = color;
        this.width = width;
        this.erase = erase;
    }

    public void addPoint(float x, float y) {
        if (!points.isEmpty()) {
            float[] last = points.get(points.size() - 1);
            float dx = x - last[0];
            float dy = y - last[1];
            if (dx * dx + dy * dy < 1.5f) return;
        }
        points.add(new float[]{x, y});
    }

    public Stroke copy() {
        Stroke s = new Stroke(frame, color, width, erase);
        for (float[] p : points) s.points.add(new float[]{p[0], p[1]});
        return s;
    }

    public Map<String, Object> toJson() {
        List<Object> flat = new ArrayList<>(points.size() * 2);
        for (float[] p : points) {
            flat.add(p[0]);
            flat.add(p[1]);
        }
        return Json.obj(
                "frame", frame,
                "color", Json.hex(color),
                "width", width,
                "erase", erase,
                "pts", flat);
    }

    public static Stroke fromJson(Map<String, Object> m) {
        Stroke s = new Stroke(
                Json.inum(m, "frame", 0),
                Json.color(m.get("color"), 0xFF101018),
                Json.fnum(m, "width", 6f),
                Json.bool(m, "erase", false));
        List<Object> flat = Json.list(m.get("pts"));
        for (int i = 0; i + 1 < flat.size(); i += 2) {
            float x = (float) Json.number(flat.get(i), 0);
            float y = (float) Json.number(flat.get(i + 1), 0);
            s.points.add(new float[]{x, y});
        }
        return s;
    }
}
