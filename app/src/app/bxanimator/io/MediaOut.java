package app.bxanimator.io;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Writes exported files to the app directory and publishes them to the gallery. */
public final class MediaOut {

    private MediaOut() {
    }

    public static File exportDir(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        File dir = new File(base, "exports");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File newExport(Context context, String baseName, String extension) {
        File dir = exportDir(context);
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                .format(new java.util.Date());
        String clean = sanitize(baseName);
        return new File(dir, clean + "-" + stamp + "." + extension);
    }

    /** Copies a content uri into the app's asset folder. */
    public static File copyTo(Context context, Uri uri, File dir, String name) throws IOException {
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File out = new File(dir, name);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IOException("cannot open the selected file");
            copy(in, os);
        }
        return out;
    }

    /**
     * Publishes a finished file so other apps can see it. Uses MediaStore on
     * Android 10+ and the public folder plus a media scan on older releases.
     */
    public static Uri publish(Context context, File file, String mime, String relativeDir, String displayName) {
        if (file == null || !file.isFile()) return null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver resolver = context.getContentResolver();
                Uri collection;
                if (mime.startsWith("video/")) {
                    collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                } else if (mime.startsWith("image/")) {
                    collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                } else {
                    collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                }
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri item = resolver.insert(collection, values);
                if (item == null) return null;
                try (InputStream in = new FileInputStream(file);
                     OutputStream out = resolver.openOutputStream(item)) {
                    if (out == null) throw new IOException("cannot write to the gallery");
                    copy(in, out);
                }
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(item, values, null, null);
                return item;
            }
            File base = Environment.getExternalStoragePublicDirectory(
                    relativeDir.contains("/") ? relativeDir.substring(0, relativeDir.indexOf('/')) : relativeDir);
            File dir = new File(base, relativeDir.contains("/")
                    ? relativeDir.substring(relativeDir.indexOf('/') + 1) : "");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File target = new File(dir, displayName);
            try (InputStream in = new FileInputStream(file); OutputStream out = new FileOutputStream(target)) {
                copy(in, out);
            }
            MediaScannerConnection.scanFile(context, new String[]{target.getAbsolutePath()},
                    new String[]{mime}, null);
            return Uri.fromFile(target);
        } catch (Exception e) {
            return null;
        }
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb);
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0);
    }

    public static String sanitize(String name) {
        String clean = name == null ? "bxanimator" : name.trim();
        clean = clean.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        if (clean.isEmpty()) clean = "bxanimator";
        if (clean.length() > 48) clean = clean.substring(0, 48);
        return clean;
    }
}
