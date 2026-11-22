package app.bxanimator.io;

import android.content.Context;
import android.graphics.Bitmap;

import app.bxanimator.core.Ids;
import app.bxanimator.core.Json;
import app.bxanimator.core.Project;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Projects live in their own folder: project.json, thumb.png and assets/. */
public final class ProjectStore {

    public static final class Entry {
        public final String id;
        public final String name;
        public final int fps;
        public final long updated;
        public final int scenes;
        public final String theme;
        public final File dir;

        Entry(String id, String name, int fps, long updated, int scenes, String theme, File dir) {
            this.id = id;
            this.name = name;
            this.fps = fps;
            this.updated = updated;
            this.scenes = scenes;
            this.theme = theme;
            this.dir = dir;
        }

        public File thumb() {
            return new File(dir, "thumb.png");
        }
    }

    private final Context context;
    private final File root;

    public ProjectStore(Context context) {
        this.context = context.getApplicationContext();
        File base = this.context.getExternalFilesDir(null);
        if (base == null) base = this.context.getFilesDir();
        root = new File(base, "projects");
        if (!root.exists()) root.mkdirs();
    }

    public File root() {
        return root;
    }

    public File dirFor(String projectId) {
        return new File(root, projectId);
    }

    public File assetsDir(String projectId) {
        File dir = new File(dirFor(projectId), "assets");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        File[] dirs = root.listFiles();
        if (dirs == null) return out;
        for (File dir : dirs) {
            if (!dir.isDirectory()) continue;
            File json = new File(dir, "project.json");
            if (!json.isFile()) continue;
            try {
                Project p = Project.fromJsonString(readText(json));
                out.add(new Entry(p.id, p.name, p.fps, p.updated, p.scenes.size(), p.theme, dir));
            } catch (Exception ignored) {
                // A corrupt project must not hide the rest of the library.
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.updated, a.updated));
        return out;
    }

    public Entry entry(String projectId) {
        for (Entry e : list()) {
            if (e.id.equals(projectId)) return e;
        }
        return null;
    }

    public Project load(String projectId) throws IOException {
        File json = new File(dirFor(projectId), "project.json");
        if (!json.isFile()) throw new IOException("project not found: " + projectId);
        Project p = Project.fromJsonString(readText(json));
        if (!p.id.equals(projectId)) {
            // Folder is the source of truth so a copied project stays addressable.
            p.id = projectId;
        }
        return p;
    }

    public File save(Project project, Bitmap thumbnail) throws IOException {
        File dir = dirFor(project.id);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        project.touch();
        File target = new File(dir, "project.json");
        File temp = new File(dir, "project.json.tmp");
        writeText(temp, project.toJsonString());
        if (target.exists() && !target.delete()) throw new IOException("cannot replace " + target);
        if (!temp.renameTo(target)) {
            writeText(target, project.toJsonString());
            temp.delete();
        }
        if (thumbnail != null) {
            File thumb = new File(dir, "thumb.png");
            try (OutputStream out = new FileOutputStream(thumb)) {
                thumbnail.compress(Bitmap.CompressFormat.PNG, 90, out);
            }
        }
        return target;
    }

    public Project duplicate(Project source, String name) throws IOException {
        Project copy = source.copy();
        copy.id = Ids.next("project");
        copy.name = name;
        copy.created = System.currentTimeMillis();
        copy.updated = copy.created;
        for (app.bxanimator.core.Scene scene : copy.scenes) {
            scene.id = Ids.next("scene");
            for (app.bxanimator.core.Layer layer : scene.layers) {
                layer.id = Ids.next(layer.type.name().toLowerCase(java.util.Locale.US));
            }
            scene.cameraId = null;
            scene.ensureCamera(copy.width / 2f, copy.height / 2f);
        }
        File from = assetsDir(source.id);
        File to = assetsDir(copy.id);
        File[] assets = from.listFiles();
        if (assets != null) {
            for (File asset : assets) {
                copyFile(asset, new File(to, asset.getName()));
            }
        }
        save(copy, null);
        return copy;
    }

    public void delete(String projectId) {
        deleteTree(dirFor(projectId));
    }

    public Bitmap loadThumb(String projectId) {
        File thumb = new File(dirFor(projectId), "thumb.png");
        if (!thumb.isFile()) return null;
        return android.graphics.BitmapFactory.decodeFile(thumb.getAbsolutePath());
    }

    public String readProjectJson(String projectId) throws IOException {
        return readText(new File(dirFor(projectId), "project.json"));
    }

    /** Imports a .bxanim project file and gives it a fresh id. */
    public Project importJson(InputStream in, String fallbackName) throws IOException {
        String text = readText(in);
        Project p = Project.fromJsonString(text);
        p.id = Ids.next("project");
        if (p.name == null || p.name.trim().isEmpty()) p.name = fallbackName;
        p.created = System.currentTimeMillis();
        p.updated = p.created;
        p.validate();
        save(p, null);
        return p;
    }

    public File writeShareCopy(String projectId) throws IOException {
        File dir = new File(context.getCacheDir(), "share");
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, Ids.project(projectId) + ".bxanim");
        try (OutputStream os = new FileOutputStream(out)) {
            os.write(readProjectJson(projectId).getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }

    // -------------------------------------------------------------- files

    private static String readText(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readText(in);
        }
    }

    private static String readText(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void writeText(File file, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    /** Small helper used by the library screen to show a project summary. */
    public static String summary(Entry entry) {
        return entry.scenes + (entry.scenes == 1 ? " scene" : " scenes") + " \u00b7 " + entry.fps + " fps";
    }

    public static boolean isValidProjectFile(File file) {
        try {
            Project p = Project.fromJsonString(readText(file));
            return p.scenes.size() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static String pretty(Project p) {
        return Json.write(p.toJson());
    }
}
