package app.bxanimator.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Ready to animate puppet rigs. Everything is built from plain bones, so a rig
 * can be edited, duplicated and saved inside a project like any other layer.
 */
public final class RigLibrary {

    public static final String HERO = "hero";
    public static final String HEROINE = "heroine";
    public static final String CHIBI = "chibi";
    public static final String VILLAIN = "villain";
    public static final String MECHA = "mecha";

    private static final String[] KEYS = {HERO, HEROINE, CHIBI, VILLAIN, MECHA};

    private RigLibrary() {
    }

    public static String[] keys() {
        return KEYS.clone();
    }

    public static String label(String key) {
        if (key == null) return "Rig";
        switch (key) {
            case HERO: return "Hero";
            case HEROINE: return "Heroine";
            case CHIBI: return "Chibi";
            case VILLAIN: return "Villain";
            case MECHA: return "Mecha";
            default: return "Rig";
        }
    }

    public static List<Rig> all() {
        List<Rig> rigs = new ArrayList<>();
        for (String key : KEYS) rigs.add(create(key));
        return rigs;
    }

    public static Rig create(String key) {
        if (key == null) key = HERO;
        switch (key) {
            case HEROINE: {
                Body b = new Body();
                b.style = "heroine";
                b.skin = 0xFFF7D5B5;
                b.hair = 0xFF5C2B46;
                b.cloth = 0xFFF1E9F5;
                b.pants = 0xFF3B2C55;
                b.shoes = 0xFF2A2233;
                b.eye = 0xFFC0567A;
                b.accent = 0xFFFF9EC4;
                b.shoulder = 44;
                b.torsoW = 112;
                b.pelvisW = 96;
                b.hairBackLen = 230;
                b.hairBackW = 168;
                b.skirt = true;
                b.skirtLen = 92;
                b.skirtW = 190;
                b.armUpper = 96;
                b.armLower = 90;
                b.thigh = 160;
                return build(key, "Heroine", b);
            }
            case CHIBI: {
                Body b = new Body();
                b.style = "chibi";
                b.skin = 0xFFF9DCC0;
                b.hair = 0xFF4A6FA5;
                b.cloth = 0xFFF2C14E;
                b.pants = 0xFF2F4858;
                b.shoes = 0xFF1F2933;
                b.eye = 0xFF4A6FA5;
                b.accent = 0xFFFFD166;
                b.torsoLen = 96;
                b.torsoW = 108;
                b.pelvisLen = 30;
                b.pelvisW = 90;
                b.shoulder = 42;
                b.shoulderY = -72;
                b.headLen = 152;
                b.headW = 156;
                b.neckLen = 14;
                b.armUpper = 62;
                b.armLower = 58;
                b.armUpperW = 30;
                b.armLowerW = 26;
                b.hand = 28;
                b.hipX = 22;
                b.thigh = 82;
                b.thighW = 44;
                b.shin = 76;
                b.shinW = 34;
                b.foot = 40;
                b.hairBackLen = 140;
                b.hairBackW = 170;
                return build(key, "Chibi", b);
            }
            case VILLAIN: {
                Body b = new Body();
                b.style = "villain";
                b.skin = 0xFFDCD5C6;
                b.hair = 0xFF14141A;
                b.cloth = 0xFF1E2A3A;
                b.pants = 0xFF141A24;
                b.shoes = 0xFF0D1016;
                b.eye = 0xFFD33A3A;
                b.accent = 0xFFB0122A;
                b.torsoLen = 196;
                b.torsoW = 138;
                b.shoulder = 56;
                b.headLen = 116;
                b.cape = true;
                b.capeLen = 300;
                b.capeW = 210;
                b.hairBackLen = 150;
                b.hairBackW = 150;
                b.sword = true;
                b.swordAngle = 205;
                return build(key, "Villain", b);
            }
            case MECHA: {
                return buildMecha();
            }
            case HERO:
            default: {
                Body b = new Body();
                b.skin = 0xFFF0C9A4;
                b.hair = 0xFF262B3A;
                b.cloth = 0xFFE4572E;
                b.pants = 0xFF2E3A4E;
                b.shoes = 0xFF191C26;
                b.eye = 0xFF3E7CB1;
                b.accent = 0xFFFFB347;
                b.hairBackLen = 128;
                b.hairBackW = 150;
                b.sword = true;
                return build(key, "Hero", b);
            }
        }
    }

    // ------------------------------------------------------------- humanoid

