package app.bxanimator.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Template projects. Each one is a small, finished scene that already animates,
 * so a new project never starts from a dead timeline.
 */
public final class ProjectFactory {

    public static final class Template {
        public final String id;
        public final String label;
        public final String description;

        Template(String id, String label, String description) {
            this.id = id;
            this.label = label;
            this.description = description;
        }
    }

    private static final Template[] TEMPLATES = {
            new Template("action", "Rooftop Clash",
                    "Two characters, a push in camera and an action shader stack."),
            new Template("dialogue", "Quiet Confession",
                    "A close up with soft light, a blink and a slow camera drift."),
            new Template("city", "City Flythrough",
                    "Layered skyline, parallax camera move and night grading."),
            new Template("blank", "Empty Scene",
                    "One camera and nothing else. Start from zero.")
    };

    private ProjectFactory() {
    }

    public static List<Template> templates() {
        List<Template> list = new ArrayList<>();
        for (Template t : TEMPLATES) list.add(t);
        return list;
    }

    public static Template template(String id) {
        for (Template t : TEMPLATES) {
            if (t.id.equals(id)) return t;
        }
        return TEMPLATES[0];
    }

    public static Project create(String name, String templateId) {
        Project p = new Project();
        p.name = name == null || name.trim().isEmpty() ? "Untitled" : name.trim();
        p.fps = 24;
        p.width = 1920;
        p.height = 1080;
        p.theme = "midnight";
        if (templateId == null) templateId = "action";
        switch (templateId) {
            case "dialogue":
                p.scenes.add(dialogueScene(p));
                p.theme = "sakura";
                break;
            case "city":
                p.scenes.add(cityScene(p));
                p.theme = "cyber";
                break;
            case "blank":
                p.scenes.add(blankScene("Scene 1", p.width, p.height));
                break;
            case "action":
            default:
                p.scenes.add(actionScene(p));
                p.scenes.add(dialogueScene(p));
                p.scenes.add(cityScene(p));
                break;
        }
        p.scenes.get(0).name = templateId.equals("action") ? "Rooftop" : template(templateId).label;
        p.validate();
        p.touch();
        return p;
    }

    public static Scene blankScene(String name, int width, int height) {
        Scene s = Scene.create(name, 6);
        s.bgTop = 0xFF16233A;
        s.bgBottom = 0xFF06080E;
        s.ensureCamera(width / 2f, height / 2f);
        return s;
    }

    // ------------------------------------------------------------- scenes

