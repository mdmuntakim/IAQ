package app.bxanimator.core;

import java.util.ArrayList;
import java.util.List;

/** Evaluates a scene at a time: layer transforms, rig poses and the camera. */
public final class SceneSampler {

    private SceneSampler() {
    }

    public static final class SampledLayer {
        public final Layer layer;
        public float x;
        public float y;
        public float scale = 1f;
        public float rotation;
        public float opacity = 1f;
        public Pose pose;
        public float blink;
        public float brow;
        public float mouth;
        public float look;

        SampledLayer(Layer layer) {
            this.layer = layer;
        }
    }

    public static final class SampledScene {
        public final Scene scene;
        public final List<SampledLayer> layers = new ArrayList<>();
        public float camX;
        public float camY;
        public float camScale = 1f;
        public float camRotation;
        public float time;

        SampledScene(Scene scene) {
            this.scene = scene;
        }

        public SampledLayer find(String layerId) {
            for (SampledLayer l : layers) {
                if (l.layer.id.equals(layerId)) return l;
            }
            return null;
        }
    }

    public static SampledScene sample(Scene scene, double time) {
        SampledScene out = new SampledScene(scene);
        out.time = (float) time;
        Layer cam = scene.camera();
        if (cam != null) {
            out.camX = cam.x(time);
            out.camY = cam.y(time);
            out.camScale = Math.max(0.05f, cam.scale(time));
            out.camRotation = cam.rotation(time);
        }
        for (Layer layer : scene.layers) {
            if (layer.type == LayerType.CAMERA) continue;
            SampledLayer sl = new SampledLayer(layer);
            sl.x = layer.x(time);
            sl.y = layer.y(time);
            sl.scale = layer.scale(time);
            sl.rotation = layer.rotation(time);
            sl.opacity = layer.opacity(time);
            if (layer.type == LayerType.RIG && layer.rig != null) {
                sl.pose = PoseSolver.solve(layer.rig, layer, time);
                sl.blink = layer.sample(Layer.FACE_BLINK, time, 0f);
                sl.brow = layer.sample(Layer.FACE_BROW, time, 0f);
                sl.mouth = layer.sample(Layer.FACE_MOUTH, time, 0f);
                sl.look = layer.sample(Layer.FACE_LOOK, time, 0f);
            }
            out.layers.add(sl);
        }
        return out;
    }
}
