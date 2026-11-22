package iaq;

import java.util.stream.IntStream;

/**
 * Row level parallelism for the image pipeline.
 *
 * <p>Every stage works on whole scan lines, so a plain parallel stream over the
 * row range gives near linear scaling on four cores without any locking.  The
 * common fork-join pool is used on purpose: it is initialised once and reused
 * for the whole run, which keeps the per-stage overhead well below a
 * millisecond.</p>
 */
public final class Parallel {

    private Parallel() {
    }

    /** Runs {@code body} for every row, in parallel. */
    public static void rows(int height, RowTask body) {
        if (height <= 0) return;
        IntStream.range(0, height).parallel().forEach(body::row);
    }

    /** Runs {@code body} for every index in {@code [0, count)}, in parallel. */
    public static void forEach(int count, IndexTask body) {
        if (count <= 0) return;
        IntStream.range(0, count).parallel().forEach(body::index);
    }

    /** Sets the worker count before the common pool is created. */
    public static void configure(int threads) {
        int n = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
        if (n < 1) n = 1;
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", Integer.toString(n));
    }

    @FunctionalInterface
    public interface RowTask {
        void row(int y);
    }

    @FunctionalInterface
    public interface IndexTask {
        void index(int i);
    }
}
