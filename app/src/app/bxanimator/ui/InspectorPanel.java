package app.bxanimator.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.bxanimator.core.AnimMath;
import app.bxanimator.core.Bone;
import app.bxanimator.core.Easing;
import app.bxanimator.core.Edits;
import app.bxanimator.core.Effect;
import app.bxanimator.core.EffectPresets;
import app.bxanimator.core.EffectType;
import app.bxanimator.core.Ids;
import app.bxanimator.core.Layer;
import app.bxanimator.core.LayerType;
import app.bxanimator.core.Project;
import app.bxanimator.core.RigLibrary;
import app.bxanimator.core.Scene;
import app.bxanimator.core.Theme;
import app.bxanimator.core.Track;

/**
 * The inspector: layer properties with keyframe diamonds, the layer stack, the
 * scene settings and the live shader stack. Everything edits the project in
 * place, so the canvas and the timeline update on the next frame.
 */
public final class InspectorPanel extends ScrollView {

    public static final int MODE_LAYER = 0;
    public static final int MODE_SCENE = 1;

    public interface FloatCallback {
        void run(float value);
    }

    public interface Host {
        Project project();

        Scene scene();

        Layer selected();

        double playhead();

        boolean autoKey();

        void beforeEdit();

        void edited();

        void requestRedraw();

        void selectLayer(Layer layer);

        void selectBone(Bone bone);

        void requestText(Layer layer);

        void requestImage(Layer layer);

        void requestAddLayer(LayerType type);

        void openShaderLab();

        void addEffect(EffectType type);

        void addPreset(String presetId);

        void switchScene(int index);

        void addScene();

        void duplicateScene();

        void deleteScene();

        void message(String message);
    }

    private final Context context;
    private final Theme theme;
    private final Host host;
    private final LinearLayout content;
    private int mode = MODE_LAYER;

