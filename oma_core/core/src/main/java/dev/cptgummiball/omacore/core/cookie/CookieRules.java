package dev.cptgummiball.omacore.core.cookie;

/**
 * What Grandma does when a player offers her a cookie (OMA_CORE_SPEC "Interaction protocol"). Only the
 * exact pack cookie counts; a vanilla cookie gets a funny refusal; a team delivers once.
 */
public final class CookieRules {
    public static final String GRANDMAS_COOKIE = "kubejs:grandmas_cookie";
    public static final String VANILLA_COOKIE = "minecraft:cookie";

    private CookieRules() {
    }

    public enum Outcome {
        /** Take exactly one Grandma's cookie and record the team's delivery. */
        ACCEPT,
        /** The team already delivered; nothing is taken. */
        ALREADY_DELIVERED,
        /** Only a vanilla cookie: refused with a joke, nothing is taken. */
        VANILLA_COOKIE,
        /** No cookie at all. */
        NO_COOKIE
    }

    public static Outcome decide(boolean teamDelivered, boolean hasGrandmasCookie, boolean hasVanillaCookie) {
        if (teamDelivered) {
            return Outcome.ALREADY_DELIVERED;
        }
        if (hasGrandmasCookie) {
            return Outcome.ACCEPT;
        }
        return hasVanillaCookie ? Outcome.VANILLA_COOKIE : Outcome.NO_COOKIE;
    }
}
