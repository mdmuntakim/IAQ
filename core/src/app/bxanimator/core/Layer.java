package app.bxanimator.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A layer of a scene: a puppet rig, a drawing, a shape, text, an image or the
 * camera. Everything animatable is a named channel holding a {@link Track}.
 */
public final class Layer {

    public static final String X = "x";
    public static final String Y = "y";
    public static final String SCALE = "scale";
    public static final String ROT = "rot";
    public static final String OPACITY = "opacity";
    public static final String FACE_BLINK = "face.blink";
    public static final String FACE_BROW = "face.brow";
    public static final String FACE_MOUTH = "face.mouth";
    public static final String FACE_LOOK = "face.look";

    public String id = Ids.next("layer");
    public String name = "Layer";
    public LayerType type = LayerType.SHAPE;
    public boolean visible = true;
    public boolean locked = false;
    public int color = 0xFFFF7A18;

    public final Map<String, Track> channels = new LinkedHashMap<>();

    // Character
    public Rig rig;

    // Text
    public String text = "";
    public float textSize = 72f;

    // Shape
    public String shape = "rect";
    public float shapeWidth = 320f;
    public float shapeHeight = 200f;
    public int shapeFill = 0xFF2C3E50;

    // Image
    public String asset;

    // Drawing
    public final List<Stroke> strokes = new ArrayList<>();
    public boolean cumulativeStrokes = true;

    public static Layer create(LayerType type, String name) {
        Layer layer = new Layer();
        layer.type = type;
        layer.name = name;
        layer.id = Ids.next(type.name().toLowerCase(java.util.Locale.US));
        if (type == LayerType.CAMERA) {
            layer.color = 0xFF6EA8FE;
        }
        return layer;
    }

    public static String boneChannel(String boneId) {
        return "b." + boneId + ".rot";
    }

    public Track track(String channel) {
        Track t = channels.get(channel);
        if (t == null) {
            t = new Track();
            channels.put(channel, t);
        }
        return t;
    }

    public Track trackOrNull(String channel) {
        return channels.get(channel);
    }

    public float sample(String channel, double time, float fallback) {
        Track t = channels.get(channel);
        return t == null ? fallback : t.sample(time, fallback);
    }

    public float x(double t) {
        return sample(X, t, 0f);
    }

    public float y(double t) {
        return sample(Y, t, 0f);
    }

    public float scale(double t) {
        return sample(SCALE, t, 1f);
    }

    public float rotation(double t) {
        return sample(ROT, t, 0f);
    }

    public float opacity(double t) {
        return AnimMath.clamp(sample(OPACITY, t, 1f), 0f, 1f);
    }

    public void setKey(String channel, double time, float value, Easing ease) {
        track(channel).put(time, value, ease);
    }

    public void setKey(String channel, double time, float value) {
        setKey(channel, time, value, null);
    }

    /** Every distinct key time of the layer, used for the collapsed timeline row. */
    public Set<Double> keyTimes() {
        Set<Double> times = new TreeSet<>();
        for (Track t : channels.values()) times.addAll(t.times());
        return times;
    }

    public boolean hasKeys() {
        for (Track t : channels.values()) {
            if (!t.isEmpty()) return true;
        }
        return false;
    }

    /** The bone angle at a time, falling back to the rest pose. */
    public float boneAngle(Bone bone, double time) {
        return sample(boneChannel(bone.id), time, bone.restAngle);
    }

    public Mat2D transform(double time) {
        return Mat2D.compose(x(time), y(time), rotation(time), scale(time), scale(time));
    }

    public Stroke addStroke(int frame, int color, float width, boolean erase) {
        Stroke s = new Stroke(frame, color, width, erase);
        strokes.add(s);
        return s;
    }

    public List<Stroke> strokesAt(int frame) {
        List<Stroke> out = new ArrayList<>();
        if (cumulativeStrokes) {
            for (Stroke s : strokes) {
                if (s.frame <= frame) out.add(s);
            }
        } else {
            int best = -1;
            for (Stroke s : strokes) {
                if (s.frame <= frame && s.frame > best) best = s.frame;
            }
            if (best >= 0) {
                for (Stroke s : strokes) {
                    if (s.frame == best) out.add(s);
                }
            }
        }
        return out;
    }

