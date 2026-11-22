package app.bxanimator.core;

/** What a layer contains. */
public enum LayerType {
    RIG("Character"),
    DRAW("Drawing"),
    SHAPE("Shape"),
    TEXT("Text"),
    IMAGE("Image"),
    CAMERA("Camera");

    public final String label;

    LayerType(String label) {
        this.label = label;
    }

    public static LayerType byName(String name, LayerType def) {
        if (name == null) return def;
        for (LayerType t : values()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        return def;
    }
}
