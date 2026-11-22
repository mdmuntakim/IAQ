package app.bxanimator;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import app.bxanimator.core.EffectPresets;
import app.bxanimator.core.Theme;
import app.bxanimator.export.ExportCancelledException;
import app.bxanimator.io.MediaOut;
import app.bxanimator.remaster.VideoRemaster;
import app.bxanimator.ui.IconButton;
import app.bxanimator.ui.Ui;

import java.io.File;
import java.util.List;

/** Pick an old clip, choose a look and rebuild it at a modern resolution. */
public final class RemasterActivity extends Activity {

    private static final int REQ_VIDEO = 3001;

    private BxApp app;
    private Theme theme;

    private Uri videoUri;
    private String videoName = "No video selected";
    private VideoRemaster.Info info;
    private int presetIndex;
    private int outputHeight = 720;
    private int outputFps = 24;
    private float lengthSeconds = 30f;

    private TextView sourceName;
    private TextView sourceMeta;
    private TextView startButton;
    private LinearLayout presetRow;
    private LinearLayout resolutionRow;
    private LinearLayout fpsRow;
    private LinearLayout lengthRow;
    private LinearLayout progressBox;
    private ProgressBar progressBar;
    private TextView progressLabel;
    private LinearLayout resultBox;
    private TextView resultText;

    private volatile boolean running;
    private volatile boolean cancelRequested;
    private File resultFile;
    private Uri publishedUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = BxApp.get(this);
        Ui.init(this);
        theme = app.theme();
        BxApp.styleWindow(this, theme);
        buildUi();
        updateStartState();
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(theme.bg);
        setContentView(root);

