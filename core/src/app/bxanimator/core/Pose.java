package app.bxanimator.core;

import java.util.Map;

/**
 * The result of evaluating a rig at one point in time: the world matrix and the
 * local angle of every bone, in layer space.
 */
public final class Pose {

    public final Map<String, Mat2D> world = new java.util.LinkedHashMap<>();
    public final Map<String, Float> angles = new java.util.LinkedHashMap<>();
    public final Map<String, Bone> bones = new java.util.LinkedHashMap<>();

    public Mat2D of(String boneId) {
        Mat2D m = world.get(boneId);
        return m == null ? Mat2D.identity() : m;
    }

    public float angle(String boneId) {
        Float f = angles.get(boneId);
        return f == null ? 0f : f;
    }

    public Bone bone(String boneId) {
        return bones.get(boneId);
    }
}
