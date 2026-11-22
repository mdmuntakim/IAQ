package app.bxanimator.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** A scene: background, layers, camera and its own shader stack. */
public final class Scene {

    public String id = Ids.next("scene");
    public String name = "Scene";
    public double duration = 8.0;
    public int bgTop = 0xFF16233A;
    public int bgBottom = 0xFF06080E;
    public String cameraId;
    public final List<Layer> layers = new ArrayList<>();
    public final List<Effect> effects = new ArrayList<>();

    public static Scene create(String name, double duration) {
        Scene s = new Scene();
        s.name = name;
        s.duration = Math.max(0.5, duration);
        return s;
    }

    public Layer layer(String layerId) {
        if (layerId == null) return null;
        for (Layer l : layers) {
            if (l.id.equals(layerId)) return l;
        }
        return null;
    }

    public int indexOf(String layerId) {
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).id.equals(layerId)) return i;
        }
        return -1;
    }

    public Layer camera() {
        Layer l = layer(cameraId);
        if (l != null && l.type == LayerType.CAMERA) return l;
        for (Layer candidate : layers) {
            if (candidate.type == LayerType.CAMERA) {
                cameraId = candidate.id;
                return candidate;
            }
        }
        return null;
    }

    public Layer ensureCamera(float centerX, float centerY) {
        Layer cam = camera();
        if (cam != null) return cam;
        cam = Layer.create(LayerType.CAMERA, "Camera");
        cam.setKey(Layer.X, 0, centerX);
        cam.setKey(Layer.Y, 0, centerY);
        cam.setKey(Layer.SCALE, 0, 1f);
        cam.setKey(Layer.ROT, 0, 0f);
        layers.add(cam);
        cameraId = cam.id;
        return cam;
    }

    public Layer add(Layer layer) {
        layers.add(layer);
        return layer;
    }

    /** Removes a layer; the camera is never removed. */
    public boolean remove(Layer layer) {
        if (layer == null || layer.type == LayerType.CAMERA) return false;
        return layers.remove(layer);
    }

    public void moveLayer(int from, int to) {
        if (from < 0 || from >= layers.size()) return;
        to = AnimMath.clamp(to, 0, layers.size() - 1);
        Layer l = layers.remove(from);
        layers.add(to, l);
    }

    public boolean removeEffect(Effect effect) {
        return effects.remove(effect);
    }

    public java.util.Set<Double> keyTimes() {
        java.util.Set<Double> times = new TreeSet<>();
        for (Layer l : layers) times.addAll(l.keyTimes());
        return times;
    }

    public Scene copy() {
        Scene s = new Scene();
        s.id = id;
        s.name = name;
        s.duration = duration;
        s.bgTop = bgTop;
        s.bgBottom = bgBottom;
        s.cameraId = cameraId;
        for (Layer l : layers) s.layers.add(l.copy());
        for (Effect e : effects) s.effects.add(e.copy());
        return s;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("duration", duration);
        m.put("bgTop", Json.hex(bgTop));
        m.put("bgBottom", Json.hex(bgBottom));
        if (cameraId != null) m.put("camera", cameraId);
        List<Object> ls = new ArrayList<>();
        for (Layer l : layers) ls.add(l.toJson());
        m.put("layers", ls);
        List<Object> es = new ArrayList<>();
        for (Effect e : effects) es.add(e.toJson());
        m.put("effects", es);
        return m;
    }

    public static Scene fromJson(Map<String, Object> m) {
        Scene s = new Scene();
        s.id = Json.str(m, "id", Ids.next("scene"));
        s.name = Json.str(m, "name", "Scene");
        s.duration = Math.max(0.5, Json.num(m, "duration", 8));
        s.bgTop = Json.color(m.get("bgTop"), 0xFF16233A);
        s.bgBottom = Json.color(m.get("bgBottom"), 0xFF06080E);
        s.cameraId = m.get("camera") instanceof String ? (String) m.get("camera") : null;
        for (Object o : Json.list(m.get("layers"))) {
            s.layers.add(Layer.fromJson(Json.map(o)));
        }
        for (Object o : Json.list(m.get("effects"))) {
            s.effects.add(Effect.fromJson(Json.map(o)));
        }
        return s;
    }
}
