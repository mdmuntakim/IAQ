package app.bxanimator.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A colour theme. Every surface of the editor reads from one of these fields, so
 * switching the theme restyles the whole studio at once.
 */
public final class Theme {

    public final String id;
    public final String label;
    public final boolean dark;
    public final int bg;
    public final int panel;
    public final int panelAlt;
    public final int header;
    public final int border;
    public final int text;
    public final int textDim;
    public final int accent;
    public final int accentSoft;
    public final int key;
    public final int playhead;
    public final int selection;
    public final int danger;
    public final int success;
    public final int canvas;
    public final int canvasGrid;
    public final int track;
    public final int trackAlt;
    public final int ruler;

    public Theme(String id, String label, boolean dark, int bg, int panel, int panelAlt, int header,
                 int border, int text, int textDim, int accent, int accentSoft, int key, int playhead,
                 int selection, int danger, int success, int canvas, int canvasGrid, int track,
                 int trackAlt, int ruler) {
        this.id = id;
        this.label = label;
        this.dark = dark;
        this.bg = bg;
        this.panel = panel;
        this.panelAlt = panelAlt;
        this.header = header;
        this.border = border;
        this.text = text;
        this.textDim = textDim;
        this.accent = accent;
        this.accentSoft = accentSoft;
        this.key = key;
        this.playhead = playhead;
        this.selection = selection;
        this.danger = danger;
        this.success = success;
        this.canvas = canvas;
        this.canvasGrid = canvasGrid;
        this.track = track;
        this.trackAlt = trackAlt;
        this.ruler = ruler;
    }

    public static final class Catalog {

        private static final List<Theme> ALL = new ArrayList<>();

        static {
            ALL.add(new Theme("midnight", "Midnight", true,
                    0xFF0B0E14, 0xFF121722, 0xFF0F141E, 0xFF0E131C, 0xFF1F2836,
                    0xFFE8EDF6, 0xFF8A94A6, 0xFFFF7A18, 0xFFFFB347, 0xFFFFC857,
                    0xFFFF4D6D, 0xFF2A3A55, 0xFFFF5A5F, 0xFF3DDC97,
                    0xFF070A10, 0xFF1A2230, 0xFF151B27, 0xFF111722, 0xFF0C111A));
            ALL.add(new Theme("graphite", "Graphite", true,
                    0xFF111315, 0xFF191C20, 0xFF15181C, 0xFF141619, 0xFF2A2F36,
                    0xFFECEEF1, 0xFF9AA0A8, 0xFF38BDF8, 0xFF7DD3FC, 0xFFFDE68A,
                    0xFFF87171, 0xFF23303D, 0xFFF87171, 0xFF4ADE80,
                    0xFF0A0B0D, 0xFF23272D, 0xFF1D2126, 0xFF171A1E, 0xFF131619));
            ALL.add(new Theme("inkpaper", "Ink & Paper", false,
                    0xFFF2EEE5, 0xFFFBF9F3, 0xFFF7F3EA, 0xFFEDE7DA, 0xFFD9D1C0,
                    0xFF1F1D1A, 0xFF7A7468, 0xFFE4572E, 0xFFF2994A, 0xFF2F6F4E,
                    0xFFD64545, 0xFFE2D9C6, 0xFFC43D3D, 0xFF2F7A57,
                    0xFFFBF8F1, 0xFFE4DCCB, 0xFFF5F0E6, 0xFFEFE9DD, 0xFFEDE6D8));
            ALL.add(new Theme("sakura", "Sakura Dusk", true,
                    0xFF150F1A, 0xFF1E1626, 0xFF191220, 0xFF1B1323, 0xFF352A40,
                    0xFFF7EAF4, 0xFFA793B0, 0xFFFF7EB6, 0xFFFFB3D1, 0xFFFFD166,
                    0xFFFF5C8A, 0xFF3C2A48, 0xFFFF6B9D, 0xFF5EEAD4,
                    0xFF0C0810, 0xFF2A1E33, 0xFF241A2C, 0xFF1B1322, 0xFF1A1220));
            ALL.add(new Theme("amber", "Amber Studio", true,
                    0xFF14100C, 0xFF1D1712, 0xFF19140F, 0xFF181310, 0xFF3A2E22,
                    0xFFF6EDE2, 0xFFA99A88, 0xFFF5A524, 0xFFFFC97A, 0xFFFFE0A3,
                    0xFFFF6B4A, 0xFF3A2C1E, 0xFFFF7043, 0xFF8FD694,
                    0xFF0B0806, 0xFF2A2118, 0xFF241C14, 0xFF1B1510, 0xFF19130E));
            ALL.add(new Theme("cyber", "Cyber Ink", true,
                    0xFF060A14, 0xFF0B1220, 0xFF08101C, 0xFF09101B, 0xFF1B2B44,
                    0xFFE6F2FF, 0xFF7C90AB, 0xFF22D3EE, 0xFFA855F7, 0xFFFDE047,
                    0xFFFF3D8B, 0xFF13233D, 0xFFFF3D8B, 0xFF34D399,
                    0xFF03060C, 0xFF122036, 0xFF101A2C, 0xFF0C1524, 0xFF0A1220));
        }

        private Catalog() {
        }

        public static List<Theme> all() {
            return new ArrayList<>(ALL);
        }

        public static Theme byId(String id) {
            if (id != null) {
                for (Theme t : ALL) {
                    if (t.id.equalsIgnoreCase(id)) return t;
                }
            }
            return ALL.get(0);
        }
    }
}
