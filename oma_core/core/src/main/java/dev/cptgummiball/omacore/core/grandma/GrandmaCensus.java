package dev.cptgummiball.omacore.core.grandma;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides what to do with the Grandma entities found around her spot (OMA_CORE_SPEC "NPC protections"):
 * keep the stored one, adopt a lone unknown one, remove duplicates, or spawn her when none exists.
 * Only called when her chunk's entities are loaded, so "none found" really means "missing".
 */
public final class GrandmaCensus {
    private GrandmaCensus() {
    }

    public record Decision(Optional<UUID> keep, List<UUID> discard, boolean spawn) {
    }

    public static Decision decide(UUID stored, List<UUID> found) {
        if (found.isEmpty()) {
            return new Decision(Optional.empty(), List.of(), true);
        }
        UUID keep = stored != null && found.contains(stored) ? stored : found.get(0);
        List<UUID> discard = new ArrayList<>(found);
        discard.remove(keep);
        return new Decision(Optional.of(keep), List.copyOf(discard), false);
    }

    /** Allows at most {@code max} restores per {@code windowTicks}; stops silent endless respawning. */
    public static final class RestoreLimiter {
        private final int max;
        private final long windowTicks;
        private final List<Long> restores = new ArrayList<>();

        public RestoreLimiter(int max, long windowTicks) {
            this.max = max;
            this.windowTicks = windowTicks;
        }

        public boolean tryRestore(long now) {
            restores.removeIf(t -> now - t >= windowTicks);
            if (restores.size() >= max) {
                return false;
            }
            restores.add(now);
            return true;
        }
    }
}
