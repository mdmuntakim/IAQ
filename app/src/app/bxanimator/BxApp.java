package app.bxanimator;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

import app.bxanimator.core.Theme;
import app.bxanimator.io.ProjectStore;

/** Application state: preferences, project storage and the active theme. */
public final class BxApp extends Application {

    public static final String PREFS = "bxanimator";
    public static final String KEY_THEME = "theme";
    public static final String KEY_LAST_PROJECT = "last_project";
    public static final String KEY_QUALITY = "preview_quality";
    public static final String KEY_ONION = "onion";
    public static final String KEY_AUTOKEY = "autokey";
    public static final String KEY_SEEN_HELP = "seen_help";

    private static BxApp instance;

    private SharedPreferences prefs;
    private ProjectStore store;
    private Theme theme;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        store = new ProjectStore(this);
        theme = Theme.Catalog.byId(prefs.getString(KEY_THEME, "midnight"));
    }

    public static BxApp get(Context context) {
        if (instance == null) instance = (BxApp) context.getApplicationContext();
        return instance;
    }

    public SharedPreferences prefs() {
        return prefs;
    }

    public ProjectStore store() {
        return store;
    }

    public Theme theme() {
        return theme;
    }

    public void setThemeId(String id) {
        theme = Theme.Catalog.byId(id);
        prefs.edit().putString(KEY_THEME, theme.id).apply();
    }

    public boolean onion() {
        return prefs.getBoolean(KEY_ONION, false);
    }

    public void setOnion(boolean on) {
        prefs.edit().putBoolean(KEY_ONION, on).apply();
    }

    public boolean autoKey() {
        return prefs.getBoolean(KEY_AUTOKEY, true);
    }

    public void setAutoKey(boolean on) {
        prefs.edit().putBoolean(KEY_AUTOKEY, on).apply();
    }

    /** 0 = draft, 1 = good, 2 = full. */
    public int previewQuality() {
        return prefs.getInt(KEY_QUALITY, 1);
    }

    public void setPreviewQuality(int q) {
        prefs.edit().putInt(KEY_QUALITY, Math.max(0, Math.min(2, q))).apply();
    }

    public void rememberProject(String id) {
        prefs.edit().putString(KEY_LAST_PROJECT, id).apply();
    }

    public String lastProject() {
        return prefs.getString(KEY_LAST_PROJECT, null);
    }

    public void markHelpSeen() {
        prefs.edit().putBoolean(KEY_SEEN_HELP, true).apply();
    }

    public boolean helpSeen() {
        return prefs.getBoolean(KEY_SEEN_HELP, false);
    }

    /** Applies the current theme to the system bars of an activity. */
    public static void styleWindow(Activity activity, Theme theme) {
        Window window = activity.getWindow();
        window.setStatusBarColor(theme.header);
        window.setNavigationBarColor(theme.bg);
        View decor = window.getDecorView();
        if (Build.VERSION.SDK_INT >= 26) {
            int flags = decor.getSystemUiVisibility();
            if (theme.dark) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(theme.bg));
    }

    public static int statusBarHeight(Context context) {
        int id = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? context.getResources().getDimensionPixelSize(id) : (int) (24 * context.getResources().getDisplayMetrics().density);
    }

    public static int navigationBarHeight(Context context) {
        int id = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? context.getResources().getDimensionPixelSize(id) : (int) (48 * context.getResources().getDisplayMetrics().density);
    }

    public static int withAlpha(int color, float alpha) {
        int a = (int) (Color.alpha(color) * Math.max(0f, Math.min(1f, alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
