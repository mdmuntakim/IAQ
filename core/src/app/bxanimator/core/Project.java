package app.bxanimator.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A project: frame rate, resolution, theme and an ordered list of scenes. */
public final class Project {

    public int format = 1;
    public String id = Ids.next("project");
    public String name = "Untitled";
    public int fps = 24;
    public int width = 1920;
    public int height = 1080;
    public String theme = "midnight";
    public String notes = "";
    public long created = System.currentTimeMillis();
    public long updated = System.currentTimeMillis();
    public final List<Scene> scenes = new ArrayList<>();

    public static Project create(String name, String templateId) {
        return ProjectFactory.create(name, templateId);
    }

    public Scene scene(int index) {
        if (scenes.isEmpty()) {
            scenes.add(ProjectFactory.blankScene("Scene 1", width, height));
        }
        return scenes.get(AnimMath.clamp(index, 0, scenes.size() - 1));
    }

    public int sceneIndex(String sceneId) {
        for (int i = 0; i < scenes.size(); i++) {
            if (scenes.get(i).id.equals(sceneId)) return i;
        }
        return 0;
    }

    public double duration() {
        double total = 0;
        for (Scene s : scenes) total += s.duration;
        return total;
    }

    public int totalFrames() {
        int total = 0;
        for (Scene s : scenes) total += Math.max(1, (int) Math.round(s.duration * fps));
        return total;
    }

    public void touch() {
        updated = System.currentTimeMillis();
    }

    public void validate() {
        if (fps < 1 || fps > 120) fps = 24;
        if (width < 64) width = 1920;
        if (height < 64) height = 1080;
        width = width - (width % 2);
        height = height - (height % 2);
        if (scenes.isEmpty()) scenes.add(ProjectFactory.blankScene("Scene 1", width, height));
        for (Scene s : scenes) {
            s.ensureCamera(width / 2f, height / 2f);
            for (Layer l : s.layers) {
                if (l.type == LayerType.RIG && l.rig == null) l.rig = RigLibrary.create(RigLibrary.HERO);
                if (l.channels == null) continue;
            }
            if (s.duration < 0.25) s.duration = 1;
        }
        if (Theme.Catalog.byId(theme) == null) theme = "midnight";
    }

    public Project copy() {
        Project p = new Project();
        p.format = format;
        p.id = id;
        p.name = name;
        p.fps = fps;
        p.width = width;
        p.height = height;
        p.theme = theme;
        p.notes = notes;
        p.created = created;
        p.updated = updated;
        for (Scene s : scenes) p.scenes.add(s.copy());
        return p;
    }

    public String toJsonString() {
        return Json.write(toJson());
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", format);
        m.put("id", id);
        m.put("name", name);
        m.put("fps", fps);
        m.put("width", width);
        m.put("height", height);
        m.put("theme", theme);
        m.put("notes", notes);
        m.put("created", created);
        m.put("updated", updated);
        List<Object> ss = new ArrayList<>();
        for (Scene s : scenes) ss.add(s.toJson());
        m.put("scenes", ss);
        return m;
    }

    public static Project fromJsonString(String text) {
        return fromJson(Json.parseObject(text));
    }

    public static Project fromJson(Map<String, Object> m) {
        Project p = new Project();
        p.format = Json.inum(m, "format", 1);
        p.id = Json.str(m, "id", Ids.next("project"));
        p.name = Json.str(m, "name", "Untitled");
        p.fps = Json.inum(m, "fps", 24);
        p.width = Json.inum(m, "width", 1920);
        p.height = Json.inum(m, "height", 1080);
        p.theme = Json.str(m, "theme", "midnight");
        p.notes = Json.str(m, "notes", "");
        p.created = Json.lnum(m, "created", System.currentTimeMillis());
        p.updated = Json.lnum(m, "updated", System.currentTimeMillis());
        for (Object o : Json.list(m.get("scenes"))) {
            p.scenes.add(Scene.fromJson(Json.map(o)));
        }
        p.validate();
        return p;
    }
}
