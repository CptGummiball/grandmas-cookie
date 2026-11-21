package dev.cptgummiball.omacore.core.cookie;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Which team already handed Grandma her cookie. {@link #tryDeliver} is a compare-and-set: the first
 * delivery of a team wins, every later one (second click, second player, reconnect) is refused. Runs on
 * the server thread only, so check and set cannot interleave.
 */
public final class CookieLedger {
    /** One accepted delivery. */
    public record Delivery(UUID player, long gameTime) {
    }

    private final Map<UUID, Delivery> deliveries = new LinkedHashMap<>();

    public boolean isDelivered(UUID team) {
        return deliveries.containsKey(team);
    }

    public Optional<Delivery> delivery(UUID team) {
        return Optional.ofNullable(deliveries.get(team));
    }

    /** @return true if this call recorded the team's delivery, false if the team had already delivered. */
    public boolean tryDeliver(UUID team, UUID player, long gameTime) {
        return deliveries.putIfAbsent(team, new Delivery(player, gameTime)) == null;
    }

    /** Restores a stored delivery (loading); an existing entry is kept. */
    public void restore(UUID team, Delivery delivery) {
        deliveries.putIfAbsent(team, delivery);
    }

    public Map<UUID, Delivery> view() {
        return Collections.unmodifiableMap(deliveries);
    }
}