    public int lastStrokeFrame() {
        int last = 0;
        for (Stroke s : strokes) last = Math.max(last, s.frame);
        return last;
    }

    public Layer copy() {
        Layer l = new Layer();
        l.id = id;
        l.name = name;
        l.type = type;
        l.visible = visible;
        l.locked = locked;
        l.color = color;
        for (Map.Entry<String, Track> e : channels.entrySet()) {
            l.channels.put(e.getKey(), e.getValue().copy());
        }
        l.rig = rig == null ? null : rig.copy();
        l.text = text;
        l.textSize = textSize;
        l.shape = shape;
        l.shapeWidth = shapeWidth;
        l.shapeHeight = shapeHeight;
        l.shapeFill = shapeFill;
        l.asset = asset;
        for (Stroke s : strokes) l.strokes.add(s.copy());
        l.cumulativeStrokes = cumulativeStrokes;
        return l;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("type", type.name());
        m.put("visible", visible);
        m.put("locked", locked);
        m.put("color", Json.hex(color));
        if (rig != null) m.put("rig", rig.toJson());
        if (type == LayerType.TEXT) {
            m.put("text", text);
            m.put("textSize", textSize);
        }
        if (type == LayerType.SHAPE) {
            m.put("shape", shape);
            m.put("shapeWidth", shapeWidth);
            m.put("shapeHeight", shapeHeight);
            m.put("shapeFill", Json.hex(shapeFill));
        }
        if (asset != null) m.put("asset", asset);
        if (!strokes.isEmpty()) {
            List<Object> list = new ArrayList<>();
            for (Stroke s : strokes) list.add(s.toJson());
            m.put("strokes", list);
            m.put("cumulative", cumulativeStrokes);
        }
        Map<String, Object> chans = new LinkedHashMap<>();
        for (Map.Entry<String, Track> e : channels.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            List<Object> keys = new ArrayList<>();
            for (Key k : e.getValue().keys) {
                keys.add(Json.obj("t", k.time, "v", k.value, "e", k.ease.id()));
            }
            chans.put(e.getKey(), keys);
        }
        if (!chans.isEmpty()) m.put("channels", chans);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Layer fromJson(Map<String, Object> m) {
        Layer l = new Layer();
        l.id = Json.str(m, "id", Ids.next("layer"));
        l.name = Json.str(m, "name", "Layer");
        l.type = LayerType.byName(Json.str(m, "type", "SHAPE"), LayerType.SHAPE);
        l.visible = Json.bool(m, "visible", true);
        l.locked = Json.bool(m, "locked", false);
        l.color = Json.color(m.get("color"), 0xFFFF7A18);
        if (m.get("rig") instanceof Map) {
            l.rig = Rig.fromJson(Json.map(m.get("rig")));
        }
        l.text = Json.str(m, "text", "");
        l.textSize = Json.fnum(m, "textSize", 72f);
        l.shape = Json.str(m, "shape", "rect");
        l.shapeWidth = Json.fnum(m, "shapeWidth", 320f);
        l.shapeHeight = Json.fnum(m, "shapeHeight", 200f);
        l.shapeFill = Json.color(m.get("shapeFill"), 0xFF2C3E50);
        l.asset = m.get("asset") instanceof String ? (String) m.get("asset") : null;
        for (Object o : Json.list(m.get("strokes"))) {
            l.strokes.add(Stroke.fromJson(Json.map(o)));
        }
        l.cumulativeStrokes = Json.bool(m, "cumulative", true);
        Map<String, Object> chans = Json.map(m.get("channels"));
        for (Map.Entry<String, Object> e : chans.entrySet()) {
            Track t = new Track();
            for (Object o : Json.list(e.getValue())) {
                Map<String, Object> km = Json.map(o);
                t.keys.add(new Key(
                        Json.num(km, "t", 0),
                        Json.fnum(km, "v", 0),
                        Easing.byId(Json.str(km, "e", "linear"), Easing.LINEAR)));
            }
            java.util.Collections.sort(t.keys);
            l.channels.put(e.getKey(), t);
        }
        return l;
    }
}
