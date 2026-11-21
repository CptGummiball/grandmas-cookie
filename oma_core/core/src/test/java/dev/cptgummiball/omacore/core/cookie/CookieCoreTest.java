package dev.cptgummiball.omacore.core.cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CookieCoreTest {
    private final UUID teamA = UUID.randomUUID();
    private final UUID teamB = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void firstDeliveryOfATeamWinsEveryLaterOneIsRefused() {
        CookieLedger ledger = new CookieLedger();
        assertTrue(ledger.tryDeliver(teamA, alice, 100));
        assertFalse(ledger.tryDeliver(teamA, bob, 101));   // teammate clicks a moment later
        assertFalse(ledger.tryDeliver(teamA, alice, 500)); // reconnect, second try
        assertEquals(alice, ledger.delivery(teamA).orElseThrow().player());
        assertTrue(ledger.tryDeliver(teamB, bob, 102));    // other team is independent
        assertEquals(2, ledger.view().size());
    }

    @Test
    void restoreKeepsTheFirstEntry() {
        CookieLedger ledger = new CookieLedger();
        ledger.restore(teamA, new CookieLedger.Delivery(alice, 1));
        ledger.restore(teamA, new CookieLedger.Delivery(bob, 2));
        assertEquals(alice, ledger.delivery(teamA).orElseThrow().player());
        assertFalse(ledger.tryDeliver(teamA, bob, 3));
    }

    @Test
    void onlyThePackCookieIsAcceptedAndOnlyOncePerTeam() {
        assertEquals(CookieRules.Outcome.ACCEPT, CookieRules.decide(false, true, true));
        assertEquals(CookieRules.Outcome.ALREADY_DELIVERED, CookieRules.decide(true, true, false));
        assertEquals(CookieRules.Outcome.VANILLA_COOKIE, CookieRules.decide(false, false, true));
        assertEquals(CookieRules.Outcome.NO_COOKIE, CookieRules.decide(false, false, false));
    }
}
