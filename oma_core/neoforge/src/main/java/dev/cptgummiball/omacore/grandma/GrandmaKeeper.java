package dev.cptgummiball.omacore.grandma;

import com.mojang.logging.LogUtils;
import dev.cptgummiball.omacore.core.grandma.GrandmaCensus;
import dev.cptgummiball.omacore.house.OmaWorldData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Keeps exactly one Grandma at her spot (ADR-0007, revised). Grandma herself is an Easy NPC built from the
 * pack preset {@value #PRESET}: invulnerable, immovable, not attackable, not leashable or pushable, with her
 * skin and dialog. oma_core adds what a single NPC cannot guarantee by itself:
 * <ul>
 *   <li>she is spawned with a fixed UUID through Easy NPC's documented {@code /easy_npc preset import} command;</li>
 *   <li>duplicates (tag {@value #TAG}) are refused on join and removed by the census, which only decides
 *       when her chunk's entities are loaded;</li>
 *   <li>a missing Grandma is restored at most {@link #MAX_RESTORES} times per 10 minutes;
 *       operators use {@code /oma repair};</li>
 *   <li>no mob may target her (second layer next to Easy NPC's own "attackable by monsters" switch).</li>
 * </ul>
 */
public final class GrandmaKeeper {
    public static final String TAG = "oma_core_grandma";
    static final String PRESET = "oma_core:easy_npc/preset/grandma/grandma.npc.snbt";
    /** Bump when the preset changes (dialogs, attributes): an existing Grandma is re-imported in place (same UUID). */
    static final int PRESET_VERSION = 4; // 3: random story lines (working/vanilla); 4: own Blockbench model
    /** Entity type of the preset; a Grandma of another type cannot be updated in place (Easy NPC keeps the type). */
    static final String PRESET_ENTITY_TYPE = "easy_npc:easy_model_entities_npc";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_INTERVAL_TICKS = 100;
    private static final int MAX_RESTORES = 3;
    private static final double SEARCH_RADIUS = 16.0;

    /** Easy NPC adds an imported NPC to the world a little later; wait this long before trying again. */
    private static final int IMPORT_TIMEOUT_TICKS = 200;

    private final GrandmaCensus.RestoreLimiter limiter = new GrandmaCensus.RestoreLimiter(MAX_RESTORES, 12000);
    private UUID pendingUuid;
    private long pendingSince;
    private boolean pendingWasRetry;

    public static boolean isGrandma(Entity entity) {
        return entity != null && entity.getTags().contains(TAG);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ensure(event.getServer(), false);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_INTERVAL_TICKS == 0) {
            ensure(server, false);
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        pendingUuid = null;
    }

    @SubscribeEvent
    public void onChangeTarget(LivingChangeTargetEvent event) {
        if (isGrandma(event.getNewAboutToBeSetTarget())) {
            event.setCanceled(true);
        }
    }

    /**
     * A second Grandma (e.g. the preset imported again) is refused while the real one is loaded. Easy NPC keeps an
     * inert index record of the refused NPC (seen in {@code /easy_npc list}); reviving it is refused here again.
     */
    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!isGrandma(entity) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        UUID stored = OmaWorldData.get(level.getServer()).grandmaUuid();
        if (stored != null && !stored.equals(entity.getUUID()) && isGrandma(level.getServer().overworld().getEntity(stored))) {
            event.setCanceled(true);
            LOGGER.warn("[oma_core] Grandma: refused a second Grandma ({}) at {}; there is only one", entity.getUUID(), entity.blockPosition());
        }
    }

    /** Runs the census; {@code force} ignores the restore limit (operator /oma repair). */
    public Component ensure(MinecraftServer server, boolean force) {
        OmaWorldData data = OmaWorldData.get(server);
        if (!data.houseGenerated()) {
            return Component.translatable("oma_core.house.missing");
        }
        ServerLevel level = server.overworld();
        BlockPos spot = data.grandmaPos();
        if (!level.areEntitiesLoaded(ChunkPos.asLong(spot))) {
            return Component.translatable("oma_core.grandma.not_loaded");
        }
        List<Entity> found = new ArrayList<>(level.getEntities((Entity) null, new AABB(spot).inflate(SEARCH_RADIUS), GrandmaKeeper::isGrandma));
        Entity byId = data.grandmaUuid() == null ? null : level.getEntity(data.grandmaUuid());
        if (isGrandma(byId) && !found.contains(byId)) {
            found.add(byId);
        }
        GrandmaCensus.Decision d = GrandmaCensus.decide(data.grandmaUuid(), found.stream().map(Entity::getUUID).toList());
        for (Entity e : found) {
            if (d.discard().contains(e.getUUID())) {
                remove(server, e);
            }
        }
        if (d.keep().isPresent()) {
            pendingUuid = null;
            UUID keep = d.keep().get();
            Entity g = found.stream().filter(e -> e.getUUID().equals(keep)).findFirst().orElseThrow();
            if (g.getYRot() != data.grandmaYaw() && g.tickCount < CHECK_INTERVAL_TICKS * 2) {
                face(g, data.grandmaYaw()); // freshly imported: turn her towards the door
            }
            if (!keep.equals(data.grandmaUuid())) {
                data.setGrandmaUuid(keep);
                LOGGER.info("[oma_core] Grandma: adopted {} at {}", keep, g.blockPosition());
            }
            if (data.grandmaPresetVersion() < PRESET_VERSION) {
                int old = data.grandmaPresetVersion();
                String type = BuiltInRegistries.ENTITY_TYPE.getKey(g.getType()).toString();
                if (!PRESET_ENTITY_TYPE.equals(type)) {
                    // A new entity type cannot be imported in place: remove her, the next check imports her again with
                    // the same UUID (her identity, dialogs and the team's cookie state stay keyed to it).
                    LOGGER.info("[oma_core] Grandma: preset v{} -> v{} changes her entity type ({} -> {}); re-creating {}",
                            old, PRESET_VERSION, type, PRESET_ENTITY_TYPE, keep);
                    run(server, "easy_npc delete " + keep);
                    if (!g.isRemoved()) {
                        g.discard();
                    }
                    return Component.translatable("oma_core.grandma.spawning");
                }
                importPreset(server, spot, keep);
                data.setGrandmaPresetVersion(PRESET_VERSION);
                LOGGER.info("[oma_core] Grandma: updated {} in place from preset v{} to v{}", keep, old, PRESET_VERSION);
            }
            if (g.position().distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) > 1.0) {
                g.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                face(g, data.grandmaYaw());
                LOGGER.info("[oma_core] Grandma: put back on her spot {}", spot);
            }
            return Component.translatable("oma_core.grandma.ok", spot.toShortString());
        }
        long now = level.getGameTime();
        if (pendingUuid != null && now - pendingSince < IMPORT_TIMEOUT_TICKS) {
            return Component.translatable("oma_core.grandma.spawning");
        }
        boolean retry = pendingUuid != null; // the previous import never showed up
        if (retry && pendingWasRetry) {
            LOGGER.error("[oma_core] Grandma: Easy NPC did not create her from {} (two attempts); is Easy NPC installed "
                    + "and the preset valid? Operators: /oma repair", PRESET);
            pendingUuid = null;
            return Component.translatable("oma_core.grandma.failed");
        }
        if (!force && !limiter.tryRestore(now)) {
            LOGGER.error("[oma_core] Grandma: missing again, but restored {} times in 10 minutes already; "
                    + "not respawning automatically (operators: /oma repair)", MAX_RESTORES);
            return Component.translatable("oma_core.grandma.limit");
        }
        // First attempt reuses the stored UUID (her identity); a retry after a lost import uses a new one.
        UUID uuid = !retry && data.grandmaUuid() != null ? data.grandmaUuid() : UUID.randomUUID();
        data.setGrandmaUuid(uuid); // before the import, so the duplicate guard accepts her
        pendingUuid = uuid;
        pendingSince = now;
        pendingWasRetry = retry;
        importPreset(server, spot, uuid);
        data.setGrandmaPresetVersion(PRESET_VERSION);
        LOGGER.info("[oma_core] Grandma: requested {} at {} from {}{}", uuid, spot, PRESET, retry ? " (retry with a new UUID)" : "");
        return Component.translatable("oma_core.grandma.spawned", spot.toShortString());
    }

    /** With an existing UUID, Easy NPC updates that NPC instead of creating a new one (documented import behaviour). */
    private static void importPreset(MinecraftServer server, BlockPos spot, UUID uuid) {
        run(server, "easy_npc preset import data " + PRESET + " " + spot.getX() + " " + spot.getY() + " " + spot.getZ() + " " + uuid);
    }

    private static void remove(MinecraftServer server, Entity e) {
        LOGGER.warn("[oma_core] Grandma: removing duplicate {} at {}", e.getUUID(), e.blockPosition());
        run(server, "easy_npc delete " + e.getUUID());
        if (!e.isRemoved()) {
            e.discard();
        }
    }

    private static void face(Entity g, float yaw) {
        g.setYRot(yaw);
        g.setYHeadRot(yaw);
        if (g instanceof LivingEntity living) {
            living.setYBodyRot(yaw);
        }
    }

    private static void run(MinecraftServer server, String command) {
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }
}
