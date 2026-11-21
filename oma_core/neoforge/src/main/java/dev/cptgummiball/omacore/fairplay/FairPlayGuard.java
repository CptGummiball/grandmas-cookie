package dev.cptgummiball.omacore.fairplay;

import com.mojang.logging.LogUtils;
import dev.cptgummiball.omacore.core.fairplay.ConfigSide;
import dev.cptgummiball.omacore.core.fairplay.FairPlayPolicy;
import dev.cptgummiball.omacore.core.fairplay.LockTable;
import dev.cptgummiball.omacore.core.fairplay.LockTableException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Server-side fair-play guard (ADR-0005). Runs on the logical server, so it covers the integrated
 * server of a singleplayer world as well as a dedicated server.
 *
 * <ul>
 *   <li>Locked config values: reset right after the server configs load (before the worlds load) and
 *       re-checked once per second, whichever way they were changed.</li>
 *   <li>"Open to LAN" with "Allow Cheats" cannot grant commands in a singleplayer world created without
 *       cheats. Vanilla {@code IntegratedServer.publishServer} sets
 *       {@code PlayerList.setAllowCommandsForAllPlayers(true)}, which makes {@code PlayerList.isOp} true
 *       for everyone; the guard turns it off on the next tick and re-sends permission levels.</li>
 * </ul>
 */
public final class FairPlayGuard {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TABLE = "/oma_core/fair_play_locks.txt";
    private static final int CHECK_INTERVAL_TICKS = 20;

    private final LockTable table;
    private final GuardSettings settings;
    private final LockedValueEnforcer enforcer;
    private volatile boolean enforceLockedValues;

    private FairPlayGuard(LockTable table, GuardSettings settings) {
        this.table = table;
        this.settings = settings;
        this.enforcer = new LockedValueEnforcer(table);
    }

    /** Loads the pack-owned lock table from the jar; a broken table fails loudly instead of half applying. */
    public static FairPlayGuard load() {
        try (InputStream in = FairPlayGuard.class.getResourceAsStream(TABLE)) {
            if (in == null) {
                throw new IllegalStateException("missing " + TABLE + " in the oma_core jar");
            }
            LockTable table = LockTable.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return new FairPlayGuard(table, GuardSettings.loadOrCreate());
        } catch (IOException | LockTableException e) {
            LOGGER.error("[oma_core] invalid fair-play lock table {}", TABLE, e);
            throw new IllegalStateException("invalid fair-play lock table: " + e.getMessage(), e);
        }
    }

    public int lockCount() {
        return table.size();
    }

    /** Server configs are loaded right before this event (ServerLifecycleHooks), worlds load after it. */
    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        enforceLockedValues = FairPlayPolicy.enforceLockedValues(server.isSingleplayer(),
                server.getWorldData().isAllowCommands(), settings.enforceOnDedicatedServer());
        if (enforceLockedValues) {
            report(enforcer.enforce(), null);
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        boolean singleplayer = server.isSingleplayer();
        boolean worldCheats = server.getWorldData().isAllowCommands();
        LOGGER.info("[oma_core] fair-play guard: singleplayer={} worldAllowsCommands={} enforceLockedValues={} "
                        + "lockedValues={} notFound={} lanCheatGuard={}",
                singleplayer, worldCheats, enforceLockedValues, table.size(), enforcer.missingCount(),
                singleplayer && !worldCheats);
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        enforceLockedValues = false;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        PlayerList players = server.getPlayerList();
        if (FairPlayPolicy.mustRevokeLanCommands(server.isSingleplayer(), server.getWorldData().isAllowCommands(),
                players.isAllowCommandsForAllPlayers())) {
            players.setAllowCommandsForAllPlayers(false);
            for (ServerPlayer player : players.getPlayers()) {
                players.sendPlayerPermissionLevel(player); // recomputes the level and re-sends the command tree
                player.sendSystemMessage(Component.translatable("oma_core.fair_play.lan_cheats_revoked"));
            }
            LOGGER.warn("[oma_core] fair-play guard: LAN 'Allow Cheats' revoked in a world created without cheats");
        }
        if (enforceLockedValues && table.size() > 0 && server.getTickCount() % CHECK_INTERVAL_TICKS == 0) {
            report(enforcer.enforce(), players);
        }
    }

    private static void report(List<LockedValueEnforcer.Reset> resets, PlayerList players) {
        for (LockedValueEnforcer.Reset reset : resets) {
            LOGGER.warn("[oma_core] fair-play guard: reset {} from '{}' to '{}'{}",
                    reset.rule().key(), reset.from(), reset.to(),
                    reset.rule().side() == ConfigSide.STARTUP ? " (startup config: effective after a restart)" : "");
            if (players != null) {
                Component setting = Component.literal(reset.rule().modId() + ": " + reset.rule().path());
                for (ServerPlayer player : players.getPlayers()) {
                    player.sendSystemMessage(Component.translatable("oma_core.fair_play.value_reset", setting));
                }
            }
        }
    }
}
