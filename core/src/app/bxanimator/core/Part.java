package app.bxanimator.core;

/** The kind of body part a bone draws. The renderer maps each part to a shape. */
public enum Part {
    PELVIS("Pelvis"),
    TORSO("Torso"),
    NECK("Neck"),
    HEAD("Head"),
    HAIR_BACK("Hair back"),
    HAIR_FRONT("Hair front"),
    UPPER_ARM("Upper arm"),
    LOWER_ARM("Lower arm"),
    HAND("Hand"),
    THIGH("Thigh"),
    SHIN("Shin"),
    FOOT("Foot"),
    SKIRT("Skirt"),
    CAPE("Cape"),
    SCARF("Scarf"),
    TAIL("Tail"),
    SWORD("Sword"),
    WING("Wing"),
    PROP("Prop");

    public final String label;

    Part(String label) {
        this.label = label;
    }

    public static Part byName(String name, Part def) {
        if (name == null) return def;
        for (Part p : values()) {
            if (p.name().equalsIgnoreCase(name)) return p;
        }
        return def;
    }
}
