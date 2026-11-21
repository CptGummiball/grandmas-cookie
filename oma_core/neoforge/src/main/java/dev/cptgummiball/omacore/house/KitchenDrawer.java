package dev.cptgummiball.omacore.house;

import com.mojang.logging.LogUtils;
import dev.cptgummiball.omacore.story.StoryProgress;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Grandma's stuck kitchen drawer (quest C00_Q05 "The Broken Drawer", docs/STORY.md). Every right-click on the drawer
 * block is cancelled on the server, so the furniture's own inventory never opens:
 * <ul>
 *   <li>without a cabinet handle: "the drawer is stuck" message and the locked-chest sound;</li>
 *   <li>with a cabinet handle ({@value #HANDLE}, held or anywhere in the inventory): one handle is used up, the story
 *       step {@code oma_core:story/drawer_opened} is granted (the FTB Quests task) and the player receives Grandma's
 *       1987 notice and the Crumblix factory cookie, once per player;</li>
 *   <li>afterwards the drawer is empty; a player who ate the factory cookie before showing it to Grandma (C00_Q07)
 *       finds another one, so that quest can never get stuck.</li>
 * </ul>
 * Owner request 2026-11-21: the drawer must stand out. Besides its dark wood among the spruce counters, it glitters
 * (wax-off sparkles every 1.5 s) for each nearby player who has not opened it yet, and only for that player.
 */
public final class KitchenDrawer {
    static final String HANDLE = "kubejs:cabinet_handle";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int GLITTER_INTERVAL_TICKS = 30;
    private static final double GLITTER_RANGE_SQR = 16 * 16;

    /** Sparkles at the drawer front for players who still have to open it (sent to them only, no shared state). */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % GLITTER_INTERVAL_TICKS != 0) {
            return;
        }
        Optional<BlockPos> drawer = OmaWorldData.get(event.getServer()).drawerPos();
        if (drawer.isEmpty()) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        BlockPos pos = drawer.get();
        if (!level.isLoaded(pos)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.6;
        double z = pos.getZ() + 0.5;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= GLITTER_RANGE_SQR && !StoryProgress.has(player, StoryProgress.DRAWER_OPENED)) {
                level.sendParticles(player, ParticleTypes.WAX_OFF, false, x, y, z, 4, 0.35, 0.25, 0.35, 0.02);
            }
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != Level.OVERWORLD) {
            return; // the client does not know the drawer; the server decides and cancels
        }
        Optional<BlockPos> drawer = OmaWorldData.get(player.getServer()).drawerPos();
        if (drawer.isEmpty() || !drawer.get().equals(event.getPos())) {
            return;
        }
        event.setCanceled(true); // no furniture GUI, no item use, no block placement against the drawer
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            use(player, event.getPos());
        }
    }

    private static void use(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (StoryProgress.has(player, StoryProgress.DRAWER_OPENED)) {
            if (!StoryProgress.has(player, StoryProgress.VANILLA_COOKIE_REJECTED)
                    && !player.getInventory().hasAnyOf(Set.of(Items.COOKIE))) {
                give(player, factoryCookie());
                player.sendSystemMessage(Component.translatable("oma_core.drawer.restock"));
            } else {
                player.sendSystemMessage(Component.translatable("oma_core.drawer.empty"));
            }
            level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5F, 1.2F);
            return;
        }
        Optional<Item> handle = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(HANDLE));
        int slot = handle.map(item -> slotOf(player.getInventory(), item)).orElse(-1);
        if (slot < 0) {
            if (handle.isEmpty()) {
                LOGGER.warn("[oma_core] Grandma's drawer: item {} is not registered (KubeJS scripts missing?)", HANDLE);
            }
            player.sendSystemMessage(Component.translatable("oma_core.drawer.stuck"));
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
            return;
        }
        if (!player.getAbilities().instabuild) {
            player.getInventory().getItem(slot).shrink(1);
        }
        StoryProgress.award(player, StoryProgress.DRAWER_OPENED);
        give(player, notice1987());
        give(player, factoryCookie());
        player.sendSystemMessage(Component.translatable("oma_core.drawer.opened"));
        level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
        LOGGER.info("[oma_core] Grandma's drawer: opened by {}", player.getGameProfile().getName());
    }

    /** The held stack first, then the rest of the inventory. */
    private static int slotOf(Inventory inv, Item item) {
        if (inv.getSelected().is(item)) {
            return inv.selected;
        }
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false); // full inventory: drop the rest at the player's feet
        }
    }

    /** Inspector Crumble's disqualification letter from the 1987 village fair (pages translated on the client). */
    static ItemStack notice1987() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Bescheid 1987/042"),
                "Insp. Streusel / Crumble", 0,
                List.of(Filterable.passThrough(Component.translatable("book.oma_core.notice_1987.page1")),
                        Filterable.passThrough(Component.translatable("book.oma_core.notice_1987.page2"))),
                true));
        book.set(DataComponents.CUSTOM_NAME,
                Component.translatable("item.oma_core.notice_1987").withStyle(s -> s.withItalic(false)));
        return book;
    }

    /** The certified factory cookie that won the 1987 Golden Cookie; a vanilla cookie, so Grandma rejects it. */
    static ItemStack factoryCookie() {
        ItemStack cookie = new ItemStack(Items.COOKIE);
        cookie.set(DataComponents.CUSTOM_NAME,
                Component.translatable("item.oma_core.crumblix_cookie").withStyle(s -> s.withItalic(false)));
        cookie.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.oma_core.crumblix_cookie.lore"))));
        return cookie;
    }
}
