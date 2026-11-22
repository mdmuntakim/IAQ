package app.bxanimator;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import app.bxanimator.core.Key;
import app.bxanimator.core.Layer;
import app.bxanimator.core.LayerType;
import app.bxanimator.core.Project;
import app.bxanimator.core.ProjectFactory;
import app.bxanimator.core.RigLibrary;
import app.bxanimator.core.Scene;
import app.bxanimator.core.Theme;
import app.bxanimator.core.Track;
import app.bxanimator.export.FrameExporter;
import app.bxanimator.export.VideoExporter;
import app.bxanimator.io.MediaOut;
import app.bxanimator.render.SceneRenderer;
import app.bxanimator.render.ShaderPipeline;
import app.bxanimator.ui.CanvasView;
import app.bxanimator.ui.Dialogs;
import app.bxanimator.ui.IconButton;
import app.bxanimator.ui.InspectorPanel;
import app.bxanimator.ui.TimelineView;
import app.bxanimator.ui.TopBar;
import app.bxanimator.ui.Ui;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The animation editor: viewport, tool rail, timeline and inspector, all wired
 * to one project. Playback runs on the Choreographer, every edit is undoable
 * and the project autosaves in the background.
 */
public final class EditorActivity extends Activity implements
        CanvasView.Listener, TimelineView.Listener, TopBar.Listener, InspectorPanel.Host {

    private static final int REQ_IMAGE = 1001;

    private BxApp app;
    private Theme theme;
    private Project project;
    private int sceneIndex;
    private Layer selected;
    private double playhead;
    private boolean playing;
    private boolean onion;
    private boolean fxEnabled = true;
    private int quality = 1;
    private int tab;

    private TopBar topBar;
    private CanvasView canvas;
    private TimelineView timeline;
    private InspectorPanel layerInspector;
    private InspectorPanel sceneInspector;
    private LinearLayout panelHost;
    private LinearLayout tabsRow;

    private IconButton toolSelect;
    private IconButton toolPose;
    private IconButton toolDraw;
    private IconButton toolCamera;
    private IconButton railOnion;
    private IconButton railGrid;
    private IconButton railFx;

    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack = new ArrayDeque<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveRunnable = () -> saveNow(false);

    private SceneRenderer thumbRenderer;
    private long lastFrameNanos;
    private Dialog progressDialog;
    private ProgressBar progressBar;
    private TextView progressLabel;
    private volatile boolean cancelRequested;
    private Layer pendingImageLayer;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!playing) return;
            if (lastFrameNanos == 0) lastFrameNanos = frameTimeNanos;
            double dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000.0;
            lastFrameNanos = frameTimeNanos;
            if (dt < 0) dt = 0;
            if (dt > 0.25) dt = 0.25;
            Scene s = scene();
            double next = playhead + dt;
            if (next >= s.duration) next = next % Math.max(0.1, s.duration);
            setPlayhead(next);
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = BxApp.get(this);
        Ui.init(this);
        theme = app.theme();
        BxApp.styleWindow(this, theme);

        String id = getIntent().getStringExtra("project_id");
        if (id == null) id = app.lastProject();
        if (id != null) {
            try {
                project = app.store().load(id);
            } catch (Exception ignored) {
                project = null;
            }
        }
        if (project == null) {
            project = Project.create("Untitled", "action");
            try {
                app.store().save(project, null);
            } catch (Exception ignored) {
                // The editor still works in memory if storage is unavailable.
            }
        }
        app.rememberProject(project.id);
        sceneIndex = AnimMath.clamp(getIntent().getIntExtra("scene_index", 0), 0, project.scenes.size() - 1);
        selected = firstSelectable(scene());
        playhead = 0;
        onion = app.onion();
        quality = app.previewQuality();
        fxEnabled = true;

        buildUi();
        refreshAll();
        if (!app.helpSeen()) {
            app.markHelpSeen();
            handler.postDelayed(() -> Ui.toast(this, "Tip: tap a layer, then drag on the canvas"), 900);
        }
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(theme.bg);
        setContentView(root);

        topBar = new TopBar(this, theme);
        topBar.setListener(this);
        root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(56)));

        LinearLayout body = Ui.row(this);
        body.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(body);

        ScrollView railScroll = new ScrollView(this);
        railScroll.setBackgroundColor(theme.panel);
        railScroll.setVerticalScrollBarEnabled(false);
        LinearLayout rail = Ui.column(this);
        rail.setPadding(Ui.dp(1), Ui.dp(2), Ui.dp(1), Ui.dp(2));
        railScroll.addView(rail);
        body.addView(railScroll, new LinearLayout.LayoutParams(Ui.dp(56),
                ViewGroup.LayoutParams.MATCH_PARENT));

        toolSelect = railButton(rail, "select", "Select", () -> setTool(CanvasView.Tool.SELECT));
        toolPose = railButton(rail, "pose", "Pose", () -> setTool(CanvasView.Tool.POSE));
        toolDraw = railButton(rail, "draw", "Draw", () -> setTool(CanvasView.Tool.DRAW));
        toolCamera = railButton(rail, "camera", "Camera", () -> setTool(CanvasView.Tool.CAMERA));
        railDivider(rail);
        railButton(rail, "fit", "Fit", () -> canvas.fit());
        railButton(rail, "grid", "Grid", () -> {
            canvas.setGrid(!canvas.isGrid());
            updateRail();
        });
        railOnion = railButton(rail, "onion", "Onion", () -> {
            onion = !onion;
            app.setOnion(onion);
            canvas.setOnion(onion);
            timeline.setState(project, scene(), selected, playhead, playing, app.autoKey(), onion);
            updateRail();
        });
        railFx = railButton(rail, "effects", "FX", () -> {
            fxEnabled = !fxEnabled;
            canvas.setFxEnabled(fxEnabled);
            updateRail();
        });
        railButton(rail, "sun", "Preview", () -> {
            quality = (quality + 1) % 3;
            app.setPreviewQuality(quality);
            canvas.setQuality(quality);
            Ui.toast(this, quality == 0 ? "Preview: draft" : quality == 1 ? "Preview: good" : "Preview: full");
        });
        railButton(rail, "layers", "Layers", () -> selectTab(1));
        railButton(rail, "shader", "Shaders", () -> selectTab(2));

        LinearLayout stage = Ui.column(this);
        stage.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        body.addView(stage);

        canvas = new CanvasView(this);
        canvas.setListener(this);
        canvas.setAssetsDir(app.store().assetsDir(project.id));
        canvas.setQuality(quality);
        canvas.setOnion(onion);
        stage.addView(canvas, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        tabsRow = Ui.row(this);
        tabsRow.setPadding(Ui.dp(8), Ui.dp(4), Ui.dp(8), Ui.dp(4));
        tabsRow.setBackgroundColor(theme.header);
        stage.addView(tabsRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        panelHost = new LinearLayout(this);
        panelHost.setOrientation(LinearLayout.VERTICAL);
        panelHost.setBackgroundColor(theme.panel);
        int panelHeight = Ui.isLandscape(this) ? Ui.dp(216)
                : Math.min(Ui.dp(310), Math.round(Ui.screenHeight(this) * 0.43f));
        stage.addView(panelHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, panelHeight));

        timeline = new TimelineView(this);
        timeline.setListener(this);
        panelHost.addView(timeline, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        layerInspector = new InspectorPanel(this, theme, this);
        layerInspector.setMode(InspectorPanel.MODE_LAYER);
        panelHost.addView(layerInspector, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        sceneInspector = new InspectorPanel(this, theme, this);
        sceneInspector.setMode(InspectorPanel.MODE_SCENE);
        panelHost.addView(sceneInspector, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        selectTab(0);
    }

    private IconButton railButton(LinearLayout rail, String icon, String label, Runnable action) {
        IconButton button = new IconButton(this, theme, icon, label, IconButton.Style.RAIL);
        button.setOnClickListener(v -> action.run());
        rail.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(56)));
        return button;
    }

    private void railDivider(LinearLayout rail) {
        View divider = new View(this);
        divider.setBackgroundColor(theme.border);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(1));
        lp.setMargins(Ui.dp(9), Ui.dp(4), Ui.dp(9), Ui.dp(4));
        rail.addView(divider, lp);
    }

    private void updateRail() {
        CanvasView.Tool tool = canvas.getTool();
        toolSelect.setActive(tool == CanvasView.Tool.SELECT);
        toolPose.setActive(tool == CanvasView.Tool.POSE);
        toolDraw.setActive(tool == CanvasView.Tool.DRAW);
        toolCamera.setActive(tool == CanvasView.Tool.CAMERA);
        railOnion.setActive(onion);
        railGrid.setActive(canvas.isGrid());
        railFx.setActive(fxEnabled);
    }

    private void setTool(CanvasView.Tool tool) {
        canvas.setTool(tool);
        updateRail();
        Ui.toast(this, tool.label + ": " + tool.hint);
    }

    private void selectTab(int index) {
        tab = index;
        timeline.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        layerInspector.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        sceneInspector.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        if (index == 1) layerInspector.bind();
        if (index == 2) sceneInspector.bind();
        updateTabs();
        updateTopBar();
    }

    private void updateTabs() {
        tabsRow.removeAllViews();
        String[] names = {"Timeline", "Layer", "Scene"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            TextView chip = Ui.chip(this, theme, names[i], tab == i, () -> selectTab(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(6);
            tabsRow.addView(chip, lp);
        }
        tabsRow.addView(Ui.flexibleSpace(this));
        TextView fxChip = Ui.chip(this, theme, fxEnabled ? "FX on" : "FX off", fxEnabled, () -> {
            fxEnabled = !fxEnabled;
            canvas.setFxEnabled(fxEnabled);
            updateTabs();
            updateRail();
        });
        tabsRow.addView(fxChip);
    }

    // --------------------------------------------------------------- state

    private Layer firstSelectable(Scene scene) {
        for (int i = scene.layers.size() - 1; i >= 0; i--) {
            Layer layer = scene.layers.get(i);
            if (layer.type != LayerType.CAMERA) return layer;
        }
        return scene.camera();
    }

    private void refreshAll() {
        if (project.scenes.isEmpty()) project.validate();
        sceneIndex = AnimMath.clamp(sceneIndex, 0, project.scenes.size() - 1);
        Scene scene = scene();
        if (selected != null && scene.layer(selected.id) == null) selected = firstSelectable(scene);
        if (selected == null) selected = firstSelectable(scene);
        playhead = AnimMath.clamp(playhead, 0, scene.duration);

        canvas.setProject(project);
        canvas.setScene(scene);
        canvas.setPlayhead(playhead);
        canvas.setSelected(selected);
        canvas.setPlaying(playing);
        canvas.setOnion(onion);
        canvas.setFxEnabled(fxEnabled);
        canvas.setQuality(quality);
        canvas.setGrid(canvas.isGrid());
        timeline.setState(project, scene, selected, playhead, playing, app.autoKey(), onion);
        layerInspector.bind();
        sceneInspector.bind();
        updateRail();
        updateTabs();
        updateTopBar();
    }

    private void updateTopBar() {
        topBar.setState(project.name, scene().name, AnimMath.timecode(playhead, project.fps),
                playing, !undoStack.isEmpty(), !redoStack.isEmpty(), tab == 1);
    }

    public void beforeEdit() {
        pushUndo();
    }

    public void edited() {
        markDirty();
        canvas.invalidateScene();
        timeline.refresh();
        updateTopBar();
    }

    private void setPlayhead(double time) {
        Scene scene = scene();
        playhead = AnimMath.clamp(time, 0, scene.duration);
        canvas.setPlayhead(playhead);
        timeline.setPlayhead(playhead);
        updateTopBar();
    }

    private void togglePlay() {
        playing = !playing;
        canvas.setPlaying(playing);
        timeline.setState(project, scene(), selected, playhead, playing, app.autoKey(), onion);
        updateTopBar();
        lastFrameNanos = 0;
        if (playing) Choreographer.getInstance().postFrameCallback(frameCallback);
        else Choreographer.getInstance().removeFrameCallback(frameCallback);
    }

    // -------------------------------------------------------------- saving

    private void markDirty() {
        handler.removeCallbacks(saveRunnable);
        handler.postDelayed(saveRunnable, 700);
    }

    private void saveNow(boolean toast) {
        handler.removeCallbacks(saveRunnable);
        try {
            app.store().save(project, thumbnail());
            app.rememberProject(project.id);
            if (toast) Ui.toast(this, "Project saved");
        } catch (Exception e) {
            if (toast) Ui.toast(this, "Could not save: " + e.getMessage());
        }
    }

    private Bitmap thumbnail() {
        try {
            Scene scene = project.scenes.get(0);
            if (thumbRenderer == null) thumbRenderer = new SceneRenderer();
            thumbRenderer.setAssetsDir(app.store().assetsDir(project.id));
            Bitmap bmp = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            canvas.drawColor(Color.BLACK);
            thumbRenderer.draw(canvas, project, scene, 0, 480, 270, 1f, 0, 0, false, 0, 1f);
            ShaderPipeline.apply(bmp, scene.effects, 0);
            return bmp;
        } catch (Throwable e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- undo

    private void pushUndo() {
        String json = project.toJsonString();
        if (!undoStack.isEmpty() && undoStack.peek().equals(json)) return;
        undoStack.push(json);
        while (undoStack.size() > 60) undoStack.removeLast();
        redoStack.clear();
        updateTopBar();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(project.toJsonString());
        restore(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(project.toJsonString());
        restore(redoStack.pop());
    }

    private void restore(String json) {
        try {
            String id = project.id;
            Project restored = Project.fromJsonString(json);
            restored.id = id;
            project = restored;
            sceneIndex = AnimMath.clamp(sceneIndex, 0, project.scenes.size() - 1);
            Scene scene = scene();
            selected = selected == null ? null : scene.layer(selected.id);
            if (selected == null) selected = firstSelectable(scene);
            playhead = AnimMath.clamp(playhead, 0, scene.duration);
            canvas.setAssetsDir(app.store().assetsDir(project.id));
            refreshAll();
            markDirty();
        } catch (Exception e) {
            Ui.toast(this, "Undo failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------- export

    private void showExportMenu() {
        String[] items = {"MP4 video", "PNG sequence (zip)", "Share project file", "Remaster a video"};
        Ui.choose(this, theme, "Export", items, index -> {
            if (index == 0) {
                Dialogs.exportOptions(this, theme, project, this::exportVideo);
            } else if (index == 1) {
                Dialogs.exportOptions(this, theme, project, (scale, fps, quality) ->
                        exportFrames(scale, fps));
            } else if (index == 2) {
                shareProject();
            } else if (index == 3) {
                startActivity(new Intent(this, RemasterActivity.class));
            }
        });
    }

    private int bitrateFor(int width, int height, int quality) {
        int pixels = width * height;
        float factor = quality == 0 ? 2.5f : quality == 1 ? 4.5f : 7f;
        return Math.max(1_500_000, Math.round(pixels * factor * 0.35f));
    }

    private void exportVideo(int scalePercent, int fps, int quality) {
        int width = Math.max(2, (project.width * scalePercent / 100) & ~1);
        int height = Math.max(2, (project.height * scalePercent / 100) & ~1);
        final VideoExporter.Options options = new VideoExporter.Options();
        options.width = width;
        options.height = height;
        options.fps = Math.max(1, fps);
        options.bitrate = bitrateFor(width, height, quality);
        options.effects = true;
        final Project snapshot = Project.fromJsonString(project.toJsonString());
        final File assets = app.store().assetsDir(project.id);
        cancelRequested = false;
        showProgress("Rendering video", "Preparing the scene\u2026");
        new Thread(() -> {
            try {
                File out = VideoExporter.export(this, snapshot, assets, options, new VideoExporter.Progress() {
                    @Override
                    public void onProgress(float fraction, String label) {
                        updateProgress(fraction, label);
                    }

                    @Override
                    public boolean cancelled() {
                        return cancelRequested;
                    }
                });
                Uri uri = MediaOut.publish(this, out, "video/mp4", "Movies/BXAnimator", out.getName());
                runOnUiThread(() -> {
                    hideProgress();
                    Ui.confirm(this, theme, "Video exported",
                            "Saved to Movies/BXAnimator\n" + out.getName() + "\n" + MediaOut.humanSize(out.length()),
                            "Share", () -> Ui.shareFile(this, out, "video/mp4", "Share video"));
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    hideProgress();
                    Ui.alert(this, theme, "Export failed", String.valueOf(e.getMessage()), "OK");
                });
            }
        }, "bx-video-export").start();
    }

    private void exportFrames(int scalePercent, int fps) {
        final Project snapshot = Project.fromJsonString(project.toJsonString());
        final File assets = app.store().assetsDir(project.id);
        final float scale = scalePercent / 100f;
        cancelRequested = false;
        showProgress("Rendering frames", "Preparing the scene\u2026");
        new Thread(() -> {
            try {
                File out = FrameExporter.exportZip(this, snapshot, assets, Math.max(1, fps), scale,
                        new FrameExporter.Progress() {
                            @Override
                            public void onProgress(float fraction, String label) {
                                updateProgress(fraction, label);
                            }

                            @Override
                            public boolean cancelled() {
                                return cancelRequested;
                            }
                        });
                Uri uri = MediaOut.publish(this, out, "application/zip", "Download/BXAnimator", out.getName());
                runOnUiThread(() -> {
                    hideProgress();
                    Ui.confirm(this, theme, "Frames exported",
                            "Saved to Download/BXAnimator\n" + out.getName() + "\n" + MediaOut.humanSize(out.length()),
                            "Share", () -> Ui.shareFile(this, out, "application/zip", "Share frames"));
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    hideProgress();
                    Ui.alert(this, theme, "Export failed", String.valueOf(e.getMessage()), "OK");
                });
            }
        }, "bx-frame-export").start();
    }

    private void shareProject() {
        try {
            saveNow(false);
            File file = app.store().writeShareCopy(project.id);
            Ui.shareFile(this, file, "application/octet-stream", "Share project");
        } catch (Exception e) {
            Ui.toast(this, "Could not share: " + e.getMessage());
        }
    }

    private void showProgress(String title, String initial) {
        hideProgress();
        LinearLayout body = Ui.column(this);
        progressLabel = Ui.label(this, theme, initial, 12.5f, theme.textDim, false);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgress(0);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(theme.accent));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(10));
        barLp.topMargin = Ui.dp(10);
        body.addView(progressLabel);
        body.addView(progressBar, barLp);
        TextView cancel = Ui.button(this, theme, "Cancel", false, () -> {
            cancelRequested = true;
            if (progressLabel != null) progressLabel.setText("Stopping\u2026");
        });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.topMargin = Ui.dp(10);
        LinearLayout row = Ui.row(this);
        row.addView(cancel, cancelLp);
        body.addView(row);
        progressDialog = Ui.panel(this, theme, title, body, null, null);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    private void updateProgress(float fraction, String label) {
        runOnUiThread(() -> {
            if (progressBar != null) progressBar.setProgress(Math.round(Math.max(0f, Math.min(1f, fraction)) * 1000));
            if (progressLabel != null && label != null) progressLabel.setText(label);
        });
    }

    private void hideProgress() {
        if (progressDialog != null) {
            try {
                progressDialog.dismiss();
            } catch (Exception ignored) {
            }
            progressDialog = null;
        }
        progressBar = null;
        progressLabel = null;
    }

    // ---------------------------------------------------------------- menu

    private void showMenu() {
        String[] items = {
                "Project settings", "Studio theme", "Scenes", "Add scene", "Duplicate scene",
                "Delete scene", "Import image", "Shader Lab", "Remaster video", "Share project",
                "How it works", "Back to library"
        };
        Ui.choose(this, theme, "Menu", items, index -> {
            switch (index) {
                case 0:
                    projectSettings();
                    break;
                case 1:
                    Dialogs.theme(this, theme, i -> {
                        app.setThemeId(Theme.Catalog.all().get(i).id);
                        saveNow(false);
                        recreate();
                    });
                    break;
                case 2:
                    showScenePicker();
                    break;
                case 3:
                    addScene();
                    break;
                case 4:
                    duplicateScene();
                    break;
                case 5:
                    deleteScene();
                    break;
                case 6:
                    pendingImageLayer = null;
                    pickImage();
                    break;
                case 7:
                    openShaderLab();
                    break;
                case 8:
                    startActivity(new Intent(this, RemasterActivity.class));
                    break;
                case 9:
                    shareProject();
                    break;
                case 10:
                    Dialogs.help(this, theme);
                    break;
                case 11:
                    saveNow(false);
                    finish();
                    break;
                default:
                    break;
            }
        });
    }

    private void projectSettings() {
        LinearLayout body = Ui.column(this);
        TextView name = Ui.label(this, theme, project.name, 15, theme.text, true);
        body.addView(name);
        LinearLayout rename = Ui.row(this);
        LinearLayout.LayoutParams renameLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        renameLp.topMargin = Ui.dp(8);
        rename.addView(Ui.chip(this, theme, "Rename project", false, () ->
                Ui.prompt(this, theme, "Project name", "Project name", project.name, text -> {
                    if (text == null || text.isEmpty()) return;
                    pushUndo();
                    project.name = text;
                    edited();
                    refreshAll();
                })));
        body.addView(rename, renameLp);

        body.addView(Ui.label(this, theme, "Frame rate", 11, theme.textDim, true));
        LinearLayout fps = Ui.row(this);
        int[] rates = {12, 24, 30};
        for (int rate : rates) {
            TextView chip = Ui.chip(this, theme, rate + " fps", project.fps == rate, () -> {
                pushUndo();
                project.fps = rate;
                edited();
                refreshAll();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(6);
            fps.addView(chip, lp);
        }
        body.addView(fps);

        body.addView(Ui.label(this, theme, "Resolution", 11, theme.textDim, true));
        LinearLayout resolutions = Ui.row(this);
        int[][] sizes = {{1280, 720}, {1920, 1080}, {1080, 1920}};
        String[] labels = {"720p", "1080p", "Vertical"};
        for (int i = 0; i < sizes.length; i++) {
            final int[] size = sizes[i];
            boolean active = project.width == size[0] && project.height == size[1];
            TextView chip = Ui.chip(this, theme, labels[i], active, () -> {
                pushUndo();
                project.width = size[0];
                project.height = size[1];
                project.validate();
                for (Scene scene : project.scenes) {
                    Layer camera = scene.camera();
                    if (camera != null && camera.channels.isEmpty()) {
                        camera.setKey(Layer.X, 0, project.width / 2f);
                        camera.setKey(Layer.Y, 0, project.height / 2f);
                    }
                }
                edited();
                refreshAll();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(6);
            resolutions.addView(chip, lp);
        }
        body.addView(resolutions);

        TextView stats = Ui.label(this, theme,
                project.scenes.size() + " scenes  \u00b7  " + project.totalFrames() + " frames  \u00b7  "
                        + Ui.fixed((float) project.duration(), 1) + " s",
                11, theme.textDim, false);
        stats.setPadding(0, Ui.dp(10), 0, 0);
        body.addView(stats);

        Ui.panel(this, theme, "Project settings", body, "Done", () -> saveNow(false)).show();
    }

    private void showScenePicker() {
        String[] items = new String[project.scenes.size()];
        for (int i = 0; i < project.scenes.size(); i++) {
            Scene s = project.scenes.get(i);
            items[i] = (i + 1) + ".  " + s.name + "  \u00b7  " + Ui.fixed((float) s.duration, 1) + "s";
        }
        Ui.choose(this, theme, "Scenes", items, this::switchScene);
    }

    @Override
    public void switchScene(int index) {
        if (index < 0 || index >= project.scenes.size()) return;
        sceneIndex = index;
        playhead = 0;
        selected = firstSelectable(scene());
        canvas.fit();
        refreshAll();
        markDirty();
    }

    @Override
    public void addScene() {
        pushUndo();
        Scene scene = ProjectFactory.blankScene("Scene " + (project.scenes.size() + 1),
                project.width, project.height);
        project.scenes.add(scene);
        sceneIndex = project.scenes.size() - 1;
        playhead = 0;
        selected = firstSelectable(scene);
        edited();
        refreshAll();
        Ui.toast(this, "Scene added");
    }

    @Override
    public void duplicateScene() {
        pushUndo();
        Scene copy = scene().copy();
        copy.id = Ids.next("scene");
        copy.name = scene().name + " copy";
        copy.cameraId = null;
        for (Layer layer : copy.layers) {
            layer.id = Ids.next(layer.type.name().toLowerCase(java.util.Locale.US));
        }
        copy.ensureCamera(project.width / 2f, project.height / 2f);
        project.scenes.add(sceneIndex + 1, copy);
        sceneIndex++;
        selected = firstSelectable(copy);
        playhead = 0;
        edited();
        refreshAll();
        Ui.toast(this, "Scene duplicated");
    }

    @Override
    public void deleteScene() {
        if (project.scenes.size() <= 1) {
            Ui.toast(this, "A project needs at least one scene");
            return;
        }
        Ui.confirm(this, theme, "Delete scene",
                "Remove \u201c" + scene().name + "\u201d from the project?", "Delete", () -> {
                    pushUndo();
                    project.scenes.remove(sceneIndex);
                    sceneIndex = AnimMath.clamp(sceneIndex, 0, project.scenes.size() - 1);
                    selected = firstSelectable(scene());
                    playhead = 0;
                    edited();
                    refreshAll();
                });
    }

    // --------------------------------------------------------------- layers

    private void addLayer(LayerType type) {
        Scene scene = scene();
        float cx = project.width / 2f;
        float cy = project.height / 2f;
        switch (type) {
            case RIG: {
                Dialogs.rigStyle(this, theme, index -> {
                    String key = RigLibrary.keys()[index];
                    pushUndo();
                    Layer layer = ProjectFactory.rig(RigLibrary.label(key), key, cx, project.height * 0.64f, 1f);
                    scene.add(layer);
                    selected = layer;
                    edited();
                    refreshAll();
                });
                return;
            }
            case SHAPE: {
                pushUndo();
                Layer layer = ProjectFactory.shape("Shape", "rect", cx, cy, 420, 260, 0xFF2C3E50);
                scene.add(layer);
                selected = layer;
                break;
            }
            case TEXT: {
                pushUndo();
                Layer layer = ProjectFactory.text("Text", "TEXT", cx, cy, 96, 0xFFF6EDE2);
                scene.add(layer);
                selected = layer;
                edited();
                refreshAll();
                editText(layer);
                return;
            }
            case DRAW: {
                pushUndo();
                Layer layer = Layer.create(LayerType.DRAW, "Drawing");
                layer.setKey(Layer.X, 0, 0f);
                layer.setKey(Layer.Y, 0, 0f);
                layer.setKey(Layer.SCALE, 0, 1f);
                layer.setKey(Layer.OPACITY, 0, 1f);
                scene.add(layer);
                selected = layer;
                break;
            }
            case IMAGE:
                pendingImageLayer = null;
                pickImage();
                return;
            default:
                return;
        }
        edited();
        refreshAll();
    }

    private void editText(Layer layer) {
        EditText edit = new EditText(this);
        edit.setText(layer.text == null ? "" : layer.text);
        edit.setTextColor(theme.text);
        edit.setTextSize(15);
        edit.setHint("Text");
        edit.setHintTextColor(Ui.alpha(theme.textDim, 0.7f));
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        edit.setMinLines(3);
        edit.setMaxLines(7);
        edit.setBackground(Ui.rounded(theme.panelAlt, 10, theme.border, 1));
        edit.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        LinearLayout body = Ui.column(this);
        body.addView(edit);
        Ui.panel(this, theme, "Text layer", body, "Apply", () -> {
            String value = edit.getText().toString();
            if (value.equals(layer.text)) return;
            pushUndo();
            layer.text = value;
            edited();
            refreshAll();
        }).show();
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQ_IMAGE);
        } catch (Exception e) {
            Ui.toast(this, "No image picker available");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            File dir = app.store().assetsDir(project.id);
            String mime = getContentResolver().getType(uri);
            String ext = ".png";
            if (mime != null && mime.contains("jpeg")) ext = ".jpg";
            else if (mime != null && mime.contains("webp")) ext = ".webp";
            String name = "image-" + System.currentTimeMillis() + ext;
            File file = MediaOut.copyTo(this, uri, dir, name);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            float w = options.outWidth > 0 ? options.outWidth : 800;
            float h = options.outHeight > 0 ? options.outHeight : 600;
            float scale = Math.min(1f, 1800f / Math.max(w, h));
            w *= scale;
            h *= scale;

            pushUndo();
            Layer layer = pendingImageLayer;
            if (layer == null || scene().layer(layer.id) == null) {
                layer = Layer.create(LayerType.IMAGE, "Image");
                layer.setKey(Layer.X, 0, project.width / 2f);
                layer.setKey(Layer.Y, 0, project.height / 2f);
                layer.setKey(Layer.OPACITY, 0, 1f);
                scene().add(layer);
            }
            layer.asset = file.getName();
            layer.shapeWidth = w;
            layer.shapeHeight = h;
            selected = layer;
            pendingImageLayer = null;
            edited();
            refreshAll();
            Ui.toast(this, "Image imported");
        } catch (Exception e) {
            Ui.toast(this, "Could not import image: " + e.getMessage());
        }
    }

    // ------------------------------------------------------ canvas listener

    @Override
    public void onSelect(Layer layer) {
        selected = layer;
        canvas.setSelected(layer);
        timeline.setState(project, scene(), selected, playhead, playing, app.autoKey(), onion);
        if (tab == 1) layerInspector.bind();
        updateTopBar();
    }

    @Override
    public void onBeforeEdit() {
        pushUndo();
    }

    @Override
    public void onEdited() {
        edited();
    }

    @Override
    public void onMessage(String message) {
        Ui.toast(this, message);
    }

    @Override
    public void onRequestDrawingLayer() {
        addLayer(LayerType.DRAW);
    }

    @Override
    public void onBoneSelected(Bone bone) {
        if (tab == 1) layerInspector.bind();
    }

    // ---------------------------------------------------- timeline listener

    @Override
    public void onSelectLayer(Layer layer) {
        selected = layer;
        canvas.setSelected(layer);
        if (tab == 1) layerInspector.bind();
        updateTopBar();
    }

    @Override
    public void onKeySelected(Layer layer, String channel, double time) {
        if (layer != null) {
            selected = layer;
            canvas.setSelected(layer);
        }
        if (time >= 0) setPlayhead(time);
        if (tab == 1) layerInspector.bind();
    }

    @Override
    public void onTogglePlay() {
        togglePlay();
    }

    @Override
    public void onAddKey() {
        if (selected == null) return;
        pushUndo();
        String channel = timeline.getSelectedChannel();
        if (channel != null) {
            selected.setKey(channel, playhead,
                    selected.sample(channel, playhead, Edits.defaultValue(selected, channel)), Easing.LINEAR);
        } else {
            boolean any = false;
            for (String ch : new ArrayList<>(selected.channels.keySet())) {
                selected.setKey(ch, playhead, selected.sample(ch, playhead,
                        Edits.defaultValue(selected, ch)), Easing.LINEAR);
                any = true;
            }
            if (!any) {
                for (String ch : new String[]{Layer.X, Layer.Y, Layer.SCALE, Layer.ROT, Layer.OPACITY}) {
                    selected.setKey(ch, playhead, selected.sample(ch, playhead,
                            Edits.defaultValue(selected, ch)), Easing.LINEAR);
                }
            }
        }
        edited();
        timeline.refresh();
        if (tab == 1) layerInspector.bind();
        Ui.toast(this, "Key added at " + AnimMath.timecode(playhead, project.fps));
    }

    @Override
    public void onDeleteKey() {
        if (selected == null) return;
        double time = timeline.getSelectedKeyTime();
        if (time < 0) {
            Ui.toast(this, "Select a key on the timeline first");
            return;
        }
        String channel = timeline.getSelectedChannel();
        pushUndo();
        int removed = 0;
        if (channel != null) {
            Track track = selected.channels.get(channel);
            if (track != null && track.removeAt(time)) removed++;
        } else {
            for (Track track : selected.channels.values()) {
                if (track.removeAt(time)) removed++;
            }
        }
        edited();
        timeline.setSelectedKey(selected, null, -1);
        timeline.refresh();
        if (tab == 1) layerInspector.bind();
        Ui.toast(this, removed == 0 ? "No key at that time" : removed + " key removed");
    }

    @Override
    public void onEasing() {
        if (selected == null) return;
        double time = timeline.getSelectedKeyTime();
        String channel = timeline.getSelectedChannel();
        Easing current = Easing.LINEAR;
        if (time >= 0) {
            if (channel != null) {
                Track track = selected.channels.get(channel);
                Key key = track == null ? null : track.keyAt(time);
                if (key != null) current = key.ease;
            } else {
                for (Track track : selected.channels.values()) {
                    Key key = track.keyAt(time);
                    if (key != null) {
                        current = key.ease;
                        break;
                    }
                }
            }
        }
        Dialogs.easing(this, theme, current, index -> {
            pushUndo();
            timeline.setSelectedEasing(Easing.values()[index]);
            edited();
            if (tab == 1) layerInspector.bind();
        });
    }

    @Override
    public void onToggleAutoKey() {
        app.setAutoKey(!app.autoKey());
        timeline.setState(project, scene(), selected, playhead, playing, app.autoKey(), onion);
        Ui.toast(this, app.autoKey() ? "Auto key on" : "Auto key off");
    }

    @Override
    public void onToggleOnion() {
        onion = !onion;
        app.setOnion(onion);
        canvas.setOnion(onion);
        timeline.setState(project, scene(), selected, playhead, playing, app.autoKey(), onion);
        updateRail();
    }

    @Override
    public void onScrub(double time) {
        setPlayhead(time);
    }

    // -------------------------------------------------------- topbar listener

    @Override
    public void onBack() {
        saveNow(false);
        finish();
    }

    @Override
    public void onUndo() {
        undo();
    }

    @Override
    public void onRedo() {
        redo();
    }

    @Override
    public void onPlay() {
        togglePlay();
    }

    @Override
    public void onExport() {
        showExportMenu();
    }

    @Override
    public void onMenu() {
        showMenu();
    }

    @Override
    public void onScene() {
        showScenePicker();
    }

    @Override
    public void onInspector() {
        selectTab(tab == 1 ? 0 : 1);
    }

    // ----------------------------------------------------- inspector host

    @Override
    public Project project() {
        return project;
    }

    @Override
    public Scene scene() {
        return project.scene(sceneIndex);
    }

    @Override
    public Layer selected() {
        return selected;
    }

    @Override
    public double playhead() {
        return playhead;
    }

    @Override
    public boolean autoKey() {
        return app.autoKey();
    }

    @Override
    public void requestRedraw() {
        canvas.invalidateScene();
    }

    @Override
    public void selectLayer(Layer layer) {
        selected = layer;
        canvas.setSelected(layer);
        timeline.setState(project, scene(), selected, playhead, playing, app.autoKey(), onion);
        updateTopBar();
    }

    @Override
    public void selectBone(Bone bone) {
        // The pose tool owns bone selection; the inspector keeps the layer list.
    }

    @Override
    public void requestText(Layer layer) {
        editText(layer);
    }

    @Override
    public void requestImage(Layer layer) {
        pendingImageLayer = layer;
        pickImage();
    }

    @Override
    public void requestAddLayer(LayerType type) {
        addLayer(type);
    }

    @Override
    public void openShaderLab() {
        saveNow(false);
        Intent intent = new Intent(this, ShaderLabActivity.class);
        intent.putExtra("project_id", project.id);
        intent.putExtra("scene_index", sceneIndex);
        startActivity(intent);
    }

    @Override
    public void addEffect(EffectType type) {
        pushUndo();
        scene().effects.add(Effect.of(type, 0.6f));
        edited();
        sceneInspector.bind();
        Ui.toast(this, type.label + " added");
    }

    @Override
    public void addPreset(String presetId) {
        pushUndo();
        scene().effects.clear();
        scene().effects.addAll(EffectPresets.build(presetId));
        edited();
        sceneInspector.bind();
        Ui.toast(this, EffectPresets.byId(presetId).label + " applied");
    }

    @Override
    public void message(String message) {
        Ui.toast(this, message);
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onPause() {
        playing = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        saveNow(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(saveRunnable);
        hideProgress();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        saveNow(false);
        super.onBackPressed();
    }
}
