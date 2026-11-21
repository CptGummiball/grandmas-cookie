package dev.cptgummiball.omacore.story;

import com.mojang.logging.LogUtils;
import dev.cptgummiball.omacore.OmaCore;
import dev.cptgummiball.omacore.core.house.HouseArea;
import dev.cptgummiball.omacore.house.OmaWorldData;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Server-verified story steps for FTB Quests: invisible advancements {@code oma_core:story/<step>} (criterion
 * {@code minecraft:impossible}, no display) that only oma_core grants. FTB Quests advancement tasks pick them up,
 * so quests like "talk to Grandma" cannot be ticked off by hand.
 */
public final class StoryProgress {
    public static final String MET_GRANDMA = "met_grandma";
    public static final String RECEIVED_RECIPE_BOOK = "received_recipe_book";
    public static final String DRAWER_OPENED = "drawer_opened";
    public static final String VANILLA_COOKIE_REJECTED = "vanilla_cookie_rejected";
    public static final String LEFT_COTTAGE = "left_cottage";
    public static final String COOKIE_DELIVERED = "cookie_delivered";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LEAVE_CHECK_TICKS = 40;
    /** Extra distance beyond the house box (incl. its margin) that counts as "left the cottage". */
    private static final int LEAVE_DISTANCE = 16;

    private static AdvancementHolder holder(ServerPlayer player, String step) {
        AdvancementHolder holder = player.getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(OmaCore.MOD_ID, "story/" + step));
        if (holder == null) {
            LOGGER.error("[oma_core] story advancement oma_core:story/{} is missing", step);
        }
        return holder;
    }

    /**
     * True once the player reached the step. Advancements are stored per player in the world save and survive death
     * (unlike {@code Entity#getPersistentData}, which NeoForge only copies on respawn under "PlayerPersisted").
     */
    public static boolean has(ServerPlayer player, String step) {
        AdvancementHolder holder = holder(player, step);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    public static void award(ServerPlayer player, String step) {
        AdvancementHolder holder = holder(player, step);
        if (holder == null) {
            return;
        }
        if (!player.getAdvancements().getOrStartProgress(holder).isDone()) {
            player.getAdvancements().award(holder, "granted");
            LOGGER.info("[oma_core] story: {} reached '{}'", player.getGameProfile().getName(), step);
        }
    }

    /** "To the Factory!": first time a player is clearly outside Grandma's grounds in the overworld. */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % LEAVE_CHECK_TICKS != 0) {
            return;
        }
        OmaWorldData data = OmaWorldData.get(server);
        if (data.area().isEmpty()) {
            return;
        }
        HouseArea a = data.area().get();
        HouseArea far = new HouseArea(a.minX() - LEAVE_DISTANCE, a.minY() - 64, a.minZ() - LEAVE_DISTANCE,
                a.maxX() + LEAVE_DISTANCE, a.maxY() + 64, a.maxZ() + LEAVE_DISTANCE);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BlockPos p = player.blockPosition();
            if (player.level().dimension() != Level.OVERWORLD || far.contains(p.getX(), p.getY(), p.getZ())) {
                continue;
            }
            award(player, LEFT_COTTAGE);
        }
    }
}
