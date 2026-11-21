package dev.cptgummiball.omacore.core.fairplay;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Decisions of the fair-play guard (ADR-0005), free of Minecraft types so they can be unit tested.
 */
public final class FairPlayPolicy {
    /** Vanilla operator level needed for cheat commands such as /give and /gamemode. */
    public static final int OPERATOR_LEVEL = 2;

    private FairPlayPolicy() {
    }

    /**
     * Whether locked config values are enforced on this logical server.
     * Singleplayer: always when the world was created without cheats (the owner may not opt out).
     * Dedicated server: unless the server operator disabled enforcement in the guard's own file.
     */
    public static boolean enforceLockedValues(boolean singleplayer, boolean worldAllowsCommands,
                                              boolean dedicatedEnforcementEnabled) {
        return singleplayer ? !worldAllowsCommands : dedicatedEnforcementEnabled;
    }

    /**
     * "Open to LAN" with "Allow Cheats" must not grant commands in a singleplayer world that was
     * created without cheats.
     */
    public static boolean mustRevokeLanCommands(boolean singleplayer, boolean worldAllowsCommands,
                                                boolean commandsForAllPlayers) {
        return singleplayer && !worldAllowsCommands && commandsForAllPlayers;
    }

    /** True when a player may use cheat paths at all (operator level or higher). */
    public static boolean isPrivileged(int permissionLevel) {
        return permissionLevel >= OPERATOR_LEVEL;
    }

    /**
     * Rules with an explicit value whose current value differs from it. A missing entry (mod absent or
     * path unknown) is not a violation: the adapter logs it as a table problem instead. Rules locked to
     * the mod default ({@link LockRule#usesModDefault()}) need the entry's default object and are compared
     * by the adapter.
     */
    public static List<LockRule> violations(LockTable table, Function<LockRule, Optional<String>> currentValue) {
        List<LockRule> out = new ArrayList<>();
        for (LockRule rule : table.rules()) {
            if (rule.usesModDefault()) {
                continue;
            }
            Optional<String> current = currentValue.apply(rule);
            if (current.isPresent() && !sameValue(current.get(), rule.value())) {
                out.add(rule);
            }
        }
        return out;
    }

    /** Compares config values the way a player would read them: 64 == 64.0, TRUE == true. */
    public static boolean sameValue(String a, String b) {
        String x = a.strip();
        String y = b.strip();
        if (x.equals(y)) {
            return true;
        }
        BigDecimal dx = number(x);
        BigDecimal dy = number(y);
        if (dx != null && dy != null) {
            return dx.compareTo(dy) == 0;
        }
        return isBoolean(x) && isBoolean(y) && x.toLowerCase(Locale.ROOT).equals(y.toLowerCase(Locale.ROOT));
    }

    private static BigDecimal number(String s) {
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBoolean(String s) {
        return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false");
    }
}
