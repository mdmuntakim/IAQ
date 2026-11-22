package app.bxanimator.core;

import java.util.ArrayList;
import java.util.List;

/** Curated shader stacks, ready to drop on a scene. */
public final class EffectPresets {

    public static final class Preset {
        public final String id;
        public final String label;
        public final String description;

        Preset(String id, String label, String description) {
            this.id = id;
            this.label = label;
            this.description = description;
        }
    }

    private static final Preset[] PRESETS = {
            new Preset("clean-cel", "Clean Cel",
                    "Crisp ink lines, gentle contrast, nothing else. The everyday look."),
            new Preset("cinematic-night", "Cinematic Night",
                    "Cool shadows, soft bloom and a light vignette for night scenes."),
            new Preset("action-burst", "Action Burst",
                    "Speed lines, colour split and bloom for impact frames."),
            new Preset("soft-memory", "Soft Memory",
                    "Watercolour bleed and warm light for flashbacks."),
            new Preset("modern-remaster", "Modern Remaster",
                    "Denoise, sharpen and restore colour. Built for old footage."),
            new Preset("retro-tape", "Retro Tape",
                    "Scanlines, grain and tracking wobble."),
            new Preset("ink-wash", "Ink Wash",
                    "Screentone, bleed and ink contours for a painted look."),
            new Preset("noir", "Noir",
                    "Almost monochrome, hard contrast, heavy vignette."),
            new Preset("dream-bloom", "Dream Bloom",
                    "Heavy glow, a hint of colour split and a soft edge.")
    };

    private EffectPresets() {
    }

    public static List<Preset> all() {
        List<Preset> list = new ArrayList<>();
        for (Preset p : PRESETS) list.add(p);
        return list;
    }

    public static Preset byId(String id) {
        for (Preset p : PRESETS) {
            if (p.id.equals(id)) return p;
        }
        return PRESETS[0];
    }

    public static List<Effect> build(String id) {
        List<Effect> list = new ArrayList<>();
        if (id == null) id = "clean-cel";
        switch (id) {
            case "cinematic-night": {
                Effect g = Effect.of(EffectType.COLOR_GRADE, 0.5f);
                g.setParam("saturation", 0.88f);
                g.setParam("contrast", 1.1f);
                g.setParam("warmth", -0.15f);
                list.add(g);
                list.add(Effect.of(EffectType.BLOOM, 0.35f));
                list.add(Effect.of(EffectType.VIGNETTE, 0.5f));
                list.add(Effect.of(EffectType.GRAIN, 0.15f));
                list.add(Effect.of(EffectType.CHROMATIC, 0.12f));
                break;
            }
            case "action-burst": {
                Effect s = Effect.of(EffectType.SPEED_LINES, 0.55f);
                s.setParam("density", 0.6f);
                list.add(s);
                list.add(Effect.of(EffectType.CHROMATIC, 0.3f));
                list.add(Effect.of(EffectType.BLOOM, 0.28f));
                Effect g = Effect.of(EffectType.COLOR_GRADE, 0.4f);
                g.setParam("saturation", 1.15f);
                g.setParam("contrast", 1.12f);
                list.add(g);
                break;
            }
            case "soft-memory": {
                list.add(Effect.of(EffectType.WATERCOLOR, 0.35f));
                list.add(Effect.of(EffectType.BLOOM, 0.45f));
                Effect g = Effect.of(EffectType.COLOR_GRADE, 0.35f);
                g.setParam("warmth", 0.18f);
                g.setParam("saturation", 0.95f);
                list.add(g);
                list.add(Effect.of(EffectType.VIGNETTE, 0.25f));
                break;
            }
            case "modern-remaster": {
                list.add(Effect.of(EffectType.REMASTER, 0.7f));
                list.add(Effect.of(EffectType.SHARPEN, 0.35f));
                Effect g = Effect.of(EffectType.COLOR_GRADE, 0.45f);
                g.setParam("saturation", 1.12f);
                g.setParam("contrast", 1.06f);
                list.add(g);
                list.add(Effect.of(EffectType.VIGNETTE, 0.12f));
                break;
            }
            case "retro-tape": {
                list.add(Effect.of(EffectType.VHS, 0.55f));
                list.add(Effect.of(EffectType.GRAIN, 0.35f));
                Effect g = Effect.of(EffectType.COLOR_GRADE, 0.35f);
                g.setParam("saturation", 1.1f);
                g.setParam("warmth", 0.2f);
                list.add(g);
                break;
            }
            case "ink-wash": {
                list.add(Effect.of(EffectType.HALFTONE, 0.4f));
                list.add(Effect.of(EffectType.WATERCOLOR, 0.5f));
                list.add(Effect.of(EffectType.CEL_OUTLINE, 0.3f));
                Effect g = Effect.of(EffectType.COLOR_GRADE, 0.2f);
                g.setParam("saturation", 0.9f);
                list.add(g);
                break;
            }
            case "noir": {
                Effect g = Effect.of(EffectType.COLOR_GRADE, 0.8f);
                g.setParam("saturation", 0.05f);
                g.setParam("contrast", 1.25f);
                list.add(g);
                list.add(Effect.of(EffectType.GRAIN, 0.4f));
                list.add(Effect.of(EffectType.VIGNETTE, 0.6f));
                break;
            }
            case "dream-bloom": {
                list.add(Effect.of(EffectType.BLOOM, 0.7f));
                list.add(Effect.of(EffectType.CHROMATIC, 0.2f));
                list.add(Effect.of(EffectType.VIGNETTE, 0.2f));
                Effect g = Effect.of(EffectType.COLOR_GRADE, 0.3f);
                g.setParam("warmth", 0.1f);
                list.add(g);
                break;
            }
            case "clean-cel":
            default: {
                Effect g = Effect.of(EffectType.COLOR_GRADE, 0.35f);
                g.setParam("saturation", 1.06f);
                g.setParam("contrast", 1.05f);
                list.add(g);
                list.add(Effect.of(EffectType.CEL_OUTLINE, 0.45f));
                list.add(Effect.of(EffectType.SHARPEN, 0.2f));
                break;
            }
        }
        return list;
    }
}
