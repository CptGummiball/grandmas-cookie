package dev.cptgummiball.omacore.house;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Every join ends at Grandma's house (OMA_CORE_SPEC step 5), in singleplayer (loading the world) and on
 * a dedicated server alike. Where the player logged out is remembered as their work place, unless they
 * were at home; {@code /oma work return} takes them back once per join after a safety check.
 * State lives in the player's persistent data (copied on respawn), so it is saved with the player.
 */
public final class RejoinHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG = "oma_core";
    /** Ticks to wait after login so the login itself (incl. a dimension switch) has finished. */
    private static final int TELEPORT_DELAY_TICKS = 5;

    private final Map<UUID, Integer> pendingHome = new HashMap<>();
    private final Set<UUID> returnAvailable = new HashSet<>();

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        OmaWorldData data = OmaWorldData.get(server);
        if (!data.houseGenerated()) {
            return;
        }
        CompoundTag oma = player.getPersistentData().getCompound(TAG);
        boolean firstJoin = !oma.getBoolean("seen");
        BlockPos pos = player.blockPosition();
        boolean atHome = player.level().dimension() == Level.OVERWORLD
                && data.area().map(a -> a.contains(pos.getX(), pos.getY(), pos.getZ())).orElse(false);
        if (!firstJoin && player.isAlive() && !atHome) {
            oma.putString("workDim", player.level().dimension().location().toString());
            oma.put("workPos", NbtUtils.writeBlockPos(pos));
            oma.putFloat("workYaw", player.getYRot());
            oma.putFloat("workPitch", player.getXRot());
            oma.putLong("workGameTime", server.overworld().getGameTime());
        }
        oma.putBoolean("seen", true);
        player.getPersistentData().put(TAG, oma);
        if (oma.contains("workPos")) {
            returnAvailable.add(player.getUUID());
        }
        pendingHome.put(player.getUUID(), server.getTickCount() + TELEPORT_DELAY_TICKS);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        pendingHome.remove(event.getEntity().getUUID());
        returnAvailable.remove(event.getEntity().getUUID());
    }

    /** Persistent data is not carried over to the respawned player object by itself. */
    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        CompoundTag oma = event.getOriginal().getPersistentData().getCompound(TAG);
        if (!oma.isEmpty()) {
            event.getEntity().getPersistentData().put(TAG, oma.copy());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (pendingHome.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Integer>> it = pendingHome.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            if (e.getValue() > now) {
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player != null && player.isAlive()) {
                sendHome(server, player);
            }
        }
    }

    private void sendHome(MinecraftServer server, ServerPlayer player) {
        OmaWorldData data = OmaWorldData.get(server);
        ServerLevel overworld = server.overworld();
        BlockPos a = data.arrival();
        player.stopRiding();
        overworld.getChunk(a); // make sure the arrival chunk is loaded
        player.teleportTo(overworld, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, data.arrivalYaw(), 0f);
        player.sendSystemMessage(Component.translatable(returnAvailable.contains(player.getUUID())
                ? "oma_core.rejoin.home_with_return" : "oma_core.rejoin.home"));
        LOGGER.info("[oma_core] rejoin: {} sent to Grandma's house", player.getGameProfile().getName());
    }

    /** {@code /oma work return}: back to the remembered work place, once per join, only if it is safe. */
    public int workReturn(ServerPlayer player) {
        CompoundTag oma = player.getPersistentData().getCompound(TAG);
        Optional<BlockPos> saved = NbtUtils.readBlockPos(oma, "workPos");
        if (saved.isEmpty() || !oma.contains("workDim")) {
            player.sendSystemMessage(Component.translatable("oma_core.work.none"));
            return 0;
        }
        if (!returnAvailable.contains(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("oma_core.work.used"));
            return 0;
        }
        ResourceLocation dimId = ResourceLocation.tryParse(oma.getString("workDim"));
        ServerLevel level = dimId == null ? null
                : player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        BlockPos pos = saved.get();
        if (level == null || !isSafe(level, pos)) {
            player.sendSystemMessage(Component.translatable("oma_core.work.unsafe"));
            return 0;
        }
        returnAvailable.remove(player.getUUID());
        player.stopRiding();
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, oma.getFloat("workYaw"), oma.getFloat("workPitch"));
        player.sendSystemMessage(Component.translatable("oma_core.work.returned"));
        return 1;
    }

    private static boolean isSafe(ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos) || pos.getY() < level.getMinBuildHeight()
                || pos.getY() + 1 >= level.getMaxBuildHeight()) {
            return false;
        }
        level.getChunk(pos); // loads the chunk for the check and the teleport
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState below = level.getBlockState(pos.below());
        return feet.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && !feet.getFluidState().is(FluidTags.LAVA)
                && !head.getFluidState().is(FluidTags.LAVA)
                && !below.getFluidState().is(FluidTags.LAVA);
    }
}
