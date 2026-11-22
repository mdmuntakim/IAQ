package app.bxanimator;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.bxanimator.core.Theme;
import app.bxanimator.ui.IconButton;
import app.bxanimator.ui.Ui;

/** A readable manual, written the way the app is used: shot by shot. */
public final class HelpActivity extends Activity {

    private BxApp app;
    private Theme theme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = BxApp.get(this);
        Ui.init(this);
        theme = app.theme();
        BxApp.styleWindow(this, theme);
        buildUi();
    }

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
        titles.addView(Ui.label(this, theme, "How BXAnimator works", 16, theme.text, true));
        titles.addView(Ui.label(this, theme, "A short manual for a long episode", 10.5f, theme.textDim, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = Ui.column(this);
        content.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(Ui.label(this, theme,
                "BXAnimator is a 2D anime studio for a phone or tablet: puppet rigs, a keyframe timeline, "
                        + "freehand drawing, a camera and a live shader stack. Everything you see in the preview "
                        + "is exactly what the exporter writes.",
                13, theme.textDim, false));
        addGap(content, 12);

        card(content, "1. Start a project",
                "The dashboard offers four templates. Rooftop Clash is a complete action shot with two characters "
                        + "and a push-in camera, Quiet Confession is a close up with a blink and a slow drift, "
                        + "City Flythrough is a parallax skyline, and Empty Scene starts from one camera. "
                        + "Each template is a real, animated scene, so you can study the keys instead of staring at an empty timeline.");
        card(content, "2. Move around the viewport",
                "One finger with Select moves a layer. The corner handle scales it, the round handle above the frame "
                        + "rotates it. Two fingers zoom and pan the viewport itself, and a double tap fits the frame. "
                        + "The small HUD in the corner always shows the scene, the timecode and the zoom level.");
        card(content, "3. Pose a character",
                "Switch to Pose and drag a limb. The bone under your finger bends and a key is written at the playhead. "
                        + "The Layer tab lists every bone with the same slider, plus face controls for blink, brow, mouth "
                        + "and eye look. Key full pose stores the entire pose in one tap; Reset pose returns to rest.");
        card(content, "4. Animate on the timeline",
                "Every layer has rows for position, scale, rotation, opacity and, for rigs, every bone. Tap the arrow "
                        + "on a layer to open its channels. Drag a diamond to retime a key, tap the toolbar to add or "
                        + "delete a key, and use the interpolation button for ease in, ease out, back, elastic or bounce. "
                        + "Auto key writes a key on every drag; with it off, a drag offsets the whole curve, which is the "
                        + "quickest way to lift a finished performance.");
        card(content, "5. Draw on top",
                "A Drawing layer holds freehand strokes per frame. Choose Draw, pick a drawing layer (or let the app "
                        + "create one), then paint. Cumulative mode keeps earlier frames visible, frame-only mode shows "
                        + "just the current frame. Erase is a brush too. Strokes are vector, so they stay sharp at any export size.");
        card(content, "6. Fly the camera",
                "The Camera tool moves the shot, not the artwork: drag to pan, pinch to zoom, twist to roll. "
                        + "Camera keys live on their own row. A slow scale from 1.00 to 1.15 over a scene is the classic "
                        + "anime push-in; a small roll adds unease.");
        card(content, "7. Light it with shaders",
                "The Scene tab carries a stack of effects: cel outline, colour grade, bloom, chromatic split, grain, "
                        + "vignette, speed lines, screentone, watercolour, modern remaster, tape and sharpen. Stack them "
                        + "in any order and tune intensity and every parameter. Presets package a whole look in one tap. "
                        + "The FX switch in the timeline bar bypasses the stack while you animate.");
        card(content, "8. Preview the GLSL",
                "Shader Lab renders the current frame through the actual GLSL ES 2.0 shader for the selected effect. "
                        + "Scrub time, tune the sliders and apply the result to the scene. It is the fastest way to see "
                        + "how a shader reacts to a specific frame before committing it.");
        card(content, "9. Remaster old footage",
                "Video Remaster takes a clip from the device, decodes it frame by frame, rebuilds it at 720p or 1080p "
                        + "and runs a chosen shader stack on every frame. Modern Remaster combines denoise, unsharp mask "
                        + "and colour restoration. Choose 15, 30 or 60 seconds for a quick test, or Full for the whole clip.");
        card(content, "10. Export",
                "Export video writes an MP4 at 50, 75 or 100 percent of the project resolution with 24, 30 or the "
                        + "project frame rate. PNG sequence writes a zip with numbered frames and the editable project "
                        + "inside. Share project sends the .bxanim file, which imports on another device.");
        card(content, "Themes and storage",
                "Six themes restyle every surface: Midnight, Graphite, Ink & Paper, Sakura Dusk, Amber Studio and "
                        + "Cyber Ink. Projects live in the app's own folder with a thumbnail, autosave after every edit "
                        + "and survive a restart. Undo and redo keep sixty steps. Nothing is uploaded anywhere.");
        card(content, "Small habits that help",
                "Key the whole pose before a big action, then add one extra key a few frames later for the impact. "
                        + "Use Hold interpolation for a frozen beat and Back Out for a snap. Keep backgrounds on their "
                        + "own layers so the camera can parallax them. Render a 50 percent draft first, then the final "
                        + "100 percent pass.");

        addGap(content, 10);
        TextView footer = Ui.label(this, theme,
                "BXAnimator 1.0.0  \u00b7  universal APK  \u00b7  built with the Android SDK command line tools",
                10.5f, theme.textDim, false);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer);
    }

    private void card(LinearLayout parent, String title, String text) {
        LinearLayout box = Ui.column(this);
        box.setBackground(Ui.rounded(theme.panel, 12, theme.border, 1));
        box.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(13));
        TextView heading = Ui.label(this, theme, title, 14, theme.accent, true);
        box.addView(heading);
        TextView body = Ui.label(this, theme, text, 12.5f, theme.textDim, false);
        body.setLineSpacing(Ui.dp(4), 1f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = Ui.dp(5);
        box.addView(body, bodyLp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(10);
        parent.addView(box, lp);
    }

    private void addGap(LinearLayout parent, int dp) {
        parent.addView(Ui.space(this, 1, dp));
    }
}
