package app.bxanimator;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.bxanimator.core.Effect;
import app.bxanimator.core.EffectType;
import app.bxanimator.core.Project;
import app.bxanimator.core.Scene;
import app.bxanimator.core.Theme;
import app.bxanimator.gl.PreviewGLView;
import app.bxanimator.render.SceneRenderer;
import app.bxanimator.ui.IconButton;
import app.bxanimator.ui.SliderView;
import app.bxanimator.ui.Ui;

/** Live GLSL preview of every shader, with an apply button for the scene. */
public final class ShaderLabActivity extends Activity {

    private BxApp app;
    private Theme theme;
    private Project project;
    private String projectId;
    private int sceneIndex;
    private Scene scene;
    private boolean fromProject;

    private PreviewGLView preview;
    private final SceneRenderer renderer = new SceneRenderer();
    private final Bitmap[] buffers = new Bitmap[2];
    private int bufferIndex;

    private EffectType effect = EffectType.COLOR_GRADE;
    private float intensity = 0.6f;
    private final float[] params = new float[]{1f, 1f, 0f, 0f};
    private double time;

    private LinearLayout effectRow;
    private LinearLayout paramsBox;
    private TextView titleSub;
    private SliderView timeSlider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = BxApp.get(this);
        Ui.init(this);
        theme = app.theme();
        BxApp.styleWindow(this, theme);

        projectId = getIntent().getStringExtra("project_id");
        if (projectId != null) {
            try {
                project = app.store().load(projectId);
                fromProject = project != null;
            } catch (Exception ignored) {
                project = null;
            }
        }
        if (project == null) {
            project = Project.create("Shader Lab", "action");
            fromProject = false;
        }
        sceneIndex = Math.max(0, Math.min(getIntent().getIntExtra("scene_index", 0),
                project.scenes.size() - 1));
        scene = project.scene(sceneIndex);
        renderer.setAssetsDir(app.store().assetsDir(project.id));
        params[0] = effect.defaults[0];
        params[1] = effect.defaults.length > 1 ? effect.defaults[1] : 0f;
        params[2] = effect.defaults.length > 2 ? effect.defaults[2] : 0f;
        params[3] = effect.defaults.length > 3 ? effect.defaults[3] : 0f;

        buildUi();
        renderFrame();
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(theme.bg);
        setContentView(root);

