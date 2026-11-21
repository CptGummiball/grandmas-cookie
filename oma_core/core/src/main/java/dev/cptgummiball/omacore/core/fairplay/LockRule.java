package dev.cptgummiball.omacore.core.fairplay;

import java.util.Objects;

/**
 * One pack-locked config value (ADR-0005): {@code modId}'s {@code side} config entry at the dotted
 * {@code path} must equal {@code value} while the fair-play guard is active. The value
 * {@value #MOD_DEFAULT} locks the entry to the mod's own default, whatever its type (lists included).
 */
public record LockRule(String modId, ConfigSide side, String path, String value) {
    public static final String MOD_DEFAULT = "@default";

    public LockRule {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(value, "value");
    }

    /** True when the entry is locked to the mod's default instead of an explicit value. */
    public boolean usesModDefault() {
        return MOD_DEFAULT.equals(value);
    }

    /** Stable identity of the locked entry (one rule per entry). */
    public String key() {
        return modId + " " + side + " " + path;
    }
}
