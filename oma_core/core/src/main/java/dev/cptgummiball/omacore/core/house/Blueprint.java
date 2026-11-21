package dev.cptgummiball.omacore.core.house;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A parsed building plan: block-state strings per cell plus named markers. Cells hold either a palette
 * character, {@link #AIR} or {@link #KEEP}. Coordinates: x west->east, y up, z north->south.
 */
public final class Blueprint {
    public static final char AIR = '.';
    public static final char KEEP = '_';
    public static final String AIR_STATE = "minecraft:air";

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final Map<Character, String> palette;
    private final char[][][] cells; // [y][z][x]
    private final Map<String, Marker> markers;

    Blueprint(int sizeX, int sizeY, int sizeZ, Map<Character, String> palette, char[][][] cells,
              Map<String, Marker> markers) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = Map.copyOf(palette);
        this.cells = cells;
        this.markers = Map.copyOf(markers);
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public Map<Character, String> palette() {
        return palette;
    }

    public Map<String, Marker> markers() {
        return markers;
    }

    public Optional<Marker> marker(String name) {
        return Optional.ofNullable(markers.get(name));
    }

    /** The block state to place, or empty for {@link #KEEP}. */
    public Optional<String> stateAt(int x, int y, int z) {
        char c = cells[y][z][x];
        if (c == KEEP) {
            return Optional.empty();
        }
        return Optional.of(c == AIR ? AIR_STATE : palette.get(c));
    }

    /**
     * A palette entry may list fallbacks separated by {@code |} (for example a furniture mod's block, then a vanilla
     * block); the first one the running game knows is placed. Returns the alternatives in order.
     */
    public static List<String> alternatives(String state) {
        List<String> out = new ArrayList<>();
        for (String s : state.split("\\|")) {
            if (!s.isBlank()) {
                out.add(s.strip());
            }
        }
        return List.copyOf(out);
    }

    /**
     * True when the cell holds one of the house's own blocks (not air, not keep). Coordinates are relative to the
     * plan's origin; anything outside the bounds is false. Used to protect the house without touching the free space
     * inside it, so players can still place and remove their own things there.
     */
    public boolean isStructure(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            return false;
        }
        char c = cells[y][z][x];
        return c != AIR && c != KEEP;
    }

    /** True when the cell is part of the bottom layer and not air/keep, i.e. needs ground below it. */
    public boolean isFoundation(int x, int z) {
        char c = cells[0][z][x];
        return c != AIR && c != KEEP;
    }

    /** A named point inside the plan with a facing (yaw: 0 = south, 180 = north). */
    public record Marker(int x, int y, int z, float yaw) {
    }
}