        LinearLayout header = Ui.row(this);
        header.setPadding(Ui.dp(6), Ui.dp(8), Ui.dp(12), Ui.dp(8));
        header.setBackgroundColor(theme.header);
        IconButton back = new IconButton(this, theme, "back", null, IconButton.Style.BAR);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)));
        LinearLayout titles = Ui.column(this);
        titles.setPadding(Ui.dp(6), 0, 0, 0);
        titles.addView(Ui.label(this, theme, "Shader Lab", 16, theme.text, true));
        titleSub = Ui.label(this, theme, "GLSL ES 2.0  \u00b7  " + effect.glsl, 10.5f, theme.textDim, false);
        titles.addView(titleSub);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView apply = Ui.button(this, theme, "Apply", true, this::applyToScene);
        header.addView(apply, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(38)));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        preview = new PreviewGLView(this);
        root.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout controls = Ui.column(this);
        controls.setBackgroundColor(theme.panel);
        controls.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(12));

        HorizontalScrollView effectScroll = new HorizontalScrollView(this);
        effectScroll.setHorizontalScrollBarEnabled(false);
        effectRow = Ui.row(this);
        effectScroll.addView(effectRow);
        controls.addView(effectScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        rebuildEffectChips();

        LinearLayout intensityRow = Ui.row(this);
        LinearLayout.LayoutParams intensityLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        intensityLp.topMargin = Ui.dp(4);
        TextView intensityLabel = Ui.label(this, theme, "Intensity", 11.5f, theme.textDim, false);
        intensityRow.addView(intensityLabel, new LinearLayout.LayoutParams(Ui.dp(78),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        SliderView intensitySlider = new SliderView(this);
        intensitySlider.configure(0f, 1f, intensity, 0.01f, 2, theme.border, theme.accent, theme.text);
        intensityRow.addView(intensitySlider, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView intensityValue = Ui.label(this, theme, intensitySlider.formatted(), 11.5f, theme.text, true);
        intensityValue.setGravity(Gravity.END);
        intensityRow.addView(intensityValue, new LinearLayout.LayoutParams(Ui.dp(46),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        intensitySlider.setListener(new SliderView.Listener() {
            @Override
            public void onStart(float value) {
            }

            @Override
            public void onChanging(float value) {
                intensity = value;
                intensityValue.setText(intensitySlider.formatted());
                preview.setUniforms(intensity, params);
            }

            @Override
            public void onCommit(float value) {
            }
        });
        controls.addView(intensityRow, intensityLp);

        ScrollView paramsScroll = new ScrollView(this);
        paramsScroll.setVerticalScrollBarEnabled(false);
        paramsBox = Ui.column(this);
        paramsScroll.addView(paramsBox);
        controls.addView(paramsScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(96)));
        rebuildParams();

        LinearLayout timeRow = Ui.row(this);
        timeRow.addView(Ui.label(this, theme, "Time", 11.5f, theme.textDim, false),
                new LinearLayout.LayoutParams(Ui.dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));
        timeSlider = new SliderView(this);
        timeSlider.configure(0f, (float) scene.duration, 0f, 1f / Math.max(1, project.fps),
                2, theme.border, theme.accent, theme.text);
        timeRow.addView(timeSlider, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView timeValue = Ui.label(this, theme, "0.00", 11.5f, theme.text, true);
        timeValue.setGravity(Gravity.END);
        timeRow.addView(timeValue, new LinearLayout.LayoutParams(Ui.dp(46), ViewGroup.LayoutParams.WRAP_CONTENT));
        timeSlider.setListener(new SliderView.Listener() {
            @Override
            public void onStart(float value) {
            }

            @Override
            public void onChanging(float value) {
                time = value;
                timeValue.setText(timeSlider.formatted());
                renderFrame();
            }

            @Override
            public void onCommit(float value) {
            }
        });
        controls.addView(timeRow);

        LinearLayout footer = Ui.row(this);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = Ui.dp(4);
        footer.addView(Ui.chip(this, theme, "Reset shader", false, () -> {
            intensity = 0.6f;
            for (int i = 0; i < effect.params.length && i < params.length; i++) params[i] = effect.defaults[i];
            intensitySlider.setValue(intensity);
            intensityValue.setText(intensitySlider.formatted());
            rebuildParams();
            preview.setUniforms(intensity, params);
        }));
        footer.addView(Ui.space(this, 8, 1));
        footer.addView(Ui.chip(this, theme, "Re-render frame", false, this::renderFrame));
        footer.addView(Ui.flexibleSpace(this));
        TextView note = Ui.label(this, theme, fromProject ? scene.name : "demo scene",
                10.5f, theme.textDim, false);
        footer.addView(note);
        controls.addView(footer, footerLp);

        root.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void rebuildEffectChips() {
        effectRow.removeAllViews();
        for (EffectType type : EffectType.values()) {
            TextView chip = Ui.chip(this, theme, type.label, type == effect, () -> {
                effect = type;
                intensity = 0.6f;
                for (int i = 0; i < params.length; i++) {
                    params[i] = i < type.defaults.length ? type.defaults[i] : 0f;
                }
                titleSub.setText("GLSL ES 2.0  \u00b7  " + type.glsl);
                rebuildEffectChips();
                rebuildParams();
                renderFrame();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(5);
            effectRow.addView(chip, lp);
        }
    }

    private void rebuildParams() {
        paramsBox.removeAllViews();
        for (int i = 0; i < effect.params.length && i < params.length; i++) {
            final int index = i;
            LinearLayout row = Ui.row(this);
            TextView label = Ui.label(this, theme, effect.paramLabels[i], 11, theme.textDim, false);
            label.setSingleLine(true);
            row.addView(label, new LinearLayout.LayoutParams(Ui.dp(96), ViewGroup.LayoutParams.WRAP_CONTENT));
            float[] range = paramRange(effect.params[i]);
            SliderView slider = new SliderView(this);
            slider.configure(range[0], range[1], params[i], (range[1] - range[0]) / 200f, 2,
                    theme.border, theme.accent, theme.text);
            row.addView(slider, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView value = Ui.label(this, theme, slider.formatted(), 11, theme.text, true);
            value.setGravity(Gravity.END);
            row.addView(value, new LinearLayout.LayoutParams(Ui.dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
            slider.setListener(new SliderView.Listener() {
                @Override
                public void onStart(float v) {
                }

                @Override
                public void onChanging(float v) {
                    params[index] = v;
                    value.setText(slider.formatted());
                    preview.setUniforms(intensity, params);
                }

                @Override
                public void onCommit(float v) {
                }
            });
            paramsBox.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
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
            default:
                return new float[]{0f, 1f};
        }
    }

    // --------------------------------------------------------------- render

    private void renderFrame() {
        if (preview == null) return;
        int width = 720;
        int height = Math.max(2, Math.round(720f * project.height / Math.max(1f, project.width) / 2f) * 2);
        Bitmap frame = buffers[bufferIndex];
        if (frame == null || frame.getWidth() != width || frame.getHeight() != height) {
            frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            buffers[bufferIndex] = frame;
        }
        bufferIndex = 1 - bufferIndex;
        Canvas canvas = new Canvas(frame);
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC);
        renderer.draw(canvas, project, scene, time, width, height, 1f, 0, 0, false, 0, 1f);
        preview.setFrame(frame);
        preview.setEffect(effect.glsl, intensity, params);
        preview.setTime((float) time);
    }

    private void applyToScene() {
        if (!fromProject || projectId == null) {
            Ui.toast(this, "Open a project to apply a shader");
            return;
        }
        try {
            Project live = app.store().load(projectId);
            if (live.scenes.isEmpty()) throw new java.io.IOException("the project has no scenes");
            int index = Math.min(sceneIndex, live.scenes.size() - 1);
            Scene target = live.scenes.get(index);
            Effect chosen = null;
            for (Effect candidate : target.effects) {
                if (candidate.type == effect) {
                    chosen = candidate;
                    break;
                }
            }
            if (chosen == null) {
                chosen = Effect.of(effect, intensity);
                target.effects.add(chosen);
            }
            chosen.enabled = true;
            chosen.intensity = intensity;
            for (int i = 0; i < effect.params.length && i < params.length; i++) {
                chosen.setParam(effect.params[i], params[i]);
            }
            app.store().save(live, null);
            Ui.toast(this, effect.label + " applied to " + target.name);
            finish();
        } catch (Exception e) {
            Ui.alert(this, theme, "Could not apply", String.valueOf(e.getMessage()), "OK");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (preview != null) preview.onResume();
    }

    @Override
    protected void onPause() {
        if (preview != null) preview.onPause();
        super.onPause();
    }
}
