package org.voxelhorizons.pack;

/**
 * Human-readable progress for long pack builds, so server admins can tell a slow build
 * from a stalled one. Messages are throttled: a counted step reports at every 10% and at
 * least every 5 seconds while it advances. Implementations must be thread-safe.
 */
public interface BuildProgress {
    BuildProgress NONE = message -> { };

    void report(String message);

    /**
     * Cancellation checkpoint, called for every counted unit (item, zip entry, download chunk)
     * as well as before each report. A stoppable build's sink throws {@link Cancelled}; the
     * compilers only write their output at the end, so an interrupted build leaves the previous
     * result in place.
     */
    default void checkCancelled() { }

    /** Thrown by a sink to stop the build at the current checkpoint. */
    final class Cancelled extends RuntimeException {
        public Cancelled(String message) { super(message); }
    }

    /** Starts a counted step; {@code total <= 0} means unknown (bytes are then shown, not %). */
    default Step step(String label, long total) {
        return new Step(this, label, total, false, System.nanoTime());
    }

    /** Like {@link #step} but counts bytes and shows megabytes. */
    default Step bytes(String label, long total) {
        return new Step(this, label, total, true, System.nanoTime());
    }

    final class Step {
        private static final long INTERVAL_NANOS = 5_000_000_000L;
        private final BuildProgress sink;
        private final String label;
        private final long total, started;
        private final boolean bytes;
        private long done, lastReport;
        private int lastDecile = 0;

        Step(BuildProgress sink, String label, long total, boolean bytes, long now) {
            this.sink = sink; this.label = label; this.total = total; this.bytes = bytes;
            this.started = now; this.lastReport = now;
        }

        public synchronized void advance(long amount) { advance(amount, System.nanoTime()); }

        synchronized void advance(long amount, long now) {
            sink.checkCancelled();
            done += amount;
            int decile = total > 0 ? (int) Math.min(10, done * 10 / total) : -1;
            if ((total > 0 && decile > lastDecile && decile < 10) || now - lastReport >= INTERVAL_NANOS) {
                lastDecile = Math.max(lastDecile, decile);
                lastReport = now;
                sink.report(label + ": " + describe());
            }
        }

        /** Reports completion with elapsed time. */
        public synchronized void finish(String summary) { finish(summary, System.nanoTime()); }

        synchronized void finish(String summary, long now) {
            sink.report(label + ": done in " + String.format(java.util.Locale.ROOT, "%.1f", (now - started) / 1e9) + "s"
                    + (summary == null || summary.isEmpty() ? "" : " (" + summary + ")"));
        }

        String describe() {
            String count = bytes ? megabytes(done) : String.valueOf(done);
            if (total <= 0) return count + " so far";
            return (done * 100 / total) + "% (" + count + " of " + (bytes ? megabytes(total) : String.valueOf(total)) + ")";
        }
    }

    /** Megabytes with one decimal, for download messages. */
    static String megabytes(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1_048_576.0);
    }
}
