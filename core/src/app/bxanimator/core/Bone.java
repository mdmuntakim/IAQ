package app.bxanimator.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One bone of a puppet rig. A bone starts at its pivot and extends along its
 * local -Y axis for {@code length} units. The pivot is expressed in the parent
 * bone's local space (before the parent rotation), so rotating a bone swings all
 * of its children around it.
 */
public final class Bone {

    public String id;
    public String name;
    /** Parent bone id, or null for a root bone. */
    public String parent;
    public float pivotX;
    public float pivotY;
    /** Rest angle in degrees, clockwise, 0 = up. */
    public float restAngle;
    public float length;
    public float width;
    public int color;
    public Part part;

    public Bone(String id, String name, String parent, float pivotX, float pivotY,
                float restAngle, float length, float width, int color, Part part) {
        this.id = id;
        this.name = name;
        this.parent = parent;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.restAngle = restAngle;
        this.length = length;
        this.width = width;
        this.color = color;
        this.part = part;
    }

    public Bone copy() {
        return new Bone(id, name, parent, pivotX, pivotY, restAngle, length, width, color, part);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        if (parent != null) m.put("parent", parent);
        m.put("px", pivotX);
        m.put("py", pivotY);
        m.put("angle", restAngle);
        m.put("length", length);
        m.put("width", width);
        m.put("color", Json.hex(color));
        m.put("part", part.name());
        return m;
    }

    public static Bone fromJson(Map<String, Object> m) {
        return new Bone(
                Json.str(m, "id", "bone"),
                Json.str(m, "name", "Bone"),
                m.get("parent") == null ? null : Json.str(m, "parent", null),
                Json.fnum(m, "px", 0),
                Json.fnum(m, "py", 0),
                Json.fnum(m, "angle", 0),
                Json.fnum(m, "length", 40),
                Json.fnum(m, "width", 16),
                Json.color(m.get("color"), 0xFFE8C39E),
                Part.byName(Json.str(m, "part", "PROP"), Part.PROP));
    }
}
