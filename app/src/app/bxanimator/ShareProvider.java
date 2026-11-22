package app.bxanimator;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Minimal file provider: shares exported files with other apps without
 * depending on AndroidX. Only files below the app's own external files
 * directory are ever served.
 */
public final class ShareProvider extends ContentProvider {

    public static final String AUTHORITY = "app.bxanimator.files";

    public static Uri uriFor(File file) {
        return Uri.parse("content://" + AUTHORITY + "/" + Uri.encode(file.getAbsolutePath()));
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null) throw new FileNotFoundException("no path");
        File file = new File(path);
        try {
            File root = getContext().getExternalFilesDir(null);
            if (root == null) root = getContext().getFilesDir();
            String rootPath = root.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (!filePath.startsWith(rootPath + File.separator)) {
                throw new FileNotFoundException("outside of the app directory");
            }
            if (!file.exists()) throw new FileNotFoundException(filePath);
            return file;
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File file = resolve(uri);
            String[] cols = projection != null ? projection
                    : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor cursor = new MatrixCursor(cols, 1);
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = file.getName();
                else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = file.length();
                else row[i] = null;
            }
            cursor.addRow(row);
            return cursor;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) return "application/octet-stream";
        String ext = MimeTypeMap.getFileExtensionFromUrl(name);
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return type == null ? "application/octet-stream" : type;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }

    public static void grant(Context context, android.content.Intent intent, Uri uri) {
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.grantUriPermission(intent.getPackage(), uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
}