    private static final class Body {
        String style = "hero";
        float pelvisLen = 44, pelvisW = 104;
        float torsoLen = 180, torsoW = 132;
        float neckLen = 26;
        float headLen = 112, headW = 120;
        float shoulder = 50, shoulderY = -142;
        float armUpper = 104, armUpperW = 40, armLower = 96, armLowerW = 32, hand = 36;
        float hipX = 27, thigh = 168, thighW = 56, shin = 152, shinW = 40, foot = 52, footW = 32;
        float hairBackLen = 122, hairBackW = 142;
        float skirtLen = 0, skirtW = 170;
        float capeLen = 0, capeW = 190;
        int skin = 0xFFF0C9A4;
        int hair = 0xFF262B3A;
        int cloth = 0xFFE4572E;
        int pants = 0xFF2E3A4E;
        int shoes = 0xFF191C26;
        int ink = 0xFF1B1522;
        int eye = 0xFF3E7CB1;
        int accent = 0xFFFFB347;
        boolean skirt = false;
        boolean cape = false;
        boolean sword = false;
        float swordAngle = 22;
    }

    private static Rig build(String id, String name, Body b) {
        Rig rig = new Rig();
        rig.id = id;
        rig.name = name;
        rig.style = b.style;
        rig.ink = b.ink;
        rig.eye = b.eye;
        rig.accent = b.accent;

        float torsoBase = -b.pelvisLen * 0.7f;
        float neckBase = -(b.torsoLen - 12f);
        float headBase = -b.neckLen * 0.6f;
        float hipBase = -b.pelvisLen * 0.15f;

        // Behind the body.
        if (b.cape) {
            bone(rig, "cape", "Cape", "torso", 0, -(b.torsoLen * 0.85f), 0,
                    b.capeLen, b.capeW, b.cloth, Part.CAPE);
        }
        bone(rig, "hair_back", "Hair", "head", 0, -b.headLen * 0.12f, 0,
                b.hairBackLen, b.hairBackW, b.hair, Part.HAIR_BACK);
        if (b.skirt) {
            bone(rig, "skirt", "Skirt", "pelvis", 0, -b.pelvisLen * 0.9f, 0,
                    b.skirtLen, b.skirtW, b.pants, Part.SKIRT);
        }

        // Legs.
        bone(rig, "thigh_l", "Thigh L", "pelvis", -b.hipX, hipBase, 184,
                b.thigh, b.thighW, b.pants, Part.THIGH);
        bone(rig, "shin_l", "Shin L", "thigh_l", 0, -(b.thigh - 10), 2,
                b.shin, b.shinW, b.pants, Part.SHIN);
        bone(rig, "foot_l", "Foot L", "shin_l", 0, -(b.shin - 8), 260,
                b.foot, b.footW, b.shoes, Part.FOOT);

        bone(rig, "thigh_r", "Thigh R", "pelvis", b.hipX, hipBase, 176,
                b.thigh, b.thighW, b.pants, Part.THIGH);
        bone(rig, "shin_r", "Shin R", "thigh_r", 0, -(b.thigh - 10), -2,
                b.shin, b.shinW, b.pants, Part.SHIN);
        bone(rig, "foot_r", "Foot R", "shin_r", 0, -(b.shin - 8), 100,
                b.foot, b.footW, b.shoes, Part.FOOT);

        // Body.
        bone(rig, "pelvis", "Pelvis", null, 0, 0, 0, b.pelvisLen, b.pelvisW, b.pants, Part.PELVIS);
        bone(rig, "torso", "Torso", "pelvis", 0, torsoBase, 0, b.torsoLen, b.torsoW, b.cloth, Part.TORSO);

        // Arms.
        bone(rig, "arm_upper_l", "Upper arm L", "torso", -b.shoulder, b.shoulderY, 208,
                b.armUpper, b.armUpperW, b.cloth, Part.UPPER_ARM);
        bone(rig, "arm_lower_l", "Lower arm L", "arm_upper_l", 0, -(b.armUpper - 8), 6,
                b.armLower, b.armLowerW, b.skin, Part.LOWER_ARM);
        bone(rig, "hand_l", "Hand L", "arm_lower_l", 0, -(b.armLower - 8), 0,
                b.hand, b.hand * 0.9f, b.skin, Part.HAND);

        bone(rig, "arm_upper_r", "Upper arm R", "torso", b.shoulder, b.shoulderY, 152,
                b.armUpper, b.armUpperW, b.cloth, Part.UPPER_ARM);
        bone(rig, "arm_lower_r", "Lower arm R", "arm_upper_r", 0, -(b.armUpper - 8), -6,
                b.armLower, b.armLowerW, b.skin, Part.LOWER_ARM);
        bone(rig, "hand_r", "Hand R", "arm_lower_r", 0, -(b.armLower - 8), 0,
                b.hand, b.hand * 0.9f, b.skin, Part.HAND);

        // Head on top of everything.
        bone(rig, "neck", "Neck", "torso", 0, neckBase, 0, b.neckLen, 44, b.skin, Part.NECK);
        bone(rig, "head", "Head", "neck", 0, headBase, 0, b.headLen, b.headW, b.skin, Part.HEAD);

        if (b.sword) {
            bone(rig, "sword", "Blade", "hand_r", 0, -b.hand * 0.4f, b.swordAngle,
                    260, 22, 0xFFC9D4E2, Part.SWORD);
        }
        return rig;
    }

