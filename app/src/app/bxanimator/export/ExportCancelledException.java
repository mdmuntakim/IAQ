package app.bxanimator.export;

/** Thrown when the user cancels a long export. */
public final class ExportCancelledException extends Exception {

    public ExportCancelledException() {
        super("Export cancelled");
    }
}
