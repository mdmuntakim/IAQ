package app.bxanimator.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import app.bxanimator.core.Theme;

/** Small theme aware UI toolkit. Everything is built in code, no XML themes. */
public final class Ui {

    public interface Callback {
        void run();
    }

    public interface TextCallback {
        void run(String text);
    }

    public interface IndexCallback {
        void run(int index);
    }

    private static float density = 2f;

    private Ui() {
    }

    public static void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
    }

    public static float density() {
        return density;
    }

    public static int dp(float value) {
        return Math.round(value * density);
    }

    public static float sp(float value) {
        return value * density;
    }

    public static int alpha(int color, float a) {
        int alpha = Math.round(Color.alpha(color) * clamp(a, 0f, 1f));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static int mix(int a, int b, float t) {
        t = clamp(t, 0f, 1f);
        int aa = (int) (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t);
        int rr = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int gg = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bb = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.argb(aa, rr, gg, bb);
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        return p;
    }

    public static Paint stroke(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        return p;
    }

    public static Paint text(int color, float sizePx, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(sizePx);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
        p.setTextAlign(Paint.Align.LEFT);
        return p;
    }

    public static GradientDrawable rounded(int fill, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    public static GradientDrawable gradient(int top, int bottom, float radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, bottom});
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    public static void background(View view, int fill, float radiusDp, int strokeColor, float strokeDp) {
        view.setBackground(rounded(fill, radiusDp, strokeColor, strokeDp));
    }

    public static void rippleBackground(View view, int fill, float radiusDp, int ripple, int strokeColor, float strokeDp) {
        GradientDrawable base = rounded(fill, radiusDp, strokeColor, strokeDp);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(ripple, 0.28f)), base, null));
    }

    public static TextView label(Context context, Theme theme, String text, float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(sizeSp);
        tv.setTypeface(Typeface.create(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
        tv.setIncludeFontPadding(false);
        tv.setLineSpacing(dp(2), 1f);
        return tv;
    }

    public static TextView button(Context context, Theme theme, String label, boolean primary, Callback onClick) {
        TextView tv = label(context, theme, label, 13, primary ? (theme.dark ? 0xFF0B0E14 : 0xFFFFFFFF) : theme.text, true);
        int fill = primary ? theme.accent : theme.panelAlt;
        int stroke = primary ? theme.accent : theme.border;
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(10), dp(16), dp(10));
        rippleBackground(tv, fill, 9, primary ? 0xFFFFFFFF : theme.accent, stroke, 1);
        tv.setClickable(true);
        tv.setFocusable(true);
        if (onClick != null) tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    public static TextView chip(Context context, Theme theme, String label, boolean active, Callback onClick) {
        TextView tv = label(context, theme, label, 11.5f, active ? theme.accentSoft : theme.textDim, active);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(10), dp(6), dp(10), dp(6));
        rippleBackground(tv, active ? alpha(theme.accent, 0.16f) : theme.panelAlt, 8,
                theme.accent, active ? alpha(theme.accent, 0.5f) : theme.border, 1);
        tv.setClickable(true);
        if (onClick != null) tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    public static LinearLayout row(Context context) {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout column(Context context) {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static View space(Context context, int widthDp, int heightDp) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)));
        return v;
    }

    public static View flexibleSpace(Context context) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    public static View divider(Context context, Theme theme) {
        View v = new View(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(8);
        v.setLayoutParams(lp);
        v.setBackgroundColor(theme.border);
        return v;
    }

    public static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void hideKeyboard(Activity activity) {
        View focus = activity.getCurrentFocus();
        if (focus == null) return;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    // ------------------------------------------------------------- dialogs

    public static Dialog panel(Context context, Theme theme, String title, View body,
                               String confirmLabel, Callback onConfirm) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attrs = dialog.getWindow().getAttributes();
        attrs.dimAmount = 0.6f;
        dialog.getWindow().setAttributes(attrs);

        LinearLayout root = column(context);
        root.setBackground(rounded(theme.panel, 16, theme.border, 1));
        root.setPadding(dp(18), dp(16), dp(18), dp(14));

        LinearLayout head = row(context);
        TextView titleView = label(context, theme, title, 16, theme.text, true);
        head.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        IconButton close = new IconButton(context, theme, "close", null, IconButton.Style.BAR);
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(32), dp(32)));
        root.addView(head);

        View gap = new View(context);
        gap.setLayoutParams(new LinearLayout.LayoutParams(1, dp(10)));
        root.addView(gap);

        if (body != null) {
            if (body.getParent() != null) ((ViewGroup) body.getParent()).removeView(body);
            ScrollView scroll = new ScrollView(context);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.addView(body);
            root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if (confirmLabel != null) {
            root.addView(divider(context, theme));
            LinearLayout footer = row(context);
            footer.setGravity(Gravity.END);
            TextView cancel = button(context, theme, "Cancel", false, dialog::dismiss);
            footer.addView(cancel);
            footer.addView(space(context, 8, 1));
            TextView confirm = button(context, theme, confirmLabel, true, () -> {
                dialog.dismiss();
                if (onConfirm != null) onConfirm.run();
            });
            footer.addView(confirm);
            root.addView(footer);
        }

        dialog.setContentView(root);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window == null) return;
            int width = context.getResources().getDisplayMetrics().widthPixels - dp(32);
            width = Math.min(width, dp(520));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        });
        return dialog;
    }

    public static void alert(Context context, Theme theme, String title, String message, String ok) {
        LinearLayout body = column(context);
        TextView text = label(context, theme, message, 13, theme.textDim, false);
        text.setLineSpacing(dp(4), 1f);
        body.addView(text);
        panel(context, theme, title, body, ok, null).show();
    }

    public static void confirm(Context context, Theme theme, String title, String message,
                               String confirmLabel, Callback onConfirm) {
        LinearLayout body = column(context);
        TextView text = label(context, theme, message, 13, theme.textDim, false);
        text.setLineSpacing(dp(4), 1f);
        body.addView(text);
        panel(context, theme, title, body, confirmLabel, onConfirm).show();
    }

    public static void prompt(Context context, Theme theme, String title, String hint,
                              String initial, TextCallback onDone) {
        EditText edit = new EditText(context);
        edit.setText(initial == null ? "" : initial);
        edit.setHint(hint);
        edit.setTextColor(theme.text);
        edit.setHintTextColor(alpha(theme.textDim, 0.7f));
        edit.setTextSize(15);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        edit.setBackground(rounded(theme.panelAlt, 10, theme.border, 1));
        edit.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout body = column(context);
        body.addView(edit);
        Dialog dialog = panel(context, theme, title, body, "Done", () -> {
            if (onDone != null) onDone.run(edit.getText().toString().trim());
        });
        dialog.setOnShowListener(d -> {
            edit.requestFocus();
            edit.setSelection(edit.getText().length());
            edit.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
            }, 150);
        });
        dialog.show();
    }

    public static void choose(Context context, Theme theme, String title, String[] items,
                              IndexCallback onPick) {
        LinearLayout body = column(context);
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            TextView item = label(context, theme, items[i], 14, theme.text, false);
            item.setPadding(dp(12), dp(12), dp(12), dp(12));
            rippleBackground(item, theme.panelAlt, 9, theme.accent, 0, 0);
            item.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            body.addView(item, lp);
            item.setOnClickListener(v -> {
                Dialog d = (Dialog) item.getTag();
                if (d != null) d.dismiss();
                if (onPick != null) onPick.run(index);
            });
        }
        Dialog dialog = panel(context, theme, title, body, null, null);
        for (int i = 0; i < body.getChildCount(); i++) {
            body.getChildAt(i).setTag(dialog);
        }
        dialog.show();
    }

    public static void shareFile(Context context, java.io.File file, String mime, String chooserTitle) {
        try {
            android.net.Uri uri = app.bxanimator.ShareProvider.uriFor(file);
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType(mime);
            intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(android.content.Intent.createChooser(intent, chooserTitle));
        } catch (Exception e) {
            toast(context, "Could not share: " + e.getMessage());
        }
    }

    public static int screenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    public static int screenHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    public static boolean isLandscape(Context context) {
        return screenWidth(context) > screenHeight(context);
    }

    public static float distance(float x0, float y0, float x1, float y1) {
        return (float) Math.hypot(x1 - x0, y1 - y0);
    }

    public static boolean inRect(float x, float y, RectF r) {
        return r != null && x >= r.left && x <= r.right && y >= r.top && y <= r.bottom;
    }

    public static String percent(float value) {
        return Math.round(value * 100f) + "%";
    }

    public static String fixed(float value, int decimals) {
        return String.format(java.util.Locale.US, "%." + decimals + "f", value);
    }
}