        LinearLayout header = Ui.row(this);
        header.setPadding(Ui.dp(6), Ui.dp(8), Ui.dp(14), Ui.dp(8));
        header.setBackgroundColor(theme.header);
        IconButton back = new IconButton(this, theme, "back", null, IconButton.Style.BAR);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)));
        LinearLayout titles = Ui.column(this);
        titles.setPadding(Ui.dp(6), 0, 0, 0);
        titles.addView(Ui.label(this, theme, "Video Remaster", 16, theme.text, true));
        titles.addView(Ui.label(this, theme, "Old footage, modern finish", 10.5f, theme.textDim, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = Ui.column(this);
        content.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Source
        LinearLayout source = card(content, "SOURCE");
        sourceName = Ui.label(this, theme, videoName, 13.5f, theme.text, true);
        sourceName.setSingleLine(true);
        source.addView(sourceName);
        sourceMeta = Ui.label(this, theme, "Choose a clip to remaster.", 11.5f, theme.textDim, false);
        sourceMeta.setLineSpacing(Ui.dp(2), 1f);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = Ui.dp(4);
        source.addView(sourceMeta, metaLp);
        LinearLayout choose = Ui.row(this);
        LinearLayout.LayoutParams chooseLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        chooseLp.topMargin = Ui.dp(10);
        choose.addView(Ui.button(this, theme, "Choose video", false, this::pickVideo));
        source.addView(choose, chooseLp);

        // Look
        LinearLayout look = card(content, "LOOK");
        HorizontalScrollView presetScroll = new HorizontalScrollView(this);
        presetScroll.setHorizontalScrollBarEnabled(false);
        presetRow = Ui.row(this);
        presetScroll.addView(presetRow);
        look.addView(presetScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        rebuildPresets();

        // Output
        LinearLayout output = card(content, "OUTPUT");
        output.addView(Ui.label(this, theme, "Resolution", 11, theme.textDim, true));
        resolutionRow = Ui.row(this);
        output.addView(resolutionRow);
        output.addView(Ui.label(this, theme, "Frame rate", 11, theme.textDim, true));
        fpsRow = Ui.row(this);
        output.addView(fpsRow);
        output.addView(Ui.label(this, theme, "Length", 11, theme.textDim, true));
        lengthRow = Ui.row(this);
        output.addView(lengthRow);
        rebuildOutput();

        startButton = Ui.button(this, theme, "Remaster video", true, this::startRemaster);
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        startLp.topMargin = Ui.dp(4);
        content.addView(startButton, startLp);

        // Progress
        progressBox = Ui.column(this);
        progressBox.setVisibility(View.GONE);
        progressBox.setBackground(Ui.rounded(theme.panel, 12, theme.border, 1));
        progressBox.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        progressLabel = Ui.label(this, theme, "Preparing\u2026", 12, theme.textDim, false);
        progressBox.addView(progressLabel);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(theme.accent));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(10));
        barLp.topMargin = Ui.dp(8);
        progressBox.addView(progressBar, barLp);
        LinearLayout cancelRow = Ui.row(this);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.topMargin = Ui.dp(8);
        cancelRow.addView(Ui.chip(this, theme, "Cancel", false, () -> {
            cancelRequested = true;
            if (progressLabel != null) progressLabel.setText("Stopping\u2026");
        }), cancelLp);
        progressBox.addView(cancelRow);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        progressLp.topMargin = Ui.dp(10);
        content.addView(progressBox, progressLp);

        // Result
        resultBox = Ui.column(this);
        resultBox.setVisibility(View.GONE);
        resultBox.setBackground(Ui.rounded(theme.panel, 12, theme.success, 1));
        resultBox.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        resultBox.addView(Ui.label(this, theme, "REMASTERED", 10, theme.success, true));
        resultText = Ui.label(this, theme, "", 12, theme.text, false);
        resultText.setLineSpacing(Ui.dp(3), 1f);
        LinearLayout.LayoutParams resultTextLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        resultTextLp.topMargin = Ui.dp(4);
        resultBox.addView(resultText, resultTextLp);
        LinearLayout resultActions = Ui.row(this);
        LinearLayout.LayoutParams resultActionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        resultActionsLp.topMargin = Ui.dp(10);
        resultActions.addView(Ui.chip(this, theme, "Share", false, () -> {
            if (resultFile != null) Ui.shareFile(this, resultFile, "video/mp4", "Share remastered video");
        }));
        resultActions.addView(Ui.space(this, 8, 1));
        resultActions.addView(Ui.chip(this, theme, "Play", false, this::playResult));
        resultBox.addView(resultActions, resultActionsLp);
        LinearLayout.LayoutParams resultLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        resultLp.topMargin = Ui.dp(10);
        content.addView(resultBox, resultLp);

        TextView note = Ui.label(this, theme,
                "The clip is decoded frame by frame, rebuilt at the selected resolution and run through the shader stack. "
                        + "A long clip takes a while; the result lands in Movies/BXAnimator.",
                10.5f, theme.textDim, false);
        note.setLineSpacing(Ui.dp(3), 1f);
        note.setPadding(0, Ui.dp(14), 0, 0);
        content.addView(note);
    }

    private LinearLayout card(LinearLayout parent, String title) {
        LinearLayout box = Ui.column(this);
        box.setBackground(Ui.rounded(theme.panel, 12, theme.border, 1));
        box.setPadding(Ui.dp(14), Ui.dp(11), Ui.dp(14), Ui.dp(12));
        TextView heading = Ui.label(this, theme, title, 10, theme.accent, true);
        heading.setPadding(0, 0, 0, Ui.dp(6));
        box.addView(heading);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(10);
        parent.addView(box, lp);
        return box;
    }

    private void rebuildPresets() {
        if (presetRow == null) return;
        presetRow.removeAllViews();
        List<String> ids = VideoRemaster.presetIds();
        for (int i = 0; i < ids.size(); i++) {
            final int index = i;
            EffectPresets.Preset preset = EffectPresets.byId(ids.get(i));
            TextView chip = Ui.chip(this, theme, preset.label, index == presetIndex, () -> {
                presetIndex = index;
                rebuildPresets();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(5);
            presetRow.addView(chip, lp);
        }
    }

    private void rebuildOutput() {
        if (resolutionRow == null) return;
        resolutionRow.removeAllViews();
        int[] heights = {720, 1080};
        for (int height : heights) {
            TextView chip = Ui.chip(this, theme, height + "p", outputHeight == height, () -> {
                outputHeight = height;
                rebuildOutput();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(5);
            resolutionRow.addView(chip, lp);
        }
        fpsRow.removeAllViews();
        int[] rates = {24, 30};
        for (int rate : rates) {
            TextView chip = Ui.chip(this, theme, rate + " fps", outputFps == rate, () -> {
                outputFps = rate;
                rebuildOutput();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(5);
            fpsRow.addView(chip, lp);
        }
        lengthRow.removeAllViews();
        String[] labels = {"15 s", "30 s", "60 s", "Full"};
        float[] values = {15f, 30f, 60f, 0f};
        for (int i = 0; i < labels.length; i++) {
            final float seconds = values[i];
            boolean active = Math.abs(lengthSeconds - seconds) < 0.01f;
            TextView chip = Ui.chip(this, theme, labels[i], active, () -> {
                lengthSeconds = seconds;
                rebuildOutput();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(5);
            lengthRow.addView(chip, lp);
        }
    }

    private void updateStartState() {
        if (startButton == null) return;
        boolean enabled = videoUri != null && !running;
        startButton.setEnabled(enabled);
        startButton.setAlpha(enabled ? 1f : 0.45f);
        startButton.setText(running ? "Rendering\u2026" : "Remaster video");
    }

    // --------------------------------------------------------------- actions

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        try {
            startActivityForResult(intent, REQ_VIDEO);
        } catch (Exception e) {
            Ui.toast(this, "No video picker available");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_VIDEO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        videoUri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(videoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        videoName = queryName(videoUri);
        sourceName.setText(videoName);
        sourceMeta.setText("Reading the clip\u2026");
        resultBox.setVisibility(View.GONE);
        updateStartState();
        info = VideoRemaster.probe(this, videoUri);
        if (info.width <= 0) {
            sourceMeta.setText("This file has no readable video track.");
            videoUri = null;
        } else {
            sourceMeta.setText(info.width + " \u00d7 " + info.height + "  \u00b7  "
                    + Ui.fixed(info.durationMs / 1000f, 1) + " s  \u00b7  "
                    + (info.rotation == 0 ? "no rotation" : info.rotation + "\u00b0 rotation"));
        }
        updateStartState();
    }

    private void startRemaster() {
        if (videoUri == null || running) return;
        running = true;
        cancelRequested = false;
        resultFile = null;
        publishedUri = null;
        resultBox.setVisibility(View.GONE);
        progressBox.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        progressLabel.setText("Preparing\u2026");
        updateStartState();

        final VideoRemaster.Options options = new VideoRemaster.Options();
        options.height = outputHeight;
        options.fps = outputFps;
        options.seconds = lengthSeconds;
        options.effects = EffectPresets.build(VideoRemaster.presetIds().get(presetIndex));
        final Uri source = videoUri;

        new Thread(() -> {
            try {
                File out = VideoRemaster.remaster(this, source, options, new VideoRemaster.Progress() {
                    @Override
                    public void onProgress(float fraction, String label) {
                        runOnUiThread(() -> {
                            progressBar.setProgress(Math.round(Math.max(0f, Math.min(1f, fraction)) * 1000));
                            if (label != null) progressLabel.setText(label);
                        });
                    }

                    @Override
                    public boolean cancelled() {
                        return cancelRequested;
                    }
                });
                publishedUri = MediaOut.publish(this, out, "video/mp4", "Movies/BXAnimator", out.getName());
                runOnUiThread(() -> finishRemaster(out, null));
            } catch (Throwable e) {
                runOnUiThread(() -> finishRemaster(null, e));
            }
        }, "bx-remaster").start();
    }

    private void finishRemaster(File out, Throwable error) {
        running = false;
        progressBox.setVisibility(View.GONE);
        updateStartState();
        if (error != null) {
            if (error instanceof ExportCancelledException) {
                Ui.toast(this, "Remaster cancelled");
            } else {
                Ui.alert(this, theme, "Remaster failed", String.valueOf(error.getMessage()), "OK");
            }
            return;
        }
        if (out == null) return;
        resultFile = out;
        resultBox.setVisibility(View.VISIBLE);
        resultText.setText(out.getName() + "\n" + MediaOut.humanSize(out.length())
                + "\nSaved to Movies/BXAnimator");
        Ui.toast(this, "Remaster finished");
    }

    private void playResult() {
        if (publishedUri != null) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(publishedUri, "video/mp4");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
        }
        if (resultFile != null) Ui.shareFile(this, resultFile, "video/mp4", "Open remastered video");
    }

    private String queryName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {
        }
        return "Selected video";
    }

    @Override
    protected void onDestroy() {
        cancelRequested = true;
        super.onDestroy();
    }
}
