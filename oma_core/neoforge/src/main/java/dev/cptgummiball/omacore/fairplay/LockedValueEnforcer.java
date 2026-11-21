package dev.cptgummiball.omacore.fairplay;

import com.mojang.logging.LogUtils;
import dev.cptgummiball.omacore.core.fairplay.FairPlayPolicy;
import dev.cptgummiball.omacore.core.fairplay.LockRule;
import dev.cptgummiball.omacore.core.fairplay.LockTable;
import dev.cptgummiball.omacore.core.fairplay.ValueCoercion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

/**
 * Resets pack-locked {@link ModConfigSpec} values (ADR-0005). Reads the uncached value, so a change made
 * through a config screen or a file edit is seen at once. Only TOML configs built with ModConfigSpec are
 * covered; other formats (FTB SNBT, plain properties) need their own handling.
 *
 * <p>A rule locks either an explicit value or the mod's own default ({@code @default}); the default is
 * compared with {@link Objects#equals}, which also covers list entries. Entries are looked up once and
 * cached: a {@link ModConfigSpec.ConfigValue} belongs to its spec for the whole game, only the loaded file
 * behind it changes between worlds.</p>
 */
final class LockedValueEnforcer {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** One applied reset, for logging and the player message. */
    record Reset(LockRule rule, String from, String to) {
    }

    private final LockTable table;
    private final Set<String> reported = new HashSet<>();
    /** A located entry and the spec it belongs to (the spec says whether its file is loaded). */
    private record Entry(ModConfigSpec spec, ModConfigSpec.ConfigValue<Object> value) {
    }

    /** After this many resets of one entry in a session it is re-checked only once a minute, so a value that
     * something keeps writing back (the mod itself, a sync) cannot rewrite the file every second. It is never
     * dropped, so flipping a value often does not switch the lock off. */
    private static final int MAX_RESETS_PER_RULE = 20;
    private static final long BACKOFF_NANOS = 60_000_000_000L;

    private final Map<LockRule, Entry> resolved = new HashMap<>();
    private final Set<LockRule> missing = new HashSet<>();
    private final Map<LockRule, Integer> resetCounts = new HashMap<>();
    private final Map<LockRule, Long> lastReset = new HashMap<>();
    private final Set<LockRule> giveUp = new HashSet<>();

    LockedValueEnforcer(LockTable table) {
        this.table = table;
    }

    List<Reset> enforce() {
        List<Reset> resets = new ArrayList<>();
        for (LockRule rule : table.rules()) {
            Optional<Entry> found = lookup(rule);
            if (found.isEmpty()) {
                continue;
            }
            if (!found.get().spec().isLoaded()) {
                continue; // e.g. SERVER configs between worlds
            }
            ModConfigSpec.ConfigValue<Object> value = found.get().value();
            Object current = value.getRaw();
            Object wanted;
            if (giveUp.contains(rule) || backingOff(rule)) {
                continue;
            }
            if (rule.usesModDefault()) {
                wanted = value.getDefault();
                if (Objects.equals(current, wanted)) {
                    continue;
                }
                if (!value.getSpec().test(wanted)) {
                    // a mod whose default breaks its own range: NeoForge would correct it straight back
                    giveUp(rule, "the mod's default '" + ValueCoercion.display(wanted)
                            + "' is outside its own range; give the table an explicit value");
                    continue;
                }
            } else {
                if (FairPlayPolicy.sameValue(ValueCoercion.display(current), rule.value())) {
                    continue;
                }
                Optional<Object> coerced = ValueCoercion.coerce(rule.value(),
                        current != null ? current : value.getDefault());
                if (coerced.isEmpty() || !value.getSpec().test(coerced.get())) {
                    reportOnce(rule, "locked value '" + rule.value() + "' does not fit the entry's type or range");
                    continue;
                }
                wanted = coerced.get();
            }
            value.set(wanted);
            value.save(); // writes the file; NeoForge fires the owning mod's reload event
            resets.add(new Reset(rule, ValueCoercion.display(current), ValueCoercion.display(wanted)));
            lastReset.put(rule, System.nanoTime());
            if (resetCounts.merge(rule, 1, Integer::sum) == MAX_RESETS_PER_RULE + 1) {
                reportOnce(rule, "reset more than " + MAX_RESETS_PER_RULE
                        + " times this session; now re-checked once a minute (something writes it back)");
            }
        }
        return resets;
    }

    private boolean backingOff(LockRule rule) {
        return resetCounts.getOrDefault(rule, 0) > MAX_RESETS_PER_RULE
                && System.nanoTime() - lastReset.getOrDefault(rule, 0L) < BACKOFF_NANOS;
    }

    private void giveUp(LockRule rule, String why) {
        giveUp.add(rule);
        LOGGER.error("[oma_core] fair-play lock '{}' is no longer enforced this session: {}", rule.key(), why);
    }

    /** Number of rules whose entry was not found (mod absent, config not loaded, or wrong path). */
    int missingCount() {
        return missing.size();
    }

    private Optional<Entry> lookup(LockRule rule) {
        Entry cached = resolved.get(rule);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<Entry> found = find(rule);
        if (found.isPresent()) {
            resolved.put(rule, found.get());
            missing.remove(rule);
        } else if (missing.add(rule)) {
            reportOnce(rule, "entry not found (mod absent, config not loaded or wrong path)");
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static Optional<Entry> find(LockRule rule) {
        ModConfig.Type type = switch (rule.side()) {
            case SERVER -> ModConfig.Type.SERVER;
            case COMMON -> ModConfig.Type.COMMON;
            case STARTUP -> ModConfig.Type.STARTUP;
        };
        List<String> path = Arrays.asList(rule.path().split("\\."));
        for (ModConfig config : ModConfigs.getModConfigs(rule.modId())) {
            if (config.getType() != type || !(config.getSpec() instanceof ModConfigSpec spec)) {
                continue;
            }
            Object leaf = spec.getValues().get(path);
            if (leaf instanceof ModConfigSpec.ConfigValue<?> value) {
                return Optional.of(new Entry(spec, (ModConfigSpec.ConfigValue<Object>) value));
            }
        }
        return Optional.empty();
    }

    private void reportOnce(LockRule rule, String problem) {
        if (reported.add(rule.key() + problem)) {
            LOGGER.error("[oma_core] fair-play lock '{}': {}", rule.key(), problem);
        }
    }
}