    private static Scene actionScene(Project p) {
        Scene s = Scene.create("Rooftop", 10);
        s.bgTop = 0xFF24365C;
        s.bgBottom = 0xFF06080F;
        s.effects.addAll(EffectPresets.build("action-burst"));
        s.ensureCamera(p.width / 2f, p.height / 2f);

        Layer moon = shape("Moon", "ellipse", 1420, 250, 360, 360, 0xFFF3E4C4);
        moon.setKey(Layer.X, 0, 1450);
        moon.setKey(Layer.X, 10, 1400, Easing.SINE_IN_OUT);
        moon.setKey(Layer.OPACITY, 0, 0.92f);
        s.add(moon);

        Layer skyline = shape("Skyline", "rect", 960, 880, 2400, 520, 0xFF0A111F);
        s.add(skyline);

        Layer far = shape("Far buildings", "rect", 960, 800, 2400, 260, 0xFF101A2E);
        far.setKey(Layer.X, 0, 960);
        far.setKey(Layer.X, 10, 900, Easing.SINE_IN_OUT);
        s.add(far);

        Layer villain = rig("Villain", RigLibrary.VILLAIN, 1290, 830, 1.02f);
        villain.setKey(Layer.X, 0, 1420, Easing.QUAD_OUT);
        villain.setKey(Layer.X, 1.6, 1290, Easing.SINE_IN_OUT);
        idle(villain, 1.6, 8.4, 2.6f, 0.9f);
        key(villain, "b.arm_upper_r.rot", 4.0, 152, Easing.QUAD_IN_OUT);
        key(villain, "b.arm_upper_r.rot", 5.1, 96, Easing.BACK_OUT);
        key(villain, "b.arm_upper_r.rot", 7.2, 152, Easing.SINE_IN_OUT);
        key(villain, "b.head.rot", 4.2, 0, Easing.SINE_IN_OUT);
        key(villain, "b.head.rot", 5.0, -6, Easing.SINE_IN_OUT);
        capeSway(villain, 0, 10, 4.5f);
        s.add(villain);

        Layer hero = rig("Hero", RigLibrary.HERO, 660, 820, 1.0f);
        hero.setKey(Layer.X, 0, 120, Easing.QUAD_OUT);
        hero.setKey(Layer.X, 1.4, 660, Easing.SINE_IN_OUT);
        hero.setKey(Layer.Y, 0, 820);
        hero.setKey(Layer.Y, 1.4, 790, Easing.BACK_OUT);
        hero.setKey(Layer.Y, 1.8, 820, Easing.BOUNCE_OUT);
        idle(hero, 1.8, 8.2, 2.2f, 1.1f);
        key(hero, "b.arm_upper_r.rot", 3.8, 152, Easing.QUAD_IN_OUT);
        key(hero, "b.arm_upper_r.rot", 4.6, 48, Easing.BACK_OUT);
        key(hero, "b.arm_upper_r.rot", 5.4, 152, Easing.SINE_IN_OUT);
        key(hero, "b.arm_upper_l.rot", 4.4, 208, Easing.QUAD_IN_OUT);
        key(hero, "b.arm_upper_l.rot", 5.0, 250, Easing.BACK_OUT);
        key(hero, "b.arm_upper_l.rot", 6.0, 208, Easing.SINE_IN_OUT);
        key(hero, "b.head.rot", 4.0, 0);
        key(hero, "b.head.rot", 4.8, 8, Easing.SINE_IN_OUT);
        blink(hero, 1.2, 4, 2.1);
        s.add(hero);

        Layer title = text("Episode title", "EPISODE 01", 960, 168, 92, 0xFFF6EDE2);
        title.setKey(Layer.OPACITY, 0.4, 0f);
        title.setKey(Layer.OPACITY, 1.3, 1f, Easing.SINE_OUT);
        title.setKey(Layer.OPACITY, 6.8, 1f);
        title.setKey(Layer.OPACITY, 7.6, 0f, Easing.SINE_IN);
        title.setKey(Layer.SCALE, 0.4, 0.94f);
        title.setKey(Layer.SCALE, 1.6, 1f, Easing.SINE_OUT);
        s.add(title);

        Layer sub = text("Subtitle", "ROOFTOP CLASH", 960, 972, 46, 0xFFB9C6DD);
        sub.setKey(Layer.OPACITY, 0.8, 0f);
        sub.setKey(Layer.OPACITY, 1.8, 1f, Easing.SINE_OUT);
        sub.setKey(Layer.OPACITY, 6.8, 1f);
        sub.setKey(Layer.OPACITY, 7.6, 0f);
        s.add(sub);

        Layer cam = s.camera();
        cam.setKey(Layer.X, 0, 900);
        cam.setKey(Layer.X, 10, 1010, Easing.SINE_IN_OUT);
        cam.setKey(Layer.Y, 0, 540);
        cam.setKey(Layer.Y, 10, 500, Easing.SINE_IN_OUT);
        cam.setKey(Layer.SCALE, 0, 1.0f);
        cam.setKey(Layer.SCALE, 10, 1.18f, Easing.SINE_IN_OUT);
        cam.setKey(Layer.ROT, 0, 0f);
        cam.setKey(Layer.ROT, 10, -1.6f, Easing.SINE_IN_OUT);
        return s;
    }

