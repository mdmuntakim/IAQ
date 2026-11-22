package app.bxanimator.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An animation curve for one channel of one layer. Keys are kept sorted by time
 * and duplicate times collapse onto one key, exactly like a professional
 * timeline behaves.
 */
public final class Track {

    public static final double EPS = 1e-4;
    private static final double QUANTUM = 10000.0;

    public final List<Key> keys = new ArrayList<>();

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public int size() {
        return keys.size();
    }

    public Key get(int index) {
        return keys.get(index);
    }

    /** Inserts or replaces the key at {@code time}. */
    public Key put(double time, float value, Easing ease) {
        time = quantize(time);
        int existing = indexAt(time);
        if (existing >= 0) {
            Key k = keys.get(existing);
            k.value = value;
            if (ease != null) k.ease = ease;
            return k;
        }
        Key key = new Key(time, value, ease);
        keys.add(key);
        Collections.sort(keys);
        return key;
    }

    public Key put(double time, float value) {
        return put(time, value, null);
    }

    public int indexAt(double time) {
        for (int i = 0; i < keys.size(); i++) {
            if (Math.abs(keys.get(i).time - time) <= EPS) return i;
        }
        return -1;
    }

    public Key keyAt(double time) {
        int i = indexAt(time);
        return i < 0 ? null : keys.get(i);
    }

    public boolean removeAt(double time) {
        int i = indexAt(time);
        if (i < 0) return false;
        keys.remove(i);
        return true;
    }

    /** Removes the key closest to {@code time} within {@code tolerance}. */
    public boolean removeNear(double time, double tolerance) {
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < keys.size(); i++) {
            double d = Math.abs(keys.get(i).time - time);
            if (d <= tolerance && d < bestDist) {
                best = i;
                bestDist = d;
            }
        }
        if (best < 0) return false;
        keys.remove(best);
        return true;
    }

    /** Moves a key, merging it with a key that already sits on the target time. */
    public void move(int index, double newTime) {
        if (index < 0 || index >= keys.size()) return;
        newTime = quantize(Math.max(0, newTime));
        Key key = keys.get(index);
        int target = indexAt(newTime);
        if (target >= 0 && target != index) {
            keys.get(target).value = key.value;
            keys.get(target).ease = key.ease;
            keys.remove(index);
            return;
        }
        key.time = newTime;
        Collections.sort(keys);
    }

    public float sample(double time, float fallback) {
        int n = keys.size();
        if (n == 0) return fallback;
        if (time <= keys.get(0).time) return keys.get(0).value;
        if (time >= keys.get(n - 1).time) return keys.get(n - 1).value;
        int i = 0;
        while (i < n - 1 && keys.get(i + 1).time <= time) i++;
        Key a = keys.get(i);
        Key b = keys.get(i + 1);
        double span = b.time - a.time;
        if (span <= EPS) return b.value;
        float u = (float) ((time - a.time) / span);
        float e = a.ease.apply(u);
        return AnimMath.lerp(a.value, b.value, e);
    }

    /** True when the curve has keys and the value changes over time. */
    public boolean isAnimated() {
        if (keys.size() < 2) return false;
        float first = keys.get(0).value;
        for (Key k : keys) {
            if (Math.abs(k.value - first) > 1e-5f) return true;
        }
        return false;
    }

    public double firstTime() {
        return keys.isEmpty() ? 0 : keys.get(0).time;
    }

    public double lastTime() {
        return keys.isEmpty() ? 0 : keys.get(keys.size() - 1).time;
    }

    public Set<Double> times() {
        Set<Double> set = new LinkedHashSet<>();
        for (Key k : keys) set.add(k.time);
        return set;
    }

    public Track copy() {
        Track t = new Track();
        for (Key k : keys) t.keys.add(k.copy());
        return t;
    }

    public void shift(double dt) {
        for (Key k : keys) k.time = quantize(Math.max(0, k.time + dt));
        Collections.sort(keys);
    }

    /** Scales every key time around {@code pivot} by {@code factor}. */
    public void scaleTimes(double pivot, double factor) {
        for (Key k : keys) k.time = quantize(Math.max(0, pivot + (k.time - pivot) * factor));
        Collections.sort(keys);
    }

    public void clear() {
        keys.clear();
    }

    /** Offsets every key value, keeping the timing (whole animation nudge). */
    public void offsetValues(float delta) {
        for (Key k : keys) k.value += delta;
    }

    /** Keeps key times stable across a JSON round trip (four decimal places). */
    public static double quantize(double time) {
        return Math.round(time * QUANTUM) / QUANTUM;
    }
}
