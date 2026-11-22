package app.bxanimator;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.bxanimator.core.Project;
import app.bxanimator.core.ProjectFactory;
import app.bxanimator.core.Theme;
import app.bxanimator.io.ProjectStore;
import app.bxanimator.ui.Dialogs;
import app.bxanimator.ui.IconButton;
import app.bxanimator.ui.Ui;

import java.io.InputStream;
import java.util.List;

/** The studio dashboard: project library, templates, themes and tools. */
public final class MainActivity extends Activity {

    private static final int REQ_IMPORT = 2001;

    private BxApp app;
    private Theme theme;
    private LinearLayout content;
    private TextView projectsHeading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = BxApp.get(this);
        Ui.init(this);
        theme = app.theme();
        BxApp.styleWindow(this, theme);
        buildUi();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (app == null) return;
        theme = app.theme();
        if (content != null) refresh();
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(theme.bg);
        setContentView(root);

        LinearLayout header = Ui.row(this);
        header.setPadding(Ui.dp(18), Ui.dp(14), Ui.dp(12), Ui.dp(10));
        header.setBackgroundColor(theme.header);
        LinearLayout titles = Ui.column(this);
        TextView title = Ui.label(this, theme, "BXAnimator", 24, theme.text, true);
        titles.addView(title);
        TextView tagline = Ui.label(this, theme, "Anime animation studio  \u00b7  pro timeline, rigs and shaders",
                11.5f, theme.textDim, false);
        titles.addView(tagline);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        IconButton themeButton = new IconButton(this, theme, "theme", null, IconButton.Style.BAR);
        themeButton.setOnClickListener(v -> pickTheme());
        header.addView(themeButton, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)));
        IconButton importButton = new IconButton(this, theme, "import", null, IconButton.Style.BAR);
        importButton.setOnClickListener(v -> importProject());
        header.addView(importButton, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)));
        IconButton helpButton = new IconButton(this, theme, "help", null, IconButton.Style.BAR);
        helpButton.setOnClickListener(v -> startActivity(new Intent(this, HelpActivity.class)));
        header.addView(helpButton, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        View line = new View(this);
        line.setBackgroundColor(theme.border);
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(1)));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        content = Ui.column(this);
        content.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void refresh() {
        if (content == null) return;
        content.removeAllViews();

        content.addView(heroCard());
        addGap(14);
        content.addView(toolsRow());
        addGap(18);

        projectsHeading = Ui.label(this, theme, "PROJECTS", 10.5f, theme.accent, true);
        content.addView(projectsHeading);
        addGap(8);

        List<ProjectStore.Entry> entries = app.store().list();
        if (entries.isEmpty()) {
            content.addView(emptyCard());
        } else {
            int columns = Ui.isLandscape(this) ? 3 : 2;
            for (int i = 0; i < entries.size(); i += columns) {
                LinearLayout row = Ui.row(this);
                row.setGravity(Gravity.TOP);
                for (int c = 0; c < columns; c++) {
                    int index = i + c;
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    lp.rightMargin = c == columns - 1 ? 0 : Ui.dp(10);
                    if (index < entries.size()) {
                        row.addView(projectCard(entries.get(index)), lp);
                    } else {
                        row.addView(Ui.space(this, 1, 1), lp);
                    }
                }
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = Ui.dp(10);
                content.addView(row, rowLp);
            }
        }

        addGap(12);
        TextView footer = Ui.label(this, theme,
                "BXAnimator " + versionName() + "  \u00b7  universal APK  \u00b7  projects stay on this device",
                10.5f, theme.textDim, false);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer);
    }

    private View heroCard() {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.gradient(Ui.mix(theme.panelAlt, theme.accent, 0.10f),
                theme.panelAlt, 16));
        card.setPadding(Ui.dp(18), Ui.dp(16), Ui.dp(18), Ui.dp(16));
        TextView kicker = Ui.label(this, theme, "START A NEW SHOT", 10, theme.accent, true);
        card.addView(kicker);
        TextView title = Ui.label(this, theme, "Build an episode, one scene at a time", 18, theme.text, true);
        title.setPadding(0, Ui.dp(4), 0, Ui.dp(4));
        card.addView(title);
        TextView body = Ui.label(this, theme,
                "Pose a rig, draw on top, move the camera and stack shaders. Every template already animates.",
                12, theme.textDim, false);
        body.setLineSpacing(Ui.dp(3), 1f);
        card.addView(body);
        TextView button = Ui.button(this, theme, "New project", true, this::newProject);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(12);
        card.addView(button, lp);
        return card;
    }

    private View toolsRow() {
        LinearLayout row = Ui.row(this);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        leftLp.rightMargin = Ui.dp(10);
        row.addView(toolCard("film", "Remaster video",
                "Rebuild an old clip at a modern resolution with the Modern Remaster stack.",
                () -> startActivity(new Intent(this, RemasterActivity.class))), leftLp);
        row.addView(toolCard("shader", "Shader Lab",
                "Preview every GLSL effect, tune it live and apply it to a scene.",
                () -> startActivity(new Intent(this, ShaderLabActivity.class))),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return row;
    }

    private View toolCard(String icon, String title, String text, Runnable action) {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.rounded(theme.panel, 13, theme.border, 1));
        card.setPadding(Ui.dp(13), Ui.dp(12), Ui.dp(13), Ui.dp(12));
        card.addView(new app.bxanimator.ui.IconView(this, icon, theme.accent, 17));
        TextView name = Ui.label(this, theme, title, 13.5f, theme.text, true);
        name.setPadding(0, Ui.dp(6), 0, Ui.dp(3));
        card.addView(name);
        TextView body = Ui.label(this, theme, text, 11, theme.textDim, false);
        body.setLineSpacing(Ui.dp(2), 1f);
        card.addView(body);
        card.setClickable(true);
        card.setOnClickListener(v -> action.run());
        return card;
    }

    private View emptyCard() {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.rounded(theme.panel, 13, theme.border, 1));
        card.setPadding(Ui.dp(16), Ui.dp(20), Ui.dp(16), Ui.dp(20));
        TextView title = Ui.label(this, theme, "Your library is empty", 15, theme.text, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title);
        TextView text = Ui.label(this, theme,
                "Start from a template: a rooftop clash, a quiet confession or a city flythrough.",
                11.5f, theme.textDim, false);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, Ui.dp(5), 0, Ui.dp(12));
        card.addView(text);
        TextView button = Ui.button(this, theme, "Create the first project", true, this::newProject);
        card.addView(button);
        return card;
    }

    private View projectCard(ProjectStore.Entry entry) {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.rounded(theme.panel, 13, theme.border, 1));
        card.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(9));

        FrameLayout thumbBox = new FrameLayout(this);
        thumbBox.setBackground(Ui.rounded(theme.panelAlt, 10, theme.border, 0));
        Bitmap thumb = app.store().loadThumb(entry.id);
        if (thumb != null) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(thumb);
            thumbBox.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            TextView letter = Ui.label(this, theme,
                    entry.name.isEmpty() ? "B" : entry.name.substring(0, 1).toUpperCase(java.util.Locale.US),
                    34, Ui.alpha(theme.accent, 0.65f), true);
            letter.setGravity(Gravity.CENTER);
            thumbBox.addView(letter, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        card.addView(thumbBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(92)));

        LinearLayout titleRow = Ui.row(this);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Ui.dp(7);
        TextView name = Ui.label(this, theme, entry.name, 12.5f, theme.text, true);
        name.setSingleLine(true);
        titleRow.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        IconButton more = new IconButton(this, theme, "more", null, IconButton.Style.BAR);
        titleRow.addView(more, new LinearLayout.LayoutParams(Ui.dp(28), Ui.dp(28)));
        card.addView(titleRow, titleLp);

        TextView meta = Ui.label(this, theme,
                ProjectStore.summary(entry) + "  \u00b7  " + relativeTime(entry.updated),
                10.5f, theme.textDim, false);
        card.addView(meta);

        card.setClickable(true);
        card.setOnClickListener(v -> openProject(entry.id));
        more.setOnClickListener(v -> projectActions(entry));
        return card;
    }

    // -------------------------------------------------------------- actions

    private void newProject() {
        Dialogs.template(this, theme, index -> {
            ProjectFactory.Template template = ProjectFactory.templates().get(index);
            Ui.prompt(this, theme, "Project name", "Name", template.label, text -> {
                String name = text == null || text.trim().isEmpty() ? template.label : text.trim();
                try {
                    Project project = Project.create(name, template.id);
                    app.store().save(project, null);
                    openProject(project.id);
                } catch (Exception e) {
                    Ui.alert(this, theme, "Could not create the project", String.valueOf(e.getMessage()), "OK");
                }
            });
        });
    }

    private void openProject(String id) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("project_id", id);
        startActivity(intent);
    }

    private void projectActions(ProjectStore.Entry entry) {
        String[] items = {"Open", "Duplicate", "Rename", "Share project", "Delete"};
        Ui.choose(this, theme, entry.name, items, index -> {
            switch (index) {
                case 0:
                    openProject(entry.id);
                    break;
                case 1:
                    try {
                        Project source = app.store().load(entry.id);
                        app.store().duplicate(source, source.name + " copy");
                        refresh();
                    } catch (Exception e) {
                        Ui.toast(this, "Could not duplicate: " + e.getMessage());
                    }
                    break;
                case 2:
                    Ui.prompt(this, theme, "Rename project", "Name", entry.name, text -> {
                        if (text == null || text.isEmpty()) return;
                        try {
                            Project project = app.store().load(entry.id);
                            project.name = text;
                            app.store().save(project, null);
                            refresh();
                        } catch (Exception e) {
                            Ui.toast(this, "Could not rename: " + e.getMessage());
                        }
                    });
                    break;
                case 3:
                    try {
                        Ui.shareFile(this, app.store().writeShareCopy(entry.id),
                                "application/octet-stream", "Share project");
                    } catch (Exception e) {
                        Ui.toast(this, "Could not share: " + e.getMessage());
                    }
                    break;
                case 4:
                    Ui.confirm(this, theme, "Delete project",
                            "Delete \u201c" + entry.name + "\u201d from this device? This cannot be undone.",
                            "Delete", () -> {
                                app.store().delete(entry.id);
                                refresh();
                            });
                    break;
                default:
                    break;
            }
        });
    }

    private void pickTheme() {
        Dialogs.theme(this, theme, index -> {
            app.setThemeId(Theme.Catalog.all().get(index).id);
            recreate();
        });
    }

    private void importProject() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json", "application/octet-stream", "text/plain"
        });
        try {
            startActivityForResult(intent, REQ_IMPORT);
        } catch (Exception e) {
            Ui.toast(this, "No file picker available");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = "Imported project";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("cannot open the file");
            Project project = app.store().importJson(in, name);
            Ui.toast(this, "Imported " + project.name);
            refresh();
            openProject(project.id);
        } catch (Exception e) {
            Ui.alert(this, theme, "Import failed",
                    "That file is not a BXAnimator project.\n" + e.getMessage(), "OK");
        }
    }

    // -------------------------------------------------------------- helpers

    private void addGap(int dp) {
        content.addView(Ui.space(this, 1, dp));
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    private static String relativeTime(long millis) {
        long delta = Math.max(0, System.currentTimeMillis() - millis);
        long minutes = delta / 60000L;
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 30) return days + "d ago";
        return (days / 30) + "mo ago";
    }
}