    private static Scene dialogueScene(Project p) {
        Scene s = Scene.create("Confession", 8);
        s.bgTop = 0xFF3A2438;
        s.bgBottom = 0xFF0D0710;
        s.effects.addAll(EffectPresets.build("soft-memory"));
        s.ensureCamera(p.width / 2f, p.height / 2f);

        Layer glow = shape("Window light", "ellipse", 1370, 300, 700, 700, 0xFF7A4E63);
        glow.setKey(Layer.OPACITY, 0, 0.35f);
        glow.setKey(Layer.OPACITY, 8, 0.5f, Easing.SINE_IN_OUT);
        s.add(glow);

        Layer heroine = rig("Heroine", RigLibrary.HEROINE, 900, 1130, 1.55f);
        idle(heroine, 0, 8, 3.1f, 0.55f);
        key(heroine, "b.head.rot", 0, 0);
        key(heroine, "b.head.rot", 2.4, -3.5f, Easing.SINE_IN_OUT);
        key(heroine, "b.head.rot", 5.2, 2.0f, Easing.SINE_IN_OUT);
        key(heroine, "b.head.rot", 8, 0f, Easing.SINE_IN_OUT);
        key(heroine, Layer.FACE_BROW, 0, 0f);
        key(heroine, Layer.FACE_BROW, 3.0, 0.6f, Easing.SINE_IN_OUT);
        key(heroine, Layer.FACE_BROW, 6.0, -0.3f, Easing.SINE_IN_OUT);
        key(heroine, Layer.FACE_MOUTH, 0, 0f);
        key(heroine, Layer.FACE_MOUTH, 2.2, 0.35f, Easing.SINE_IN_OUT);
        key(heroine, Layer.FACE_MOUTH, 4.4, 0f, Easing.SINE_IN_OUT);
        key(heroine, Layer.FACE_LOOK, 0, 0f);
        key(heroine, Layer.FACE_LOOK, 4.0, 0.45f, Easing.SINE_IN_OUT);
        blink(heroine, 0.8, 3, 2.4);
        s.add(heroine);

        Layer line = text("Dialogue", "\u201cI knew you would come back.\u201d", 960, 968, 58, 0xFFF6E7F1);
        line.setKey(Layer.OPACITY, 0.6, 0f);
        line.setKey(Layer.OPACITY, 1.6, 1f, Easing.SINE_OUT);
        line.setKey(Layer.Y, 0.6, 992);
        line.setKey(Layer.Y, 1.8, 968, Easing.SINE_OUT);
        s.add(line);

        Layer cam = s.camera();
        cam.setKey(Layer.X, 0, 930);
        cam.setKey(Layer.X, 8, 915, Easing.SINE_IN_OUT);
        cam.setKey(Layer.Y, 0, 520);
        cam.setKey(Layer.Y, 8, 495, Easing.SINE_IN_OUT);
        cam.setKey(Layer.SCALE, 0, 1.32f);
        cam.setKey(Layer.SCALE, 8, 1.46f, Easing.SINE_IN_OUT);
        return s;
    }

    private static Scene cityScene(Project p) {
        Scene s = Scene.create("City", 8);
        s.bgTop = 0xFF0B1B33;
        s.bgBottom = 0xFF03060C;
        s.effects.addAll(EffectPresets.build("cinematic-night"));
        s.ensureCamera(p.width / 2f, p.height / 2f);

        Layer moon = shape("Moon", "ellipse", 1500, 220, 260, 260, 0xFFE9F2FF);
        moon.setKey(Layer.X, 0, 1560);
        moon.setKey(Layer.X, 8, 1460, Easing.SINE_IN_OUT);
        s.add(moon);

        Layer far = shape("Far city", "rect", 960, 760, 2600, 420, 0xFF0D1B2E);
        far.setKey(Layer.X, 0, 980);
        far.setKey(Layer.X, 8, 860, Easing.SINE_IN_OUT);
        s.add(far);

        Layer mid = shape("Mid city", "rect", 960, 860, 2600, 320, 0xFF0A1422);
        mid.setKey(Layer.X, 0, 1000);
        mid.setKey(Layer.X, 8, 760, Easing.SINE_IN_OUT);
        s.add(mid);

        Layer near = shape("Near city", "rect", 960, 1010, 2600, 220, 0xFF05090F);
        near.setKey(Layer.X, 0, 900);
        near.setKey(Layer.X, 8, 640, Easing.SINE_IN_OUT);
        s.add(near);

        Layer beacon = shape("Beacon", "ellipse", 360, 430, 22, 22, 0xFFFF5C5C);
        beacon.setKey(Layer.OPACITY, 0, 1f);
        beacon.setKey(Layer.OPACITY, 0.8, 0.2f);
        beacon.setKey(Layer.OPACITY, 1.6, 1f, Easing.SINE_IN_OUT);
        beacon.setKey(Layer.OPACITY, 2.4, 0.2f);
        beacon.setKey(Layer.OPACITY, 3.2, 1f, Easing.SINE_IN_OUT);
        s.add(beacon);

        Layer cam = s.camera();
        cam.setKey(Layer.X, 0, 720);
        cam.setKey(Layer.X, 8, 1220, Easing.SINE_IN_OUT);
        cam.setKey(Layer.Y, 0, 600);
        cam.setKey(Layer.Y, 8, 520, Easing.SINE_IN_OUT);
        cam.setKey(Layer.SCALE, 0, 1.02f);
        cam.setKey(Layer.SCALE, 8, 1.12f, Easing.SINE_IN_OUT);
        cam.setKey(Layer.ROT, 0, 0f);
        cam.setKey(Layer.ROT, 8, 1.2f, Easing.SINE_IN_OUT);
        return s;
    }

