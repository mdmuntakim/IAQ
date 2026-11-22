package app.bxanimator.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A puppet rig: an ordered list of bones plus the palette used to draw it. */
public final class Rig {

    public String id = "rig";
    public String name = "Rig";
    /** Silhouette style: hero, heroine, chibi, villain, mecha. */
    public String style = "hero";
    public int ink = 0xFF17131C;
    public int eye = 0xFF3E6FA8;
    public int accent = 0xFFFF7A18;
    public final List<Bone> bones = new ArrayList<>();

    public Bone bone(String boneId) {
        if (boneId == null) return null;
        for (Bone b : bones) {
            if (b.id.equals(boneId)) return b;
        }
        return null;
    }

    public Rig copy() {
        Rig r = new Rig();
        r.id = id;
        r.name = name;
        r.style = style;
        r.ink = ink;
        r.eye = eye;
        r.accent = accent;
        for (Bone b : bones) r.bones.add(b.copy());
        return r;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("style", style);
        m.put("ink", Json.hex(ink));
        m.put("eye", Json.hex(eye));
        m.put("accent", Json.hex(accent));
        List<Object> list = new ArrayList<>(bones.size());
        for (Bone b : bones) list.add(b.toJson());
        m.put("bones", list);
        return m;
    }

    public static Rig fromJson(Map<String, Object> m) {
        Rig r = new Rig();
        r.id = Json.str(m, "id", "rig");
        r.name = Json.str(m, "name", "Rig");
        r.style = Json.str(m, "style", "hero");
        r.ink = Json.color(m.get("ink"), 0xFF17131C);
        r.eye = Json.color(m.get("eye"), 0xFF3E6FA8);
        r.accent = Json.color(m.get("accent"), 0xFFFF7A18);
        for (Object o : Json.list(m.get("bones"))) {
            r.bones.add(Bone.fromJson(Json.map(o)));
        }
        return r;
    }
}
