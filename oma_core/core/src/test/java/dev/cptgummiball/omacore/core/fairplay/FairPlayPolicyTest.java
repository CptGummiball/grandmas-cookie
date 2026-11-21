package dev.cptgummiball.omacore.core.fairplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FairPlayPolicyTest {
    @Test
    void singleplayerWithoutCheatsAlwaysEnforcesEvenIfDedicatedSwitchIsOff() {
        assertTrue(FairPlayPolicy.enforceLockedValues(true, false, false));
        assertFalse(FairPlayPolicy.enforceLockedValues(true, true, true)); // world created with cheats
        assertTrue(FairPlayPolicy.enforceLockedValues(false, false, true));
        assertFalse(FairPlayPolicy.enforceLockedValues(false, false, false)); // operator opted out
    }

    @Test
    void lanCheatsAreRevokedOnlyInCheatFreeSingleplayerWorlds() {
        assertTrue(FairPlayPolicy.mustRevokeLanCommands(true, false, true));
        assertFalse(FairPlayPolicy.mustRevokeLanCommands(true, false, false));
        assertFalse(FairPlayPolicy.mustRevokeLanCommands(true, true, true));
        assertFalse(FairPlayPolicy.mustRevokeLanCommands(false, false, true));
    }

    @Test
    void operatorLevelIsTwo() {
        assertFalse(FairPlayPolicy.isPrivileged(0));
        assertFalse(FairPlayPolicy.isPrivileged(1));
        assertTrue(FairPlayPolicy.isPrivileged(2));
        assertTrue(FairPlayPolicy.isPrivileged(4));
    }

    @Test
    void parsesTableAndReportsOnlyRealViolations() throws LockTableException {
        LockTable table = LockTable.parse("""
                # comment
                ftbultimine SERVER max_blocks = 64

                somemod common general.enabled = true
                somemod SERVER general.rate = 1.5
                ghost SERVER x = 1
                """);
        assertEquals(4, table.size());
        Map<String, String> current = Map.of(
                "ftbultimine SERVER max_blocks", "64.0",   // same number
                "somemod COMMON general.enabled", "TRUE",  // same boolean
                "somemod SERVER general.rate", "9");       // changed by the player
        List<LockRule> v = FairPlayPolicy.violations(table, r -> Optional.ofNullable(current.get(r.key())));
        assertEquals(List.of("somemod SERVER general.rate"), v.stream().map(LockRule::key).toList());
    }

    @Test
    void rejectsMalformedSideAndDuplicates() {
        assertEquals(1, assertThrows(LockTableException.class, () -> LockTable.parse("nonsense")).line());
        assertEquals(2, assertThrows(LockTableException.class,
                () -> LockTable.parse("# x\na CLIENT b = 1")).line());
        assertEquals(2, assertThrows(LockTableException.class,
                () -> LockTable.parse("a SERVER b = 1\na server b = 2")).line());
        assertEquals(1, assertThrows(LockTableException.class, () -> LockTable.parse("a SERVER b = ")).line());
    }

    @Test
    void modDefaultStartupAndSpacedPathsParse() throws LockTableException {
        LockTable table = LockTable.parse("""
                modularrouters COMMON Energy Costs.breakerModuleEnergyCost = @default
                create_dragons_plus COMMON features.fluid.fluid/dye = @default
                modern_industrialization STARTUP debugCommands = false
                """);
        assertEquals(3, table.size());
        LockRule spaced = table.rules().get(0);
        assertEquals("Energy Costs.breakerModuleEnergyCost", spaced.path());
        assertTrue(spaced.usesModDefault());
        assertEquals("features.fluid.fluid/dye", table.rules().get(1).path());
        assertEquals(ConfigSide.STARTUP, table.rules().get(2).side());
        assertFalse(table.rules().get(2).usesModDefault());
        // default-locked rules are left to the adapter, which knows the entry's default object
        assertEquals(List.of(), FairPlayPolicy.violations(table, r -> Optional.of("changed"))
                .stream().filter(LockRule::usesModDefault).toList());
    }

    @Test
    void textValuesAreComparedExactly() {
        assertTrue(FairPlayPolicy.sameValue("NORMAL", " NORMAL "));
        assertFalse(FairPlayPolicy.sameValue("NORMAL", "normal"));
        assertFalse(FairPlayPolicy.sameValue("1", "true"));
    }
}
