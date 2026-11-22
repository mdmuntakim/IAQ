package app.bxanimator.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Evaluates a rig into world space and hit tests bones for direct posing. */
public final class PoseSolver {

    private PoseSolver() {
    }

    public static Mat2D layerMatrix(Layer layer, double time) {
        return layer.transform(time);
    }

    public static Pose solve(Rig rig, Layer layer, double time) {
        Map<String, Float> angles = new HashMap<>();
        if (rig != null) {
            for (Bone b : rig.bones) {
                angles.put(b.id, layer.boneAngle(b, time));
            }
        }
        return solve(rig, angles, layerMatrix(layer, time));
    }

    public static Pose solve(Rig rig, Map<String, Float> angles, Mat2D base) {
        Pose pose = new Pose();
        if (rig == null) return pose;
        for (Bone b : rig.bones) {
            pose.bones.put(b.id, b);
            Float a = angles.get(b.id);
            pose.angles.put(b.id, a == null ? b.restAngle : a);
        }
        Set<String> visiting = new HashSet<>();
        for (Bone b : rig.bones) {
            worldOf(b, rig, pose, base, visiting);
        }
        return pose;
    }

    private static Mat2D worldOf(Bone bone, Rig rig, Pose pose, Mat2D base, Set<String> visiting) {
        Mat2D cached = pose.world.get(bone.id);
        if (cached != null) return cached;
        if (!visiting.add(bone.id)) {
            // Broken parent cycle: treat the bone as a root instead of hanging.
            Mat2D m = base.copy();
            m.mul(Mat2D.translate(bone.pivotX, bone.pivotY));
            m.mul(Mat2D.rotate(pose.angle(bone.id)));
            pose.world.put(bone.id, m);
            return m;
        }
        Mat2D parent = base;
        if (bone.parent != null) {
            Bone parentBone = rig.bone(bone.parent);
            if (parentBone != null) {
                parent = worldOf(parentBone, rig, pose, base, visiting);
            }
        }
        Mat2D m = parent.copy();
        m.mul(Mat2D.translate(bone.pivotX, bone.pivotY));
        m.mul(Mat2D.rotate(pose.angle(bone.id)));
        pose.world.put(bone.id, m);
        visiting.remove(bone.id);
        return m;
    }

    /**
     * Finds the bone under a point in layer space. The tolerance widens the hit
     * area in layer units; the best (closest relative to its width) wins.
     */
    public static Bone pick(Rig rig, Pose pose, float x, float y, float tolerance) {
        if (rig == null) return null;
        Bone best = null;
        float bestScore = Float.MAX_VALUE;
        for (Bone b : rig.bones) {
            Mat2D m = pose.of(b.id);
            float x0 = m.tx;
            float y0 = m.ty;
            float x1 = m.mapX(0, -b.length);
            float y1 = m.mapY(0, -b.length);
            float dist = distanceToSegment(x, y, x0, y0, x1, y1);
            float radius = Math.max(b.width * 0.5f, 16f) + Math.max(0f, tolerance);
            if (dist <= radius) {
                float score = dist / Math.max(1f, radius);
                if (score < bestScore) {
                    bestScore = score;
                    best = b;
                }
            }
        }
        return best;
    }

    public static float distanceToSegment(float px, float py, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len2 = dx * dx + dy * dy;
        if (len2 < 1e-6f) return (float) Math.hypot(px - x0, py - y0);
        float t = ((px - x0) * dx + (py - y0) * dy) / len2;
        t = AnimMath.clamp(t, 0f, 1f);
        float cx = x0 + t * dx;
        float cy = y0 + t * dy;
        return (float) Math.hypot(px - cx, py - cy);
    }

    /** Angle of the bone under the pointer, used by the pose tool. */
    public static float pointerAngle(Bone bone, Pose pose, float x, float y) {
        Mat2D m = pose.of(bone.id);
        float dx = x - m.tx;
        float dy = y - m.ty;
        return AnimMath.radToDeg((float) Math.atan2(dx, -dy));
    }

    /** World position of the tip of a bone, handy for gizmos and IK helpers. */
    public static float[] tip(Bone bone, Pose pose) {
        Mat2D m = pose.of(bone.id);
        return new float[]{m.mapX(0, -bone.length), m.mapY(0, -bone.length)};
    }
}
