package dev.cptgummiball.omacore.core.house;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Chooses where Grandma's house goes in a fresh world: the dry candidate with the flattest ground,
 * ties broken by distance to the world spawn. Pure logic; the adapter samples the heights.
 */
public final class SitePicker {
    /** Ground that varies more than this inside the footprint is not used while flatter land exists. */
    public static final int MAX_PREFERRED_SPREAD = 4;

    private SitePicker() {
    }

    /**
     * @param dx      offset from the spawn (blocks)
     * @param dz      offset from the spawn (blocks)
     * @param surface sampled surface heights (y of the top solid block) inside the footprint
     * @param water   true when any sample is water or lava
     */
    public record Candidate(int dx, int dz, int[] surface, boolean water) {
        public int spread() {
            return Arrays.stream(surface).max().orElse(0) - Arrays.stream(surface).min().orElse(0);
        }

        /** Floor level: the median surface height, so the house neither floats nor sinks too much. */
        public int floorY() {
            int[] s = surface.clone();
            Arrays.sort(s);
            return s[s.length / 2];
        }

        long distanceSq() {
            return (long) dx * dx + (long) dz * dz;
        }
    }

    public static Optional<Candidate> pick(List<Candidate> candidates) {
        Comparator<Candidate> order = Comparator.comparing((Candidate c) -> c.spread() > MAX_PREFERRED_SPREAD)
                .thenComparingInt(Candidate::spread)
                .thenComparingLong(Candidate::distanceSq);
        return candidates.stream().filter(c -> !c.water() && c.surface().length > 0).min(order);
    }
}
