package app.bxanimator.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.bxanimator.core.Easing;
import app.bxanimator.core.EffectPresets;
import app.bxanimator.core.LayerType;
import app.bxanimator.core.Project;
import app.bxanimator.core.RigLibrary;
import app.bxanimator.core.Theme;

/** Shared dialogs: colours, themes, easing, export settings and help. */
public final class Dialogs {

    public interface IntCallback {
        void run(int value);
    }

    public interface ExportCallback {
        void run(int scalePercent, int fps, int quality);
    }

    /** A hand picked palette that reads well on every theme. */
    private static final int[] PALETTE = {
            0xFFF6EDE2, 0xFFD9D1C0, 0xFF9AA0A8, 0xFF4A5568, 0xFF1F2933, 0xFF0B0E14,
            0xFFFF4D6D, 0xFFFF7A18, 0xFFFFB347, 0xFFFFD166, 0xFFFFE0A3, 0xFFF2994A,
            0xFF3DDC97, 0xFF2F7A57, 0xFF22D3EE, 0xFF38BDF8, 0xFF3E6FA8, 0xFF6EA8FE,
            0xFFA855F7, 0xFFFF7EB6, 0xFFFF9EC4, 0xFF5C2B46, 0xFF7A4E63, 0xFF24365C
    };

    private Dialogs() {
    }

    // -------------------------------------------------------------- colours

