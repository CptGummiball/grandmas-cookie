package dev.cptgummiball.omacore.core.grandma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GrandmaCensusTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void spawnsOnlyWhenNoneIsFound() {
        assertEquals(new GrandmaCensus.Decision(Optional.empty(), List.of(), true), GrandmaCensus.decide(a, List.of()));
        assertFalse(GrandmaCensus.decide(a, List.of(a)).spawn());
    }

    @Test
    void keepsTheStoredOneAndRemovesDuplicates() {
        GrandmaCensus.Decision d = GrandmaCensus.decide(b, List.of(a, b, c));
        assertEquals(Optional.of(b), d.keep());
        assertEquals(List.of(a, c), d.discard());
    }

    @Test
    void adoptsALoneUnknownGrandma() {
        GrandmaCensus.Decision d = GrandmaCensus.decide(null, List.of(c));
        assertEquals(Optional.of(c), d.keep());
        assertTrue(d.discard().isEmpty());
        assertEquals(Optional.of(a), GrandmaCensus.decide(b, List.of(a)).keep()); // stored one gone, a replacement exists
    }

    @Test
    void restoreLimiterStopsEndlessRespawns() {
        GrandmaCensus.RestoreLimiter limiter = new GrandmaCensus.RestoreLimiter(3, 12000);
        assertTrue(limiter.tryRestore(0));
        assertTrue(limiter.tryRestore(100));
        assertTrue(limiter.tryRestore(200));
        assertFalse(limiter.tryRestore(300));
        assertTrue(limiter.tryRestore(12000)); // first one left the window
    }
}
