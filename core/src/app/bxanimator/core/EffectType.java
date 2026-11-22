package app.bxanimator.core;

/**
 * Every shader effect the renderer knows. The same ids drive the software
 * renderer used for preview and export, and the GLSL preview in the Shader Lab.
 */
public enum EffectType {

    CEL_OUTLINE("Cel Outline",
            "Ink contours around the artwork, like a cleaned up cel sheet.",
            "cel",
            new String[]{"ink", "width"},
            new String[]{"Ink strength", "Line width"},
            new float[]{0.55f, 1.5f}),

    COLOR_GRADE("Colour Grade",
            "Saturation, contrast, warmth and lifted blacks in one pass.",
            "grade",
            new String[]{"saturation", "contrast", "warmth", "lift"},
            new String[]{"Saturation", "Contrast", "Warmth", "Lift"},
            new float[]{1f, 1f, 0f, 0f}),

    BLOOM("Bloom",
            "Soft light bleed around the bright areas of the frame.",
            "bloom",
            new String[]{"threshold", "radius"},
            new String[]{"Threshold", "Radius"},
            new float[]{0.62f, 0.55f}),

    CHROMATIC("Chromatic Split",
            "Separates the colour channels towards the edges of the frame.",
            "chroma",
            new String[]{"amount"},
            new String[]{"Split"},
            new float[]{0.5f}),

    GRAIN("Film Grain",
            "Animated grain with a fine, filmic structure.",
            "grain",
            new String[]{"amount"},
            new String[]{"Amount"},
            new float[]{0.3f}),

    VIGNETTE("Vignette",
            "Darkens the corners to pull the eye to the centre.",
            "vignette",
            new String[]{"softness"},
            new String[]{"Softness"},
            new float[]{0.5f}),

    SPEED_LINES("Speed Lines",
            "Radial action lines that stream out of the frame centre.",
            "speedlines",
            new String[]{"density", "length"},
            new String[]{"Density", "Length"},
            new float[]{0.5f, 0.6f}),

    HALFTONE("Screentone",
            "Manga style dot screen that follows the shadows.",
            "halftone",
            new String[]{"scale"},
            new String[]{"Dot size"},
            new float[]{0.5f}),

    WATERCOLOR("Watercolour",
            "Bleeds the colours and softens the edges like painted paper.",
            "watercolor",
            new String[]{"bleed"},
            new String[]{"Bleed"},
            new float[]{0.5f}),

    REMASTER("Modern Remaster",
            "Cleans an old frame: denoise, sharpen, restore colour and contrast.",
            "remaster",
            new String[]{"sharpen", "denoise", "color"},
            new String[]{"Sharpen", "Denoise", "Colour restore"},
            new float[]{0.6f, 0.4f, 0.5f}),

    VHS("Tape",
            "Scanlines, colour bleed and tracking wobble of an old tape.",
            "vhs",
            new String[]{"tracking"},
            new String[]{"Tracking"},
            new float[]{0.5f}),

    SHARPEN("Sharpen",
            "Unsharp mask for crisp lines and clean edges.",
            "sharpen",
            new String[]{"amount"},
            new String[]{"Amount"},
            new float[]{0.6f});

    public final String label;
    public final String hint;
    /** Fragment shader name in the Shader Lab. */
    public final String glsl;
    public final String[] params;
    public final String[] paramLabels;
    public final float[] defaults;

    EffectType(String label, String hint, String glsl, String[] params, String[] paramLabels, float[] defaults) {
        this.label = label;
        this.hint = hint;
        this.glsl = glsl;
        this.params = params;
        this.paramLabels = paramLabels;
        this.defaults = defaults;
    }

    public float defaultOf(String param) {
        for (int i = 0; i < params.length; i++) {
            if (params[i].equals(param)) return defaults[i];
        }
        return 0f;
    }

    public static EffectType byName(String name, EffectType def) {
        if (name == null) return def;
        for (EffectType t : values()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        return def;
    }
}
