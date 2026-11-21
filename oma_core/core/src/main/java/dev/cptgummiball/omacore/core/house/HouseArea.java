package dev.cptgummiball.omacore.core.house;

/**
 * The house's box in the overworld plus a margin. A player who logs out inside it is "at home", so
 * their saved work place is kept instead of being overwritten with the house.
 */
public record HouseArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static final int MARGIN = 3;

    public static HouseArea of(int originX, int originY, int originZ, int sizeX, int sizeY, int sizeZ) {
        return new HouseArea(originX - MARGIN, originY - MARGIN, originZ - MARGIN,
                originX + sizeX - 1 + MARGIN, originY + sizeY - 1 + MARGIN, originZ + sizeZ - 1 + MARGIN);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