    private static Rig buildMecha() {
        Rig rig = new Rig();
        rig.id = MECHA;
        rig.name = "Mecha";
        rig.style = "mecha";
        rig.ink = 0xFF10141C;
        rig.eye = 0xFF4DE1FF;
        rig.accent = 0xFFF2A03D;

        int metal = 0xFF8A93A6;
        int dark = 0xFF3A404C;
        int plate = 0xFF5C6474;

        bone(rig, "cape", "Thruster pack", "torso", 0, -150, 0, 220, 170, dark, Part.CAPE);
        bone(rig, "thigh_l", "Thigh L", "pelvis", -34, -8, 183, 160, 64, dark, Part.THIGH);
        bone(rig, "shin_l", "Shin L", "thigh_l", 0, -150, 2, 150, 52, metal, Part.SHIN);
        bone(rig, "foot_l", "Foot L", "shin_l", 0, -142, 262, 58, 42, dark, Part.FOOT);
        bone(rig, "thigh_r", "Thigh R", "pelvis", 34, -8, 177, 160, 64, dark, Part.THIGH);
        bone(rig, "shin_r", "Shin R", "thigh_r", 0, -150, -2, 150, 52, metal, Part.SHIN);
        bone(rig, "foot_r", "Foot R", "shin_r", 0, -142, 98, 58, 42, dark, Part.FOOT);

        bone(rig, "pelvis", "Pelvis", null, 0, 0, 0, 52, 120, dark, Part.PELVIS);
        bone(rig, "torso", "Chest", "pelvis", 0, -36, 0, 190, 170, plate, Part.TORSO);
        bone(rig, "core", "Core", "torso", 0, -110, 0, 40, 54, 0xFF4DE1FF, Part.PROP);

        bone(rig, "shoulder_l", "Shoulder L", "torso", -78, -150, 200, 40, 92, metal, Part.PROP);
        bone(rig, "arm_upper_l", "Upper arm L", "shoulder_l", 0, -30, 190, 108, 48, metal, Part.UPPER_ARM);
        bone(rig, "arm_lower_l", "Lower arm L", "arm_upper_l", 0, -98, 6, 100, 42, plate, Part.LOWER_ARM);
        bone(rig, "hand_l", "Hand L", "arm_lower_l", 0, -92, 0, 42, 40, dark, Part.HAND);

        bone(rig, "shoulder_r", "Shoulder R", "torso", 78, -150, 160, 40, 92, metal, Part.PROP);
        bone(rig, "arm_upper_r", "Upper arm R", "shoulder_r", 0, -30, 170, 108, 48, metal, Part.UPPER_ARM);
        bone(rig, "arm_lower_r", "Lower arm R", "arm_upper_r", 0, -98, -6, 100, 42, plate, Part.LOWER_ARM);
        bone(rig, "hand_r", "Hand R", "arm_lower_r", 0, -92, 0, 42, 40, dark, Part.HAND);

        bone(rig, "neck", "Neck", "torso", 0, -178, 0, 24, 46, dark, Part.NECK);
        bone(rig, "head", "Head", "neck", 0, -14, 0, 74, 86, metal, Part.HEAD);
        return rig;
    }

    private static void bone(Rig rig, String id, String name, String parent,
                             float px, float py, float angle, float len, float width,
                             int color, Part part) {
        rig.bones.add(new Bone(id, name, parent, px, py, angle, len, width, color, part));
    }
}