    public InspectorPanel(Context context, Theme theme, Host host) {
        super(context);
        this.context = context;
        this.theme = theme;
        this.host = host;
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        content = Ui.column(context);
        content.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(18));
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        bind();
    }

    public void setMode(int mode) {
        this.mode = mode;
        bind();
    }

    public int getMode() {
        return mode;
    }

    public void bind() {
        final int scroll = getScrollY();
        content.removeAllViews();
        Project project = host.project();
        Scene scene = host.scene();
        if (project == null || scene == null) {
            content.addView(Ui.label(context, theme, "No scene loaded.", 13, theme.textDim, false));
            return;
        }
        if (mode == MODE_SCENE) buildScene(project, scene);
        else buildLayer(project, scene);
        post(() -> {
            int max = Math.max(0, content.getHeight() - getHeight());
            scrollTo(0, Math.min(scroll, max));
        });
    }

    private void bindSoon() {
        postDelayed(this::bind, 40);
    }

    // --------------------------------------------------------------- layer

    private void buildLayer(Project project, Scene scene) {
        Layer layer = host.selected();
        if (layer == null) {
            content.addView(Ui.label(context, theme,
                    "Select a layer on the canvas or in the list below.", 12.5f, theme.textDim, false));
            addSpace(8);
        } else {
            buildLayerHeader(scene, layer);
            addSpace(2);
            LinearLayout transform = card("TRANSFORM");
            channelRow(transform, layer, "Position X", Layer.X, -project.width, project.width * 2f, 0f, 0);
            channelRow(transform, layer, "Position Y", Layer.Y, -project.height, project.height * 2f, 0f, 0);
            channelRow(transform, layer, "Scale", Layer.SCALE, 0.05f, 8f, 0.01f, 2);
            channelRow(transform, layer, "Rotation", Layer.ROT, -180f, 180f, 0.5f, 1);
            channelRow(transform, layer, "Opacity", Layer.OPACITY, 0f, 1f, 0.01f, 2);

            if (layer.type == LayerType.RIG) buildRig(layer);
            if (layer.type == LayerType.SHAPE) buildShape(layer);
            if (layer.type == LayerType.TEXT) buildText(layer);
            if (layer.type == LayerType.IMAGE) buildImage(layer);
            if (layer.type == LayerType.DRAW) buildDraw(layer);
            if (layer.type == LayerType.CAMERA) buildCamera(project, layer);
        }
        buildLayerStack(scene);
    }

    private void buildLayerHeader(Scene scene, Layer layer) {
        LinearLayout box = card(null);
        LinearLayout head = Ui.row(context);
        head.addView(new IconView(context, typeIcon(layer), theme.accent, 17));
        TextView name = Ui.label(context, theme, layer.name, 15, theme.text, true);
        name.setPadding(Ui.dp(8), 0, 0, 0);
        head.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        IconButton eye = new IconButton(context, theme, layer.visible ? "eye" : "eyeoff", null, IconButton.Style.BAR);
        eye.setActive(!layer.visible);
        eye.setActiveTint(theme.danger);
        head.addView(eye, new LinearLayout.LayoutParams(Ui.dp(32), Ui.dp(32)));
        IconButton lock = new IconButton(context, theme, layer.locked ? "lock" : "unlock", null, IconButton.Style.BAR);
        lock.setActive(layer.locked);
        lock.setActiveTint(theme.key);
        head.addView(lock, new LinearLayout.LayoutParams(Ui.dp(32), Ui.dp(32)));
        box.addView(head);
        TextView kind = Ui.label(context, theme,
                layer.type.label + (layer.type == LayerType.RIG && layer.rig != null
                        ? "  \u00b7  " + RigLibrary.label(layer.rig.style) : ""),
                11, theme.textDim, false);
        kind.setPadding(Ui.dp(4), Ui.dp(2), 0, 0);
        box.addView(kind);

        LinearLayout actions = Ui.row(context);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = Ui.dp(8);
        actions.addView(Ui.chip(context, theme, "Rename", false, () -> renameLayer(layer)));
        actions.addView(Ui.space(context, 6, 1));
        if (layer.type != LayerType.CAMERA) {
            actions.addView(Ui.chip(context, theme, "Duplicate", false, () -> duplicateLayer(scene, layer)));
            actions.addView(Ui.space(context, 6, 1));
            actions.addView(Ui.chip(context, theme, "Delete", false, () -> deleteLayer(scene, layer)));
        }
        box.addView(actions, actionsLp);

        eye.setOnClickListener(v -> {
            host.beforeEdit();
            layer.visible = !layer.visible;
            host.edited();
            bind();
        });
        lock.setOnClickListener(v -> {
            host.beforeEdit();
            layer.locked = !layer.locked;
            host.edited();
            bind();
        });
    }

    private void renameLayer(Layer layer) {
        Ui.prompt(context, theme, "Rename layer", "Layer name", layer.name, text -> {
            if (text == null || text.isEmpty()) return;
            host.beforeEdit();
            layer.name = text;
            host.edited();
            bind();
        });
    }

    private void duplicateLayer(Scene scene, Layer layer) {
        host.beforeEdit();
        Layer copy = layer.copy();
        copy.id = Ids.next(layer.type.name().toLowerCase(java.util.Locale.US));
        copy.name = layer.name + " copy";
        int index = scene.indexOf(layer.id);
        scene.layers.add(Math.max(0, index + 1), copy);
        host.edited();
        host.selectLayer(copy);
        bind();
    }

    private void deleteLayer(Scene scene, Layer layer) {
        if (layer.type == LayerType.CAMERA) return;
        Ui.confirm(context, theme, "Delete layer",
                "Remove \u201c" + layer.name + "\u201d and all of its keys?", "Delete", () -> {
                    host.beforeEdit();
                    scene.remove(layer);
                    host.edited();
                    Layer next = null;
                    for (int i = scene.layers.size() - 1; i >= 0; i--) {
                        if (scene.layers.get(i).type != LayerType.CAMERA) {
                            next = scene.layers.get(i);
                            break;
                        }
                    }
                    host.selectLayer(next);
                    bind();
                });
    }

    // ---------------------------------------------------------------- rig

    private void buildRig(Layer layer) {
        LinearLayout face = card("FACE");
        channelRow(face, layer, "Blink", Layer.FACE_BLINK, 0f, 1f, 0.01f, 2);
        channelRow(face, layer, "Brow", Layer.FACE_BROW, -1f, 1f, 0.01f, 2);
        channelRow(face, layer, "Mouth", Layer.FACE_MOUTH, 0f, 1f, 0.01f, 2);
        channelRow(face, layer, "Eye look", Layer.FACE_LOOK, -1f, 1f, 0.01f, 2);
        LinearLayout poseRow = Ui.row(context);
        LinearLayout.LayoutParams poseLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        poseLp.topMargin = Ui.dp(6);
        poseRow.addView(Ui.chip(context, theme, "Key full pose", false, () -> {
            host.beforeEdit();
            int count = Edits.keyFullPose(layer, host.playhead());
            host.edited();
            host.message(count + " pose keys written");
            bind();
        }));
        poseRow.addView(Ui.space(context, 6, 1));
        poseRow.addView(Ui.chip(context, theme, "Reset pose", false, () -> {
            host.beforeEdit();
            Edits.resetPose(layer);
            host.edited();
            bind();
        }));
        face.addView(poseRow, poseLp);

        if (layer.rig != null) {
            LinearLayout style = card("CHARACTER STYLE");
            LinearLayout chips = Ui.row(context);
            String[] keys = RigLibrary.keys();
            for (String key : keys) {
                final String rigKey = key;
                boolean active = key.equals(layer.rig.style);
                TextView chip = Ui.chip(context, theme, RigLibrary.label(key), active, () -> {
                    host.beforeEdit();
                    layer.rig = RigLibrary.create(rigKey);
                    layer.color = layer.rig.accent;
                    host.edited();
                    bind();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = Ui.dp(5);
                chips.addView(chip, lp);
            }
            style.addView(chips);
            addSpace(4);

            LinearLayout bones = card("BONES");
            TextView hint = Ui.label(context, theme,
                    "Drag a slider to bend the limb. Pose tool does the same on the canvas.",
                    10.5f, theme.textDim, false);
            bones.addView(hint);
            for (Bone bone : layer.rig.bones) {
                channelRow(bones, layer, bone.name, Layer.boneChannel(bone.id), -180f, 180f, 0.5f, 1);
            }
        }
    }

    // -------------------------------------------------------------- shape

    private void buildShape(Layer layer) {
        LinearLayout box = card("SHAPE");
        LinearLayout row1 = Ui.row(context);
        LinearLayout row2 = Ui.row(context);
        String[] kinds = {"rect", "ellipse", "triangle", "star", "burst", "line", "frame", "ring"};
        String[] names = {"Rect", "Ellipse", "Triangle", "Star", "Burst", "Line", "Frame", "Ring"};
        for (int i = 0; i < kinds.length; i++) {
            final String kind = kinds[i];
            boolean active = kind.equals(layer.shape);
            TextView chip = Ui.chip(context, theme, names[i], active, () -> {
                host.beforeEdit();
                layer.shape = kind;
                host.edited();
                bind();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(5);
            lp.bottomMargin = Ui.dp(5);
            (i < 4 ? row1 : row2).addView(chip, lp);
        }
        box.addView(row1);
        box.addView(row2);
        valueRow(box, "Width", layer.shapeWidth, 8f, 4000f, 1f, 0, v -> layer.shapeWidth = v);
        valueRow(box, "Height", layer.shapeHeight, 8f, 4000f, 1f, 0, v -> layer.shapeHeight = v);
        LinearLayout colourRow = Ui.row(context);
        colourRow.addView(Ui.label(context, theme, "Fill", 11.5f, theme.textDim, false),
                new LinearLayout.LayoutParams(Ui.dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));
        View swatch = new View(context);
        swatch.setBackground(Ui.rounded(layer.shapeFill, 8, theme.border, 1));
        colourRow.addView(swatch, new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(24)));
        colourRow.addView(Ui.space(context, 8, 1));
        colourRow.addView(Ui.chip(context, theme, "Choose colour", false, () ->
                Dialogs.color(context, theme, "Shape fill", layer.shapeFill, value -> {
                    host.beforeEdit();
                    layer.shapeFill = value;
                    layer.color = value;
                    host.edited();
                    bind();
                })));
        LinearLayout.LayoutParams colourLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        colourLp.topMargin = Ui.dp(4);
        box.addView(colourRow, colourLp);
    }

    // --------------------------------------------------------------- text

    private void buildText(Layer layer) {
        LinearLayout box = card("TEXT");
        String preview = layer.text == null || layer.text.isEmpty() ? "(empty)" : layer.text;
        if (preview.length() > 90) preview = preview.substring(0, 90) + "\u2026";
        TextView content = Ui.label(context, theme, preview, 12.5f, theme.text, false);
        content.setLineSpacing(Ui.dp(3), 1f);
        box.addView(content);
        LinearLayout actions = Ui.row(context);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = Ui.dp(8);
        actions.addView(Ui.chip(context, theme, "Edit text", false, () -> host.requestText(layer)));
        actions.addView(Ui.space(context, 6, 1));
        actions.addView(Ui.chip(context, theme, "Colour", false, () ->
                Dialogs.color(context, theme, "Text colour", layer.color, value -> {
                    host.beforeEdit();
                    layer.color = value;
                    host.edited();
                    bind();
                })));
        box.addView(actions, actionsLp);
        valueRow(box, "Size", layer.textSize, 8f, 400f, 1f, 0, v -> layer.textSize = v);
    }

    // -------------------------------------------------------------- image

    private void buildImage(Layer layer) {
        LinearLayout box = card("IMAGE");
        TextView path = Ui.label(context, theme,
                layer.asset == null ? "No image selected" : layer.asset,
                11, theme.textDim, false);
        path.setSingleLine(true);
        box.addView(path);
        LinearLayout actions = Ui.row(context);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = Ui.dp(8);
        actions.addView(Ui.chip(context, theme, layer.asset == null ? "Choose image" : "Replace image",
                false, () -> host.requestImage(layer)));
        box.addView(actions, actionsLp);
        valueRow(box, "Width", layer.shapeWidth > 1 ? layer.shapeWidth : 400f, 8f, 6000f, 1f, 0, v -> layer.shapeWidth = v);
        valueRow(box, "Height", layer.shapeHeight > 1 ? layer.shapeHeight : 400f, 8f, 6000f, 1f, 0, v -> layer.shapeHeight = v);
    }

    // --------------------------------------------------------------- draw

    private void buildDraw(Layer layer) {
        LinearLayout box = card("DRAWING");
        TextView info = Ui.label(context, theme,
                layer.strokes.size() + (layer.strokes.size() == 1 ? " stroke" : " strokes") + "  \u00b7  "
                        + layer.lastStrokeFrame() + " last frame",
                11.5f, theme.textDim, false);
        box.addView(info);
        LinearLayout actions = Ui.row(context);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = Ui.dp(8);
        actions.addView(Ui.chip(context, theme, "Clear frame", false, () -> {
            int frame = AnimMath.frameAt(host.playhead(), host.project().fps);
            host.beforeEdit();
            layer.strokes.removeIf(s -> s.frame == frame);
            host.edited();
            bind();
        }));
        actions.addView(Ui.space(context, 6, 1));
        actions.addView(Ui.chip(context, theme, "Clear all", false, () ->
                Ui.confirm(context, theme, "Clear drawing",
                        "Remove every stroke on \u201c" + layer.name + "\u201d?", "Clear", () -> {
                            host.beforeEdit();
                            layer.strokes.clear();
                            host.edited();
                            bind();
                        })));
        box.addView(actions, actionsLp);
        LinearLayout modeRow = Ui.row(context);
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        modeLp.topMargin = Ui.dp(6);
        modeRow.addView(Ui.chip(context, theme, layer.cumulativeStrokes ? "Cumulative" : "Frame only",
                layer.cumulativeStrokes, () -> {
                    host.beforeEdit();
                    layer.cumulativeStrokes = !layer.cumulativeStrokes;
                    host.edited();
                    bind();
                }));
        box.addView(modeRow, modeLp);
    }

    // -------------------------------------------------------------- camera

    private void buildCamera(Project project, Layer layer) {
        LinearLayout box = card("CAMERA");
        TextView hint = Ui.label(context, theme,
                "The camera is the shot. Key its position, scale and roll on the timeline.",
                11.5f, theme.textDim, false);
        hint.setLineSpacing(Ui.dp(3), 1f);
        box.addView(hint);
        LinearLayout actions = Ui.row(context);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = Ui.dp(8);
        actions.addView(Ui.chip(context, theme, "Reset camera", false, () -> {
            host.beforeEdit();
            layer.channels.clear();
            layer.setKey(Layer.X, 0, project.width / 2f);
            layer.setKey(Layer.Y, 0, project.height / 2f);
            layer.setKey(Layer.SCALE, 0, 1f);
            layer.setKey(Layer.ROT, 0, 0f);
            host.edited();
            host.requestRedraw();
            bind();
        }));
        box.addView(actions, actionsLp);
    }

    // --------------------------------------------------------- layer stack

    private void buildLayerStack(Scene scene) {
        LinearLayout box = card("LAYERS  \u00b7  TOP FIRST");
        Layer selected = host.selected();
        for (int i = scene.layers.size() - 1; i >= 0; i--) {
            Layer layer = scene.layers.get(i);
            final Layer rowLayer = layer;
            LinearLayout row = Ui.row(context);
            row.setPadding(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
            boolean active = selected != null && selected.id.equals(layer.id);
            row.setBackground(Ui.rounded(active ? Ui.alpha(theme.accent, 0.12f) : theme.panel,
                    8, active ? theme.accent : theme.border, active ? 1.5f : 0));
            row.addView(new IconView(context, typeIcon(layer), active ? theme.accent : theme.textDim, 13));
            TextView name = Ui.label(context, theme, layer.name, 12, active ? theme.text : theme.textDim, active);
            name.setSingleLine(true);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nameLp.leftMargin = Ui.dp(4);
            row.addView(name, nameLp);
            IconButton eye = new IconButton(context, theme, layer.visible ? "eye" : "eyeoff", null, IconButton.Style.BAR);
            row.addView(eye, new LinearLayout.LayoutParams(Ui.dp(26), Ui.dp(26)));
            IconButton lock = new IconButton(context, theme, layer.locked ? "lock" : "unlock", null, IconButton.Style.BAR);
            row.addView(lock, new LinearLayout.LayoutParams(Ui.dp(26), Ui.dp(26)));
            if (layer.type != LayerType.CAMERA) {
                IconButton up = new IconButton(context, theme, "up", null, IconButton.Style.BAR);
                IconButton down = new IconButton(context, theme, "down", null, IconButton.Style.BAR);
                row.addView(up, new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24)));
                row.addView(down, new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24)));
                up.setOnClickListener(v -> {
                    int index = scene.indexOf(layer.id);
                    if (index < 0 || index >= scene.layers.size() - 1) return;
                    host.beforeEdit();
                    scene.moveLayer(index, index + 1);
                    host.edited();
                    bind();
                });
                down.setOnClickListener(v -> {
                    int index = scene.indexOf(layer.id);
                    if (index <= 0) return;
                    host.beforeEdit();
                    scene.moveLayer(index, index - 1);
                    host.edited();
                    bind();
                });
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = Ui.dp(3);
            box.addView(row, rowLp);
            row.setOnClickListener(v -> {
                host.selectLayer(rowLayer);
                bind();
            });
            eye.setOnClickListener(v -> {
                host.beforeEdit();
                rowLayer.visible = !rowLayer.visible;
                host.edited();
                bind();
            });
            lock.setOnClickListener(v -> {
                host.beforeEdit();
                rowLayer.locked = !rowLayer.locked;
                host.edited();
                bind();
            });
        }
        LinearLayout add = Ui.row(context);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        addLp.topMargin = Ui.dp(8);
        add.addView(Ui.chip(context, theme, "+ Character", false, () -> host.requestAddLayer(LayerType.RIG)));
        add.addView(Ui.space(context, 5, 1));
        add.addView(Ui.chip(context, theme, "+ Shape", false, () -> host.requestAddLayer(LayerType.SHAPE)));
        add.addView(Ui.space(context, 5, 1));
        add.addView(Ui.chip(context, theme, "+ Text", false, () -> host.requestAddLayer(LayerType.TEXT)));
        box.addView(add, addLp);
        LinearLayout add2 = Ui.row(context);
        LinearLayout.LayoutParams add2Lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        add2Lp.topMargin = Ui.dp(5);
        add2.addView(Ui.chip(context, theme, "+ Drawing", false, () -> host.requestAddLayer(LayerType.DRAW)));
        add2.addView(Ui.space(context, 5, 1));
        add2.addView(Ui.chip(context, theme, "+ Image", false, () -> host.requestAddLayer(LayerType.IMAGE)));
        box.addView(add2, add2Lp);
    }

    // --------------------------------------------------------------- scene

    private void buildScene(Project project, Scene scene) {
        LinearLayout settings = card("SCENE");
        LinearLayout nameRow = Ui.row(context);
        nameRow.addView(new IconView(context, "film", theme.accent, 14));
        TextView name = Ui.label(context, theme, scene.name, 14.5f, theme.text, true);
        name.setPadding(Ui.dp(6), 0, 0, 0);
        nameRow.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nameRow.addView(Ui.chip(context, theme, "Rename", false, () ->
                Ui.prompt(context, theme, "Scene name", "Scene name", scene.name, text -> {
                    if (text == null || text.isEmpty()) return;
                    host.beforeEdit();
                    scene.name = text;
                    host.edited();
                    bind();
                })));
        settings.addView(nameRow);
        TextView meta = Ui.label(context, theme,
                project.scenes.size() + (project.scenes.size() == 1 ? " scene" : " scenes")
                        + "  \u00b7  " + project.fps + " fps  \u00b7  " + project.width + " \u00d7 " + project.height,
                11, theme.textDim, false);
        meta.setPadding(Ui.dp(2), Ui.dp(2), 0, Ui.dp(4));
        settings.addView(meta);

        valueRow(settings, "Duration", (float) scene.duration, 0.5f, 60f, 0.1f, 1, v -> scene.duration = v);

        LinearLayout bgRow = Ui.row(context);
        LinearLayout.LayoutParams bgLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bgLp.topMargin = Ui.dp(4);
        bgRow.addView(Ui.label(context, theme, "Background", 11.5f, theme.textDim, false),
                new LinearLayout.LayoutParams(Ui.dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));
        View top = new View(context);
        top.setBackground(Ui.rounded(scene.bgTop, 8, theme.border, 1));
        bgRow.addView(top, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(24)));
        bgRow.addView(Ui.space(context, 6, 1));
        View bottom = new View(context);
        bottom.setBackground(Ui.rounded(scene.bgBottom, 8, theme.border, 1));
        bgRow.addView(bottom, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(24)));
        bgRow.addView(Ui.space(context, 8, 1));
        bgRow.addView(Ui.chip(context, theme, "Top", false, () ->
                Dialogs.color(context, theme, "Background top", scene.bgTop, value -> {
                    host.beforeEdit();
                    scene.bgTop = value;
                    host.edited();
                    bind();
                })));
        bgRow.addView(Ui.space(context, 5, 1));
        bgRow.addView(Ui.chip(context, theme, "Bottom", false, () ->
                Dialogs.color(context, theme, "Background bottom", scene.bgBottom, value -> {
                    host.beforeEdit();
                    scene.bgBottom = value;
                    host.edited();
                    bind();
                })));
        settings.addView(bgRow, bgLp);

        buildSceneList(project, scene);
        buildEffects(scene);
    }

    private void buildSceneList(Project project, Scene scene) {
        LinearLayout box = card("SCENES");
        for (int i = 0; i < project.scenes.size(); i++) {
            Scene candidate = project.scenes.get(i);
            final int index = i;
            boolean active = candidate == scene;
            LinearLayout row = Ui.row(context);
            row.setPadding(Ui.dp(6), Ui.dp(7), Ui.dp(6), Ui.dp(7));
            row.setBackground(Ui.rounded(active ? Ui.alpha(theme.accent, 0.12f) : theme.panel, 8,
                    active ? theme.accent : theme.border, active ? 1.5f : 0));
            TextView label = Ui.label(context, theme, (i + 1) + ".  " + candidate.name,
                    12.5f, active ? theme.text : theme.textDim, active);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView duration = Ui.label(context, theme, Ui.fixed((float) candidate.duration, 1) + "s",
                    10.5f, theme.textDim, false);
            row.addView(duration);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = Ui.dp(3);
            box.addView(row, rowLp);
            row.setOnClickListener(v -> {
                if (index != host.project().sceneIndex(scene.id)) host.switchScene(index);
            });
        }
        LinearLayout actions = Ui.row(context);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = Ui.dp(8);
        actions.addView(Ui.chip(context, theme, "+ Scene", false, () -> host.addScene()));
        actions.addView(Ui.space(context, 5, 1));
        actions.addView(Ui.chip(context, theme, "Duplicate", false, () -> host.duplicateScene()));
        actions.addView(Ui.space(context, 5, 1));
        actions.addView(Ui.chip(context, theme, "Delete", false, () -> host.deleteScene()));
        box.addView(actions, actionsLp);
    }

    private void buildEffects(Scene scene) {
        LinearLayout tools = card("SHADER STACK");
        TextView hint = Ui.label(context, theme,
                "Effects run top to bottom on the scene, in the preview and in the export.",
                11, theme.textDim, false);
        hint.setLineSpacing(Ui.dp(3), 1f);
        tools.addView(hint);
        LinearLayout actions = Ui.row(context);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = Ui.dp(8);
        actions.addView(Ui.chip(context, theme, "+ Effect", false, () ->
                Dialogs.effect(context, theme, index -> host.addEffect(EffectType.values()[index]))));
        actions.addView(Ui.space(context, 5, 1));
        actions.addView(Ui.chip(context, theme, "Preset", false, () ->
                Dialogs.preset(context, theme, index -> host.addPreset(EffectPresets.all().get(index).id))));
        actions.addView(Ui.space(context, 5, 1));
        actions.addView(Ui.chip(context, theme, "Shader Lab", false, () -> host.openShaderLab()));
        tools.addView(actions, actionsLp);

        if (scene.effects.isEmpty()) {
            TextView empty = Ui.label(context, theme, "No effects on this scene.",
                    11.5f, theme.textDim, false);
            empty.setPadding(0, Ui.dp(6), 0, 0);
            tools.addView(empty);
            return;
        }

        for (int i = 0; i < scene.effects.size(); i++) {
            Effect effect = scene.effects.get(i);
            final int index = i;
            LinearLayout box = card(null);
            LinearLayout head = Ui.row(context);
            TextView label = Ui.label(context, theme, effect.type.label, 13, theme.text, true);
            head.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            head.addView(Ui.chip(context, theme, effect.enabled ? "On" : "Off", effect.enabled, () -> {
                host.beforeEdit();
                effect.enabled = !effect.enabled;
                host.edited();
                host.requestRedraw();
                bind();
            }));
            IconButton up = new IconButton(context, theme, "up", null, IconButton.Style.BAR);
            IconButton down = new IconButton(context, theme, "down", null, IconButton.Style.BAR);
            IconButton trash = new IconButton(context, theme, "trash", null, IconButton.Style.BAR);
            head.addView(up, new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24)));
            head.addView(down, new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24)));
            head.addView(trash, new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24)));
            box.addView(head);
            valueRow(box, "Intensity", effect.intensity, 0f, 1f, 0.01f, 2, v -> effect.intensity = v);
            for (int p = 0; p < effect.type.params.length; p++) {
                String param = effect.type.params[p];
                float[] range = paramRange(param);
                valueRow(box, effect.type.paramLabels[p], effect.param(param), range[0], range[1],
                        (range[1] - range[0]) / 200f, 2, v -> effect.setParam(param, v));
            }
            up.setOnClickListener(v -> {
                if (index <= 0) return;
                host.beforeEdit();
                scene.effects.remove(index);
                scene.effects.add(index - 1, effect);
                host.edited();
                host.requestRedraw();
                bind();
            });
            down.setOnClickListener(v -> {
                if (index >= scene.effects.size() - 1) return;
                host.beforeEdit();
                scene.effects.remove(index);
                scene.effects.add(index + 1, effect);
                host.edited();
                host.requestRedraw();
                bind();
            });
            trash.setOnClickListener(v -> {
                host.beforeEdit();
                scene.effects.remove(effect);
                host.edited();
                host.requestRedraw();
                bind();
            });
        }
    }

    private static float[] paramRange(String param) {
        switch (param) {
            case "saturation":
                return new float[]{0f, 2f};
            case "contrast":
                return new float[]{0.4f, 2f};
            case "warmth":
            case "lift":
                return new float[]{-1f, 1f};
            case "width":
                return new float[]{0.5f, 6f};
            case "radius":
                return new float[]{0f, 1.5f};
            case "density":
            case "length":
                return new float[]{0f, 1f};
            default:
                return new float[]{0f, 1f};
        }
    }

    // ------------------------------------------------------------- widgets

    private void channelRow(LinearLayout parent, Layer layer, String label, String channel,
                            float min, float max, float step, int decimals) {
        final double time = host.playhead();
        float value = layer.sample(channel, time, Edits.defaultValue(layer, channel));
        boolean keyed = Edits.hasKeyAt(layer, channel, time);

        LinearLayout row = Ui.row(context);
        TextView name = Ui.label(context, theme, label, 11.5f, theme.textDim, false);
        name.setSingleLine(true);
        row.addView(name, new LinearLayout.LayoutParams(Ui.dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));
        SliderView slider = new SliderView(context);
        slider.configure(min, max, value, step, decimals, theme.border, theme.accent, theme.text);
        row.addView(slider, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView valueLabel = Ui.label(context, theme, slider.formatted(), 11.5f, theme.text, true);
        valueLabel.setGravity(Gravity.END);
        row.addView(valueLabel, new LinearLayout.LayoutParams(Ui.dp(46), ViewGroup.LayoutParams.WRAP_CONTENT));
        IconButton key = new IconButton(context, theme, keyed ? "keyfill" : "key", null, IconButton.Style.BAR);
        key.setActive(keyed);
        key.setActiveTint(theme.key);
        row.addView(key, new LinearLayout.LayoutParams(Ui.dp(28), Ui.dp(28)));

        slider.setListener(new SliderView.Listener() {
            @Override
            public void onStart(float v) {
                host.beforeEdit();
            }

            @Override
            public void onChanging(float v) {
                Edits.setChannel(layer, channel, host.playhead(), v, host.autoKey());
                valueLabel.setText(slider.formatted());
                host.requestRedraw();
            }

            @Override
            public void onCommit(float v) {
                host.edited();
                bindSoon();
            }
        });
        key.setOnClickListener(v -> {
            host.beforeEdit();
            boolean added = Edits.toggleKey(layer, channel, host.playhead());
            host.edited();
            host.message(added ? "Key added" : "Key removed");
            bind();
        });

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Ui.dp(2);
        parent.addView(row, rowLp);
    }

    private void valueRow(LinearLayout parent, String label, float value, float min, float max,
                          float step, int decimals, FloatCallback apply) {
        LinearLayout row = Ui.row(context);
        TextView name = Ui.label(context, theme, label, 11.5f, theme.textDim, false);
        name.setSingleLine(true);
        row.addView(name, new LinearLayout.LayoutParams(Ui.dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));
        SliderView slider = new SliderView(context);
        slider.configure(min, max, value, step, decimals, theme.border, theme.accent, theme.text);
        row.addView(slider, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView valueLabel = Ui.label(context, theme, slider.formatted(), 11.5f, theme.text, true);
        valueLabel.setGravity(Gravity.END);
        row.addView(valueLabel, new LinearLayout.LayoutParams(Ui.dp(50), ViewGroup.LayoutParams.WRAP_CONTENT));
        slider.setListener(new SliderView.Listener() {
            @Override
            public void onStart(float v) {
                host.beforeEdit();
            }

            @Override
            public void onChanging(float v) {
                apply.run(v);
                valueLabel.setText(slider.formatted());
                host.requestRedraw();
            }

            @Override
            public void onCommit(float v) {
                host.edited();
                bindSoon();
            }
        });
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Ui.dp(2);
        parent.addView(row, rowLp);
    }

    private LinearLayout card(String title) {
        LinearLayout box = Ui.column(context);
        box.setBackground(Ui.rounded(theme.panelAlt, 10, theme.border, 1));
        box.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(9));
        if (title != null) {
            TextView heading = Ui.label(context, theme, title, 10, theme.accent, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(5);
            box.addView(heading, lp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(8);
        content.addView(box, lp);
        return box;
    }

    private void addSpace(int dp) {
        content.addView(Ui.space(context, 1, dp));
    }

    private static String typeIcon(Layer layer) {
        switch (layer.type) {
            case RIG:
                return "pose";
            case DRAW:
                return "brush";
            case TEXT:
                return "text";
            case IMAGE:
                return "image";
            case CAMERA:
                return "camera";
            case SHAPE:
            default:
                return "shape";
        }
    }
}
