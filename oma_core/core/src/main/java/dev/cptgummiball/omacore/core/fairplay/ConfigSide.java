package dev.cptgummiball.omacore.core.fairplay;

/**
 * Config files that can change gameplay on the logical server (client configs are never locked).
 * STARTUP configs are read once when the game starts; a reset there takes effect from the next start.
 */
public enum ConfigSide {
    COMMON,
    SERVER,
    STARTUP
}
