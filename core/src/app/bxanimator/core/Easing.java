package app.bxanimator.core;

import java.util.Locale;

/**
 * Interpolation curves between two keyframes. The curve belongs to the keyframe
 * on the left of a segment, which is how every timeline editor works.
 */
public enum Easing {

    LINEAR("Linear"),
    HOLD("Hold"),
    STEP_END("Step"),
    SINE_IN("Sine in"),
    SINE_OUT("Sine out"),
    SINE_IN_OUT("Sine in/out"),
    QUAD_IN("Quad in"),
    QUAD_OUT("Quad out"),
    QUAD_IN_OUT("Quad in/out"),
    CUBIC_IN_OUT("Cubic in/out"),
    BACK_OUT("Back out"),
    ELASTIC_OUT("Elastic out"),
    BOUNCE_OUT("Bounce out");

    public final String label;

    Easing(String label) {
        this.label = label;
    }

    /** Maps normalised segment time 0..1 to eased 0..1. */
    public float apply(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        switch (this) {
            case HOLD:
                return 0f;
            case STEP_END:
                return 1f;
            case SINE_IN:
                return 1f - (float) Math.cos(t * Math.PI / 2f);
            case SINE_OUT:
                return (float) Math.sin(t * Math.PI / 2f);
            case SINE_IN_OUT:
                return -(float) Math.cos(Math.PI * t) / 2f + 0.5f;
            case QUAD_IN:
                return t * t;
            case QUAD_OUT:
                return 1f - (1f - t) * (1f - t);
            case QUAD_IN_OUT:
                return t < 0.5f ? 2f * t * t : 1f - 2f * (1f - t) * (1f - t);
            case CUBIC_IN_OUT:
                return t < 0.5f ? 4f * t * t * t : 1f - 4f * (1f - t) * (1f - t) * (1f - t);
            case BACK_OUT: {
                float c1 = 1.70158f;
                float c3 = c1 + 1f;
                float u = t - 1f;
                return 1f + c3 * u * u * u + c1 * u * u;
            }
            case ELASTIC_OUT: {
                if (t == 0f) return 0f;
                if (t == 1f) return 1f;
                float c4 = (2f * (float) Math.PI) / 3f;
                return (float) Math.pow(2f, -10f * t) * (float) Math.sin((t * 10f - 0.75f) * c4) + 1f;
            }
            case BOUNCE_OUT: {
                float n1 = 7.5625f;
                float d1 = 2.75f;
                if (t < 1f / d1) return n1 * t * t;
                if (t < 2f / d1) {
                    float u = t - 1.5f / d1;
                    return n1 * u * u + 0.75f;
                }
                if (t < 2.5f / d1) {
                    float u = t - 2.25f / d1;
                    return n1 * u * u + 0.9375f;
                }
                float u = t - 2.625f / d1;
                return n1 * u * u + 0.984375f;
            }
            case LINEAR:
            default:
                return t;
        }
    }

    public String id() {
        return name().toLowerCase(Locale.US);
    }

    public static Easing byId(String id, Easing def) {
        if (id == null) return def;
        String norm = id.trim().toUpperCase(Locale.US).replace('-', '_').replace(' ', '_');
        for (Easing e : values()) {
            if (e.name().equals(norm) || e.id().equalsIgnoreCase(id)) return e;
        }
        return def;
    }
}
