package app.bxanimator.core;

import java.util.Locale;
import java.util.UUID;

/** Short, readable, unique identifiers for projects, scenes, layers and rigs. */
public final class Ids {

    private Ids() {
    }

    public static String next(String prefix) {
        String clean = prefix == null ? "id" : prefix.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
        if (clean.isEmpty()) clean = "id";
        return clean + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    public static String project(String name) {
        String clean = name == null ? "" : name.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-");
        clean = clean.replaceAll("^-+|-+$", "");
        if (clean.isEmpty()) clean = "project";
        if (clean.length() > 40) clean = clean.substring(0, 40);
        return clean;
    }
}
