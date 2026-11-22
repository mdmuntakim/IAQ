package app.bxanimator.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** One entry of a scene's effect stack. Effects are applied in list order. */
public final class Effect {

    public EffectType type = EffectType.COLOR_GRADE;
    public boolean enabled = true;
    public float intensity = 0.6f;
    public final Map<String, Float> params = new LinkedHashMap<>();

    public static Effect of(EffectType type, float intensity) {
        Effect e = new Effect();
        e.type = type;
        e.intensity = intensity;
        for (int i = 0; i < type.params.length; i++) {
            e.params.put(type.params[i], type.defaults[i]);
        }
        return e;
    }

    public float param(String name) {
        Float v = params.get(name);
        return v == null ? type.defaultOf(name) : v;
    }

    public void setParam(String name, float value) {
        params.put(name, value);
    }

    public Effect copy() {
        Effect e = new Effect();
        e.type = type;
        e.enabled = enabled;
        e.intensity = intensity;
        e.params.putAll(params);
        return e;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type.name());
        m.put("enabled", enabled);
        m.put("intensity", intensity);
        Map<String, Object> p = new LinkedHashMap<>();
        for (Map.Entry<String, Float> e : params.entrySet()) p.put(e.getKey(), e.getValue());
        if (!p.isEmpty()) m.put("params", p);
        return m;
    }

    public static Effect fromJson(Map<String, Object> m) {
        EffectType type = EffectType.byName(Json.str(m, "type", "COLOR_GRADE"), EffectType.COLOR_GRADE);
        Effect e = Effect.of(type, Json.fnum(m, "intensity", 0.6f));
        e.enabled = Json.bool(m, "enabled", true);
        Map<String, Object> p = Json.map(m.get("params"));
        for (Map.Entry<String, Object> entry : p.entrySet()) {
            e.params.put(entry.getKey(), (float) Json.number(entry.getValue(), 0));
        }
        return e;
    }
}
