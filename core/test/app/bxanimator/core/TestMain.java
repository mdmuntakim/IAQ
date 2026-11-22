package app.bxanimator.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JVM test suite for the animation core. Runs without Android: {@code bash build.sh --tests}.
 */
public final class TestMain {

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        testJson();
        testEasing();
        testTrack();
        testMatrix();
        testRigLibrary();
        testPoseSolver();
        testLayerAndStrokes();
        testProject();
        testSampler();
        testEffects();
        testThemes();

        System.out.println();
        System.out.println(passed + " checks passed, " + failures.size() + " failed");
        if (!failures.isEmpty()) {
            for (String f : failures) System.out.println("  FAIL " + f);
            System.exit(1);
        }
    }

    // ------------------------------------------------------------- tests

    private static void testJson() {
        Map<String, Object> src = Json.obj(
                "name", "Episode \"01\"",
                "fps", 24,
                "nested", Json.obj("a", 1.5, "b", Json.arr(1, 2, 3)),
                "unicode", "アニメ \u2014 ok",
                "flag", true,
                "nil", null);
        String text = Json.write(src);
        Map<String, Object> back = Json.parseObject(text);
        eq("json string", "Episode \"01\"", Json.str(back, "name", ""));
        eq("json number", 24, Json.inum(back, "fps", 0));
        eq("json unicode", "アニメ \u2014 ok", Json.str(back, "unicode", ""));
        eq("json bool", true, Json.bool(back, "flag", false));
        eq("json nested", 1.5, Json.num(Json.map(back.get("nested")), "a", 0), 1e-9);
        eq("json array", 3, Json.list(Json.map(back.get("nested")).get("b")).size());
        eq("json null", null, back.get("nil"));

        eq("colour hex", "#FFFF7A18", Json.hex(0xFFFF7A18));
        eq("colour parse", 0xFFFF7A18, Json.color("#FF7A18", 0));
        eq("colour short", 0xFFFFFFFF, Json.color("#fff", 0));
        eq("colour argb", 0x80112233, Json.color("#80112233", 0));
        eq("colour bad", 7, Json.color("nope", 7));

        boolean threw = false;
        try {
            Json.parse("{\"a\":1,}");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("json rejects trailing comma", threw);
    }

    private static void testEasing() {
        for (Easing e : Easing.values()) {
            eq("ease " + e + " start", 0f, e.apply(0f), 1e-6);
            eq("ease " + e + " end", 1f, e.apply(1f), 1e-6);
            float mid = e.apply(0.5f);
            check("ease " + e + " finite", Float.isFinite(mid));
        }
        eq("hold mid", 0f, Easing.HOLD.apply(0.5f), 1e-6);
        eq("step mid", 1f, Easing.STEP_END.apply(0.5f), 1e-6);
        eq("easing by id", Easing.BOUNCE_OUT, Easing.byId("bounce_out", Easing.LINEAR));
        eq("easing by id dash", Easing.SINE_IN_OUT, Easing.byId("sine-in-out", Easing.LINEAR));
        eq("easing fallback", Easing.LINEAR, Easing.byId("nope", Easing.LINEAR));
        eq("wrap", -10f, AnimMath.wrapDegrees(350f), 1e-5);
        eq("delta", -20f, AnimMath.deltaDegrees(350f, 330f), 1e-5);
        eq("frame at", 12, AnimMath.frameAt(0.5, 24));
        eq("timecode", "00:01:12", AnimMath.timecode(1.5, 24));
    }

    private static void testTrack() {
        Track t = new Track();
        eq("empty sample", 5f, t.sample(1, 5f), 1e-6);
        t.put(2, 100, Easing.LINEAR);
        t.put(0, 0, Easing.LINEAR);
        t.put(1, 50, Easing.LINEAR);
        eq("sorted first", 0, t.get(0).time, 1e-9);
        eq("sorted last", 2, t.get(2).time, 1e-9);
        eq("midpoint", 50f, t.sample(1, 0), 1e-4);
        eq("clamp low", 0f, t.sample(-3, 0), 1e-6);
        eq("clamp high", 100f, t.sample(9, 0), 1e-6);
        t.put(1, 60, Easing.LINEAR);
        eq("replace keeps count", 3, t.size());
        eq("replace value", 60f, t.sample(1, 0), 1e-6);
        eq("find key", true, t.keyAt(2) != null);
        t.move(0, 3);
        eq("move sorted", 3, t.get(2).time, 1e-9);
        eq("remove", true, t.removeAt(1));
        eq("remove missing", false, t.removeAt(1));
        eq("animated", true, t.isAnimated());
        t.clear();
        eq("clear", 0, t.size());
        check("empty not animated", !t.isAnimated());

        Track hold = new Track();
        hold.put(0, 0, Easing.HOLD);
        hold.put(1, 100, Easing.LINEAR);
        eq("hold segment", 0f, hold.sample(0.9, 0), 1e-5);
        eq("hold next", 100f, hold.sample(1, 0), 1e-5);

        Track copy = hold.copy();
        copy.put(2, 5, Easing.LINEAR);
        eq("copy independent", 2, hold.size());

        Track scaled = hold.copy();
        scaled.scaleTimes(0, 2);
        eq("scale times", 2, scaled.lastTime(), 1e-9);
    }

    private static void testMatrix() {
        Mat2D m = Mat2D.translate(10, 20);
        eq("translate x", 13f, m.mapX(3, 4), 1e-5);
        eq("translate y", 24f, m.mapY(3, 4), 1e-5);
        Mat2D r = Mat2D.rotate(90);
        eq("rotate x", 1f, r.mapX(0, -1), 1e-5);
        eq("rotate y", 0f, r.mapY(0, -1), 1e-5);
        Mat2D chain = Mat2D.translate(100, 0);
        chain.mul(Mat2D.rotate(90));
        eq("chain x", 100f, chain.mapX(0, 0), 1e-4);
        eq("chain y", 5f, chain.mapY(5, 0), 1e-4);
        Mat2D s = Mat2D.compose(0, 0, 0, 2, 3);
        eq("compose sx", 4f, s.mapX(2, 0), 1e-5);
        eq("compose sy", 6f, s.mapY(0, 2), 1e-5);
        eq("scale length", 2f, s.scaleX(), 1e-5);
    }

    private static void testRigLibrary() {
        for (Rig rig : RigLibrary.all()) {
            check("rig " + rig.id + " has bones", rig.bones.size() >= 12);
            Set<String> ids = new HashSet<>();
            for (Bone b : rig.bones) {
                check("rig " + rig.id + " unique " + b.id, ids.add(b.id));
                check("rig " + rig.id + " length " + b.id, b.length > 0);
                check("rig " + rig.id + " width " + b.id, b.width > 0);
                if (b.parent != null) {
                    check("rig " + rig.id + " parent " + b.parent, rig.bone(b.parent) != null);
                }
            }
            check("rig " + rig.id + " head", rig.bone("head") != null);
            check("rig " + rig.id + " pelvis", rig.bone("pelvis") != null);
            Rig copy = rig.copy();
            eq("rig copy bones", rig.bones.size(), copy.bones.size());
            Rig json = Rig.fromJson(rig.toJson());
            eq("rig json bones", rig.bones.size(), json.bones.size());
            eq("rig json style", rig.style, json.style);
        }
        check("rig label", "Heroine".equals(RigLibrary.label(RigLibrary.HEROINE)));
    }

    private static void testPoseSolver() {
        Rig rig = RigLibrary.create(RigLibrary.HERO);
        Layer layer = Layer.create(LayerType.RIG, "Hero");
        layer.rig = rig;
        Pose pose = PoseSolver.solve(rig, layer, 0);
        Bone head = rig.bone("head");
        float[] headTip = PoseSolver.tip(head, pose);
        check("head above pelvis", headTip[1] < -300);

        // Rotating the torso by 90 degrees swings the head to the side.
        layer.setKey(Layer.boneChannel("torso"), 1, 90f, Easing.LINEAR);
        Pose rotated = PoseSolver.solve(rig, layer, 1);
        float[] rotatedTip = PoseSolver.tip(head, rotated);
        check("torso rotation moves head sideways", rotatedTip[0] > 100);
        eq("bone angle sampled", 90f, rotated.angle("torso"), 1e-4);
        eq("rest angle fallback", rig.bone("thigh_l").restAngle, rotated.angle("thigh_l"), 1e-4);

        Bone picked = PoseSolver.pick(rig, pose, 0, -250, 30);
        check("pick head or neck", picked != null);
        Bone miss = PoseSolver.pick(rig, pose, 2000, 2000, 10);
        eq("pick miss", null, miss);

        float angle = PoseSolver.pointerAngle(rig.bone("torso"), pose, 100, -100);
        check("pointer angle finite", Float.isFinite(angle));
        eq("distance on segment", 0f, PoseSolver.distanceToSegment(5, 0, 0, 0, 10, 0), 1e-5);
        eq("distance off segment", 3f, PoseSolver.distanceToSegment(5, 3, 0, 0, 10, 0), 1e-5);

        // A layer transform must offset the whole rig.
        layer.channels.clear();
        layer.setKey(Layer.X, 0, 500f);
        Pose moved = PoseSolver.solve(rig, layer, 0);
        eq("layer offset", 500f, PoseSolver.tip(head, moved)[0], 1e-3);

        // Every rig must resolve without cycles and with finite matrices.
        for (Rig candidate : RigLibrary.all()) {
            Pose p = PoseSolver.solve(candidate, Layer.create(LayerType.RIG, "x"), 0);
            for (Bone b : candidate.bones) {
                Mat2D m = p.of(b.id);
                check("pose finite " + candidate.id + " " + b.id,
                        Float.isFinite(m.a) && Float.isFinite(m.tx) && Float.isFinite(m.ty));
            }
        }
    }

    private static void testLayerAndStrokes() {
        Layer layer = Layer.create(LayerType.RIG, "Hero");
        layer.rig = RigLibrary.create(RigLibrary.HERO);
        eq("opacity default", 1f, layer.opacity(0), 1e-6);
        eq("scale default", 1f, layer.scale(0), 1e-6);
        eq("bone rest", layer.rig.bone("head").restAngle, layer.boneAngle(layer.rig.bone("head"), 0), 1e-5);
        layer.setKey(Layer.OPACITY, 0, 0f);
        layer.setKey(Layer.OPACITY, 1, 1f);
        eq("opacity half", 0.5f, layer.opacity(0.5), 1e-4);
        eq("key times", 2, layer.keyTimes().size());
        check("has keys", layer.hasKeys());

        Layer drawing = Layer.create(LayerType.DRAW, "Sketch");
        drawing.addStroke(0, 0xFF000000, 4, false);
        drawing.addStroke(2, 0xFF000000, 4, false);
        eq("cumulative strokes", 2, drawing.strokesAt(3).size());
        eq("no future strokes", 0, drawing.strokesAt(-1).size());
        drawing.cumulativeStrokes = false;
        eq("held strokes", 1, drawing.strokesAt(3).size());
        eq("last stroke frame", 2, drawing.lastStrokeFrame());

        Layer copy = drawing.copy();
        eq("layer copy strokes", 2, copy.strokes.size());
        Layer back = Layer.fromJson(drawing.toJson());
        eq("layer json strokes", 2, back.strokes.size());
        eq("layer json type", LayerType.DRAW, back.type);
        eq("stroke points", 0, back.strokes.get(0).points.size());
    }

    private static void testProject() {
        Project p = ProjectFactory.create("Test Episode", "action");
        eq("three scenes", 3, p.scenes.size());
        eq("fps", 24, p.fps);
        check("duration", p.duration() > 20);
        check("frames", p.totalFrames() > 400);
        check("camera", p.scenes.get(0).camera() != null);
        check("rig layer", p.scenes.get(0).layers.size() >= 6);

        String json = p.toJsonString();
        Project back = Project.fromJsonString(json);
        eq("project json scenes", 3, back.scenes.size());
        eq("project json name", "Test Episode", back.name);
        eq("project json fps", 24, back.fps);
        eq("project json layer count", p.scenes.get(0).layers.size(), back.scenes.get(0).layers.size());
        eq("project json duration", p.scenes.get(0).duration, back.scenes.get(0).duration, 1e-6);
        check("project json effects", back.scenes.get(0).effects.size() > 0);
        boolean rigSurvived = false;
        for (Layer l : back.scenes.get(0).layers) {
            if (l.type == LayerType.RIG && l.rig != null && l.rig.bones.size() > 10) rigSurvived = true;
        }
        check("project json rig", rigSurvived);
        eq("project json keys", p.scenes.get(0).keyTimes().size(), back.scenes.get(0).keyTimes().size());
        check("project copy", p.copy().scenes.size() == 3);

        Project blank = new Project();
        blank.scenes.clear();
        blank.width = 1919;
        blank.height = 1081;
        blank.fps = 999;
        blank.validate();
        eq("validate scene", 1, blank.scenes.size());
        eq("validate even width", 1918, blank.width);
        eq("validate even height", 1080, blank.height);
        eq("validate fps", 24, blank.fps);
        check("validate camera", blank.scenes.get(0).camera() != null);

        for (ProjectFactory.Template t : ProjectFactory.templates()) {
            Project built = ProjectFactory.create(t.label, t.id);
            check("template " + t.id, built.scenes.size() >= 1);
            check("template camera " + t.id, built.scene(0).camera() != null);
        }
    }

    private static void testSampler() {
        Project p = ProjectFactory.create("Sampler", "action");
        Scene scene = p.scene(0);
        SceneSampler.SampledScene a = SceneSampler.sample(scene, 0);
        SceneSampler.SampledScene b = SceneSampler.sample(scene, 5);
        check("sampler layers", a.layers.size() >= 6);
        check("sampler camera moves", Math.abs(a.camScale - b.camScale) > 1e-4);
        check("sampler time", b.time == 5f);
        SceneSampler.SampledLayer hero = null;
        for (SceneSampler.SampledLayer l : a.layers) {
            if (l.layer.type == LayerType.RIG && l.pose != null) hero = l;
        }
        check("sampler rig pose", hero != null && hero.pose.world.size() > 5);
        Layer firstVisible = null;
        for (Layer l : scene.layers) {
            if (l.type != LayerType.CAMERA) {
                firstVisible = l;
                break;
            }
        }
        check("sampler find", firstVisible != null && a.find(firstVisible.id) != null);
    }

    private static void testEffects() {
        eq("effect types", 12, EffectType.values().length);
        for (EffectType type : EffectType.values()) {
            Effect e = Effect.of(type, 0.5f);
            eq("effect defaults " + type, type.params.length, e.params.size());
            for (String param : type.params) {
                check("effect param default " + type + "." + param, e.param(param) == type.defaultOf(param));
            }
            Effect back = Effect.fromJson(e.toJson());
            eq("effect json " + type, type, back.type);
            eq("effect json intensity " + type, 0.5f, back.intensity, 1e-6);
        }
        for (EffectPresets.Preset preset : EffectPresets.all()) {
            List<Effect> stack = EffectPresets.build(preset.id);
            check("preset " + preset.id, !stack.isEmpty());
            for (Effect e : stack) {
                check("preset param " + preset.id, e.intensity >= 0 && e.intensity <= 1);
            }
        }
        List<Effect> remaster = EffectPresets.build("modern-remaster");
        boolean hasRemaster = false;
        for (Effect e : remaster) {
            if (e.type == EffectType.REMASTER) hasRemaster = true;
        }
        check("remaster preset contains remaster", hasRemaster);
        eq("preset by id fallback", "clean-cel", EffectPresets.byId("nope").id);
    }

    private static void testThemes() {
        List<Theme> themes = Theme.Catalog.all();
        eq("theme count", 6, themes.size());
        Set<String> ids = new HashSet<>();
        for (Theme t : themes) {
            check("theme id " + t.id, ids.add(t.id));
            check("theme label " + t.id, t.label.length() > 2);
            check("theme accent " + t.id, (t.accent >>> 24) == 0xFF);
            check("theme text " + t.id, (t.text >>> 24) == 0xFF);
        }
        eq("theme lookup", "sakura", Theme.Catalog.byId("sakura").id);
        eq("theme fallback", "midnight", Theme.Catalog.byId("nope").id);
        eq("theme dark", false, Theme.Catalog.byId("inkpaper").dark);
    }

    // ------------------------------------------------------------- asserts

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failures.add(name);
        }
    }

    private static void eq(String name, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        check(name + " (expected " + expected + ", got " + actual + ")", ok);
    }

    private static void eq(String name, float expected, float actual, double tolerance) {
        check(name + " (expected " + expected + ", got " + actual + ")", Math.abs(expected - actual) <= tolerance);
    }

    private static void eq(String name, double expected, double actual, double tolerance) {
        check(name + " (expected " + expected + ", got " + actual + ")", Math.abs(expected - actual) <= tolerance);
    }

    private static void eq(String name, int expected, int actual) {
        check(name + " (expected " + expected + ", got " + actual + ")", expected == actual);
    }

    private static void eq(String name, boolean expected, boolean actual) {
        check(name + " (expected " + expected + ", got " + actual + ")", expected == actual);
    }
}