    // ------------------------------------------------------------- helpers

    public static Layer rig(String name, String rigKey, float x, float y, float scale) {
        Layer l = Layer.create(LayerType.RIG, name);
        l.rig = RigLibrary.create(rigKey);
        l.color = l.rig.accent;
        l.setKey(Layer.X, 0, x);
        l.setKey(Layer.Y, 0, y);
        l.setKey(Layer.SCALE, 0, scale);
        l.setKey(Layer.ROT, 0, 0f);
        l.setKey(Layer.OPACITY, 0, 1f);
        return l;
    }

    public static Layer shape(String name, String kind, float x, float y, float w, float h, int fill) {
        Layer l = Layer.create(LayerType.SHAPE, name);
        l.shape = kind;
        l.shapeWidth = w;
        l.shapeHeight = h;
        l.shapeFill = fill;
        l.color = fill;
        l.setKey(Layer.X, 0, x);
        l.setKey(Layer.Y, 0, y);
        return l;
    }

    public static Layer text(String name, String content, float x, float y, float size, int color) {
        Layer l = Layer.create(LayerType.TEXT, name);
        l.text = content;
        l.textSize = size;
        l.color = color;
        l.setKey(Layer.X, 0, x);
        l.setKey(Layer.Y, 0, y);
        l.setKey(Layer.OPACITY, 0, 1f);
        return l;
    }

    public static void key(Layer layer, String channel, double time, float value) {
        layer.setKey(channel, time, value);
    }

    public static void key(Layer layer, String channel, double time, float value, Easing ease) {
        layer.setKey(channel, time, value, ease);
    }

    /** Breathing, weight shift and arm sway for a rig between two times. */
    public static void idle(Layer layer, double from, double to, float period, float amount) {
        if (layer.rig == null) return;
        double t = from;
        int step = 0;
        while (t <= to + 1e-6) {
            float sign = step % 2 == 0 ? 1f : -1f;
            key(layer, "b.torso.rot", t, sign * 0.9f * amount, Easing.SINE_IN_OUT);
            key(layer, "b.head.rot", t, -sign * 1.4f * amount, Easing.SINE_IN_OUT);
            key(layer, "b.arm_upper_l.rot", t, 208 + sign * 2.2f * amount, Easing.SINE_IN_OUT);
            key(layer, "b.arm_upper_r.rot", t, 152 - sign * 2.2f * amount, Easing.SINE_IN_OUT);
            t += period / 2.0;
            step++;
        }
    }

    public static void capeSway(Layer layer, double from, double to, float period) {
        double t = from;
        int step = 0;
        while (t <= to + 1e-6) {
            float sign = step % 2 == 0 ? 1f : -1f;
            key(layer, "b.cape.rot", t, sign * 4f, Easing.SINE_IN_OUT);
            key(layer, "b.hair_back.rot", t, -sign * 2.5f, Easing.SINE_IN_OUT);
            t += period / 2.0;
            step++;
        }
    }

    /** Adds closed-eye keys to a rig layer, starting at {@code start}. */
    public static void blink(Layer layer, double start, int count, double every) {
        for (int i = 0; i < count; i++) {
            double t = start + i * every;
            key(layer, Layer.FACE_BLINK, t, 0f);
            key(layer, Layer.FACE_BLINK, t + 0.07, 1f, Easing.SINE_IN_OUT);
            key(layer, Layer.FACE_BLINK, t + 0.14, 0f, Easing.SINE_IN_OUT);
        }
    }
}
