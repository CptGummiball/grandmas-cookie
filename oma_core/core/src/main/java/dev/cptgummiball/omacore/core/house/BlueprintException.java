package dev.cptgummiball.omacore.core.house;

/** A blueprint that cannot be parsed; carries the 1-based line number. */
public final class BlueprintException extends Exception {
    private final int line;

    public BlueprintException(int line, String message) {
        super("line " + line + ": " + message);
        this.line = line;
    }

    public int line() {
        return line;
    }
}
