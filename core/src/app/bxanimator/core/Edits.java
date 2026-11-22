package app.bxanimator.core;

/** Editing operations shared by the canvas, the inspector and the timeline. */
public final class Edits {

    private Edits() {
    }

    public static float defaultValue(Layer layer, String channel) {
        if (channel == null) return 0f;
        switch (channel) {
            case Layer.SCALE:
                return 1f;
            case Layer.OPACITY:
                return 1f;
            case Layer.FACE_BLINK:
            case Layer.FACE_MOUTH:
                return 0f;
            case Layer.X:
                return 0f;
            case Layer.Y:
                return 0f;
            case Layer.ROT:
                return 0f;
            default:
                break;
        }
        if (channel.startsWith("b.") && channel.endsWith(".rot") && layer.rig != null) {
            Bone bone = layer.rig.bone(channel.substring(2, channel.length() - 4));
            if (bone != null) return bone.restAngle;
        }
        return 0f;
    }

    /**
     * Writes a channel value at the playhead. With auto key on a key is created;
     * with auto key off an animated channel is offset as a whole, which is the
     * quickest way to retime a whole performance.
     */
    public static void setChannel(Layer layer, String channel, double time, float value, boolean autoKey) {
        if (layer == null || channel == null) return;
        if (autoKey) {
            layer.setKey(channel, time, value, Easing.LINEAR);
            return;
        }
        Track track = layer.trackOrNull(channel);
        if (track == null || track.isEmpty()) {
            layer.setKey(channel, 0, value, Easing.LINEAR);
            return;
        }
        float current = track.sample(time, value);
        track.offsetValues(value - current);
    }

    /** Adds a key at the time or removes the one already there. */
    public static boolean toggleKey(Layer layer, String channel, double time) {
        if (layer == null || channel == null) return false;
        Track track = layer.track(channel);
        if (track.keyAt(time) != null) {
            track.removeAt(time);
            return false;
        }
        track.put(time, layer.sample(channel, time, defaultValue(layer, channel)), Easing.LINEAR);
        return true;
    }

    public static boolean hasKeyAt(Layer layer, String channel, double time) {
        if (layer == null || channel == null) return false;
        Track track = layer.channels.get(channel);
        return track != null && track.keyAt(time) != null;
    }

    /** Keys every bone of a rig with its current pose. */
    public static int keyFullPose(Layer layer, double time) {
        if (layer == null || layer.rig == null) return 0;
        int count = 0;
        for (Bone bone : layer.rig.bones) {
            String channel = Layer.boneChannel(bone.id);
            layer.setKey(channel, time, layer.boneAngle(bone, time), Easing.LINEAR);
            count++;
        }
        for (String face : new String[]{Layer.FACE_BLINK, Layer.FACE_BROW, Layer.FACE_MOUTH, Layer.FACE_LOOK}) {
            layer.setKey(face, time, layer.sample(face, time, 0f), Easing.LINEAR);
            count++;
        }
        return count;
    }

    /** Resets every bone channel to the rest pose. */
    public static void resetPose(Layer layer) {
        if (layer == null || layer.rig == null) return;
        for (Bone bone : layer.rig.bones) {
            layer.channels.remove(Layer.boneChannel(bone.id));
        }
        layer.channels.remove(Layer.FACE_BLINK);
        layer.channels.remove(Layer.FACE_BROW);
        layer.channels.remove(Layer.FACE_MOUTH);
        layer.channels.remove(Layer.FACE_LOOK);
    }

    /** Shifts every key of a layer in time. */
    public static void shiftLayer(Layer layer, double delta) {
        if (layer == null) return;
        for (Track t : layer.channels.values()) t.shift(delta);
    }
}
