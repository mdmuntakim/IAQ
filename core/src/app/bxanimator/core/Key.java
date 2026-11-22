package app.bxanimator.core;

/** One keyframe: a time in seconds, a value and the curve that leaves this key. */
public final class Key implements Comparable<Key> {

    public double time;
    public float value;
    public Easing ease;

    public Key(double time, float value, Easing ease) {
        this.time = time;
        this.value = value;
        this.ease = ease == null ? Easing.LINEAR : ease;
    }

    public Key copy() {
        return new Key(time, value, ease);
    }

    @Override
    public int compareTo(Key other) {
        return Double.compare(time, other.time);
    }

    @Override
    public String toString() {
        return "Key{" + time + "=" + value + " " + ease + '}';
    }
}
