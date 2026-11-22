package iaq;

/**
 * Colour space helpers.  The pipeline resamples and blurs in linear light
 * (physically correct) and grades in sRGB-encoded light (perceptually even).
 */
public final class Color {

    private Color() {
    }

    public static float srgbToLinear(double v) {
        if (v <= 0.04045) return (float) (v / 12.92);
        return (float) Math.pow((v + 0.055) / 1.055, 2.4);
    }

    public static float linearToSrgb(double v) {
        if (v <= 0.0031308) return (float) (v * 12.92);
        return (float) (1.055 * Math.pow(v, 1.0 / 2.4) - 0.055);
    }

    /** Rec. 709 luma of linear RGB. */
    public static float luma(float r, float g, float b) {
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    /** Rec. 601 luma, used for the perceptual (sRGB encoded) stages. */
    public static float luma601(float r, float g, float b) {
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }

    public static float clamp01(double v) {
        return v <= 0 ? 0f : (v >= 1 ? 1f : (float) v);
    }

    public static float clamp(double v, double lo, double hi) {
        return v <= lo ? (float) lo : (v >= hi ? (float) hi : (float) v);
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = (x - edge0) / (edge1 - edge0);
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return t * t * (3 - 2 * t);
    }

    /** Soft highlight shoulder: linear below the knee, exponential above it. */
    public static float shoulder(float x, float knee) {
        if (x <= knee) return x;
        float room = 1f - knee;
        float over = (x - knee) / room;
        return knee + room * (1f - (float) Math.exp(-over));
    }
}