    public static void color(Context context, Theme theme, String title, int initial, IntCallback onPick) {
        final int[] selected = {initial};
        LinearLayout body = Ui.column(context);

        TextView preview = Ui.label(context, theme, hex(initial), 13, theme.text, true);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        preview.setBackground(Ui.rounded(initial, 10, theme.border, 1));
        body.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(46)));

        final EditText hexEdit = new EditText(context);
        hexEdit.setText(hex(initial));
        hexEdit.setSingleLine(true);
        hexEdit.setTextColor(theme.text);
        hexEdit.setTextSize(14);
        hexEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        hexEdit.setBackground(Ui.rounded(theme.panelAlt, 9, theme.border, 1));
        hexEdit.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8));
        LinearLayout.LayoutParams hexLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hexLp.topMargin = Ui.dp(8);
        body.addView(hexEdit, hexLp);

        LinearLayout grid = Ui.column(context);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gridLp.topMargin = Ui.dp(10);
        body.addView(grid, gridLp);

        for (int row = 0; row < (PALETTE.length + 5) / 6; row++) {
            LinearLayout line = Ui.row(context);
            for (int col = 0; col < 6; col++) {
                int index = row * 6 + col;
                if (index >= PALETTE.length) {
                    line.addView(Ui.space(context, 1, 1), new LinearLayout.LayoutParams(0, Ui.dp(40), 1f));
                    continue;
                }
                final int value = PALETTE[index];
                View swatch = new View(context);
                swatch.setBackground(swatchDrawable(value, theme, value == selected[0]));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(40), 1f);
                lp.setMargins(Ui.dp(3), Ui.dp(3), Ui.dp(3), Ui.dp(3));
                line.addView(swatch, lp);
                swatch.setOnClickListener(v -> {
                    selected[0] = value;
                    preview.setBackground(Ui.rounded(value, 10, theme.border, 1));
                    preview.setText(hex(value));
                    hexEdit.setText(hex(value));
                    for (int r = 0; r < grid.getChildCount(); r++) {
                        LinearLayout child = (LinearLayout) grid.getChildAt(r);
                        for (int c = 0; c < child.getChildCount(); c++) {
                            View cell = child.getChildAt(c);
                            if (cell.getTag() instanceof Integer) {
                                int cellValue = (Integer) cell.getTag();
                                cell.setBackground(swatchDrawable(cellValue, theme, cellValue == selected[0]));
                            }
                        }
                    }
                });
                swatch.setTag(value);
                line.addView(swatch, lp);
            }
            grid.addView(line);
        }

        Dialog dialog = Ui.panel(context, theme, title, body, "Use colour", () -> {
            int value = parseColor(hexEdit.getText().toString(), selected[0]);
            selected[0] = value;
            if (onPick != null) onPick.run(value);
        });
        dialog.show();
    }

    private static GradientDrawable swatchDrawable(int color, Theme theme, boolean selected) {
        GradientDrawable d = Ui.rounded(color, 9, selected ? theme.text : theme.border, selected ? 2.5f : 1f);
        return d;
    }

    private static String hex(int color) {
        return String.format(java.util.Locale.US, "#%08X", color);
    }

    public static int parseColor(String text, int fallback) {
        if (text == null) return fallback;
        String clean = text.trim();
        if (clean.isEmpty()) return fallback;
        if (!clean.startsWith("#")) clean = "#" + clean;
        try {
            if (clean.length() == 7) return 0xFF000000 | Color.parseColor(clean);
            if (clean.length() == 9) {
                long value = Long.parseLong(clean.substring(1), 16);
                return (int) value;
            }
            return Color.parseColor(clean);
        } catch (Exception e) {
            return fallback;
        }
    }

    // --------------------------------------------------------------- themes

    public static void theme(Context context, Theme current, IntCallback onPick) {
        LinearLayout body = Ui.column(context);
        java.util.List<Theme> themes = Theme.Catalog.all();
        for (int i = 0; i < themes.size(); i++) {
            final int index = i;
            Theme candidate = themes.get(i);
            LinearLayout row = Ui.row(context);
            row.setPadding(Ui.dp(8), Ui.dp(9), Ui.dp(8), Ui.dp(9));
            row.setBackground(Ui.rounded(candidate.panel, 10, candidate.id.equals(current.id) ? current.accent : candidate.border,
                    candidate.id.equals(current.id) ? 2 : 1));
            LinearLayout swatches = Ui.row(context);
            for (int color : new int[]{candidate.bg, candidate.panel, candidate.accent, candidate.text, candidate.key}) {
                View chip = new View(context);
                chip.setBackground(Ui.rounded(color, 5, candidate.border, 1));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(18), Ui.dp(18));
                lp.rightMargin = Ui.dp(4);
                swatches.addView(chip, lp);
            }
            row.addView(swatches);
            TextView label = Ui.label(context, current, candidate.label, 13.5f, current.text, true);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelLp.leftMargin = Ui.dp(10);
            row.addView(label, labelLp);
            if (candidate.id.equals(current.id)) {
                TextView check = Ui.label(context, current, "\u2713", 15, current.accent, true);
                check.setPadding(Ui.dp(8), 0, Ui.dp(2), 0);
                row.addView(check);
            }
            row.setClickable(true);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = Ui.dp(6);
            body.addView(row, rowLp);
            row.setOnClickListener(v -> {
                Dialog d = (Dialog) row.getTag();
                if (d != null) d.dismiss();
                if (onPick != null) onPick.run(index);
            });
        }
        Dialog dialog = Ui.panel(context, current, "Studio theme", body, null, null);
        for (int i = 0; i < body.getChildCount(); i++) body.getChildAt(i).setTag(dialog);
        dialog.show();
    }

    // --------------------------------------------------------------- easing

    public static void easing(Context context, Theme theme, Easing current, IntCallback onPick) {
        Easing[] all = Easing.values();
        String[] labels = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            labels[i] = all[i].label + (all[i] == current ? "  \u2022 current" : "");
        }
        Ui.choose(context, theme, "Interpolation", labels, index -> {
            if (onPick != null) onPick.run(index);
        });
    }

    // --------------------------------------------------------------- export

    public static void exportOptions(Context context, Theme theme, Project project, ExportCallback onStart) {
        LinearLayout body = Ui.column(context);
        final int[] state = {100, project.fps, 1};

        body.addView(sectionLabel(context, theme, "Resolution"));
        LinearLayout resolution = Ui.row(context);
        addChoiceRow(context, theme, resolution, new String[]{"50%", "75%", "100%"}, new int[]{50, 75, 100},
                2, value -> state[0] = value);
        body.addView(resolution);

        body.addView(sectionLabel(context, theme, "Frame rate"));
        LinearLayout fps = Ui.row(context);
        String projectLabel = project.fps + " fps";
        addChoiceRow(context, theme, fps, new String[]{"24 fps", "30 fps", projectLabel},
                new int[]{24, 30, project.fps}, 2, value -> state[1] = value);
        body.addView(fps);

        body.addView(sectionLabel(context, theme, "Quality"));
        LinearLayout quality = Ui.row(context);
        addChoiceRow(context, theme, quality, new String[]{"Draft", "Good", "High"},
                new int[]{0, 1, 2}, 1, value -> state[2] = value);
        body.addView(quality);

        TextView note = Ui.label(context, theme,
                "The video is rendered with the same engine as the preview, shader stack included.",
                11.5f, theme.textDim, false);
        note.setLineSpacing(Ui.dp(3), 1f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = Ui.dp(10);
        body.addView(note, noteLp);

        Dialog dialog = Ui.panel(context, theme, "Export video", body, "Start export", () -> {
            int fpsValue = state[1];
            if (onStart != null) onStart.run(state[0], fpsValue, state[2]);
        });
        dialog.show();
    }

    private static TextView sectionLabel(Context context, Theme theme, String text) {
        TextView tv = Ui.label(context, theme, text, 11, theme.textDim, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(10);
        lp.bottomMargin = Ui.dp(6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static void addChoiceRow(Context context, Theme theme, LinearLayout row, String[] labels,
                                     int[] values, int initial, IntCallback onPick) {
        final int[] selected = {initial};
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            row.removeAllViews();
            for (int i = 0; i < labels.length; i++) {
                final int index = i;
                TextView chip = Ui.chip(context, theme, labels[i], i == selected[0], () -> {
                    selected[0] = index;
                    render[0].run();
                    onPick.run(values[index]);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = Ui.dp(6);
                row.addView(chip, lp);
            }
        };
        render[0].run();
    }

    // ------------------------------------------------------------ templates

    public static void template(Context context, Theme theme, IntCallback onPick) {
        java.util.List<app.bxanimator.core.ProjectFactory.Template> templates =
                app.bxanimator.core.ProjectFactory.templates();
        String[] labels = new String[templates.size()];
        for (int i = 0; i < templates.size(); i++) {
            labels[i] = templates.get(i).label + "  \u2014  " + templates.get(i).description;
        }
        Ui.choose(context, theme, "New project", labels, index -> {
            if (onPick != null) onPick.run(index);
        });
    }

    public static void rigStyle(Context context, Theme theme, IntCallback onPick) {
        String[] keys = RigLibrary.keys();
        String[] labels = new String[keys.length];
        for (int i = 0; i < keys.length; i++) labels[i] = RigLibrary.label(keys[i]);
        Ui.choose(context, theme, "Character style", labels, index -> {
            if (onPick != null) onPick.run(index);
        });
    }

    public static void addLayer(Context context, Theme theme, IntCallback onPick) {
        String[] labels = {
                "Character \u2014 posable anime puppet",
                "Shape \u2014 rectangle, ellipse, star, burst",
                "Text \u2014 title, subtitle, dialogue",
                "Drawing \u2014 freehand frame by frame layer",
                "Image \u2014 imported artwork or background"
        };
        Ui.choose(context, theme, "Add layer", labels, index -> {
            if (onPick != null) onPick.run(index);
        });
    }

    public static void effect(Context context, Theme theme, IntCallback onPick) {
        app.bxanimator.core.EffectType[] all = app.bxanimator.core.EffectType.values();
        String[] labels = new String[all.length];
        for (int i = 0; i < all.length; i++) labels[i] = all[i].label + "  \u2014  " + all[i].hint;
        Ui.choose(context, theme, "Add effect", labels, index -> {
            if (onPick != null) onPick.run(index);
        });
    }

    public static void preset(Context context, Theme theme, IntCallback onPick) {
        java.util.List<EffectPresets.Preset> presets = EffectPresets.all();
        String[] labels = new String[presets.size()];
        for (int i = 0; i < presets.size(); i++) {
            labels[i] = presets.get(i).label + "  \u2014  " + presets.get(i).description;
        }
        Ui.choose(context, theme, "Shader presets", labels, index -> {
            if (onPick != null) onPick.run(index);
        });
    }

    // ----------------------------------------------------------------- help

    public static void help(Context context, Theme theme) {
        LinearLayout body = Ui.column(context);
        addHelp(body, context, theme, "The tools",
                "Select moves, scales and rotates a layer. Pose bends the selected character; every drag writes a key at the playhead. "
                        + "Draw paints on a drawing layer. Camera flies the shot: drag to pan, pinch to zoom, twist to roll.");
        addHelp(body, context, theme, "The timeline",
                "Tap a layer row to select it, tap the arrow to open its channels, drag a diamond to retime a key. "
                        + "The toolbar holds play, add key, delete key, interpolation, auto key and onion skin.");
        addHelp(body, context, theme, "Shaders",
                "The Scene tab carries a live effect stack. Add individual effects or a curated preset, tune every parameter "
                        + "and open the Shader Lab to preview the GLSL version before applying it.");
        addHelp(body, context, theme, "Remaster",
                "Remaster a video takes an old clip, rebuilds it frame by frame at a modern resolution and runs the Modern Remaster stack "
                        + "on every frame. The result lands in Movies/BXAnimator.");
        addHelp(body, context, theme, "Export",
                "Export video renders an MP4 with the same engine as the preview. PNG sequence writes a zip of numbered frames. "
                        + "Share project sends the editable .bxanim file.");
        Ui.panel(context, theme, "How BXAnimator works", body, "Got it", null).show();
    }

    private static void addHelp(LinearLayout body, Context context, Theme theme, String title, String text) {
        TextView heading = Ui.label(context, theme, title, 13, theme.accent, true);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        headingLp.topMargin = Ui.dp(10);
        body.addView(heading, headingLp);
        TextView paragraph = Ui.label(context, theme, text, 12.5f, theme.textDim, false);
        paragraph.setLineSpacing(Ui.dp(4), 1f);
        body.addView(paragraph);
    }

    public static String layerTypeLabel(LayerType type) {
        return type == null ? "Layer" : type.label;
    }
}
