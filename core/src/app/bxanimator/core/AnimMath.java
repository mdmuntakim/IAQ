package app.bxanimator.core;

/** Small maths helpers shared by the timeline, the rig solver and the renderer. */
public final class AnimMath {

    private AnimMath() {
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float invLerp(float a, float b, float v) {
        if (Math.abs(b - a) < 1e-6f) return 0f;
        return (v - a) / (b - a);
    }

    public static float smoothstep(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static float degToRad(float deg) {
        return deg * (float) Math.PI / 180f;
    }

    public static float radToDeg(float rad) {
        return rad * 180f / (float) Math.PI;
    }

    public static float wrapDegrees(float deg) {
        deg = deg % 360f;
        if (deg > 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }

    /** Shortest rotation from {@code from} to {@code to}, in degrees. */
    public static float deltaDegrees(float from, float to) {
        return wrapDegrees(to - from);
    }

    /** Frame number for a time in seconds. */
    public static int frameAt(double time, int fps) {
        return (int) Math.round(time * fps);
    }

    public static double timeAt(int frame, int fps) {
        return fps <= 0 ? 0 : (double) frame / fps;
    }

    public static String timecode(double seconds, int fps) {
        if (seconds < 0) seconds = 0;
        int totalFrames = (int) Math.round(seconds * fps);
        int f = totalFrames % Math.max(1, fps);
        int totalSeconds = totalFrames / Math.max(1, fps);
        int s = totalSeconds % 60;
        int m = (totalSeconds / 60) % 60;
        int h = totalSeconds / 3600;
        if (h > 0) return String.format(java.util.Locale.US, "%d:%02d:%02d:%02d", h, m, s, f);
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", m, s, f);
    }

    public static String shortNumber(float v) {
        if (Math.abs(v - Math.round(v)) < 0.005f) return String.valueOf(Math.round(v));
        return String.format(java.util.Locale.US, "%.2f", v);
    }
}
