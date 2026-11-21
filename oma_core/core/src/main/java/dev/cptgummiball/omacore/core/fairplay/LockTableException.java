package dev.cptgummiball.omacore.core.fairplay;

/** A lock table line that cannot be parsed; carries the 1-based line number. */
public final class LockTableException extends Exception {
    private final int line;

    public LockTableException(int line, String message) {
        super("line " + line + ": " + message);
        this.line = line;
    }

    public int line() {
        return line;
    }
}
