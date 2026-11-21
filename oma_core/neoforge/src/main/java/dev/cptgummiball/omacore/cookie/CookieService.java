package dev.cptgummiball.omacore.cookie;

import com.mojang.logging.LogUtils;
import dev.cptgummiball.omacore.core.cookie.CookieRules;
import dev.cptgummiball.omacore.credits.Credits;
import dev.cptgummiball.omacore.house.OmaWorldData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;

/**
 * Grandma's two server-authoritative services. Players reach them by right-clicking Grandma
 * ({@link GrandmaInteraction}); operators can call them with {@code /oma grandma book} and {@code /oma cookie handoff}:
 * <ul>
 *   <li>the "recipe book" (FTB Quests' quest book with Grandma's name on it), given when the player has none;</li>
 *   <li>the cookie handoff: exactly one {@value CookieRules#GRANDMAS_COOKIE} per team, recorded with a
 *       compare-and-set and saved at once, then the item is taken. A crash in between can only leave the player
 *       with a souvenir cookie; it can never grant a second delivery.</li>
 * </ul>
 */
public final class CookieService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation COOKIE = ResourceLocation.parse(CookieRules.GRANDMAS_COOKIE);
    private static final ResourceLocation VANILLA_COOKIE = ResourceLocation.parse(CookieRules.VANILLA_COOKIE);
    private static final ResourceLocation QUEST_BOOK = ResourceLocation.parse("ftbquests:book");

    private CookieService() {
    }

    /** True for Grandma's cookie and for a vanilla cookie (both are "offering Grandma a cookie"). */
    public static boolean isAnyCookie(ItemStack stack) {
        return BuiltInRegistries.ITEM.getOptional(COOKIE).map(stack::is).orElse(false)
                || BuiltInRegistries.ITEM.getOptional(VANILLA_COOKIE).map(stack::is).orElse(false);
    }

    /** Right-click with a cookie in hand: only the held stack counts; Grandma answers through her dialog. */
    public static CookieRules.Outcome handoffHeld(ServerPlayer player) {
        return handoff(player, true, false);
    }

    /** Operator command: searches the whole inventory and answers in chat. */
    public static int handoff(ServerPlayer player) {
        return handoff(player, false, true) == CookieRules.Outcome.ACCEPT ? 1 : 0;
    }

    /** True when the player's team already handed Grandma her cookie. */
    public static boolean teamDelivered(ServerPlayer player) {
        return OmaWorldData.get(player.getServer()).cookies().isDelivered(TeamResolver.teamOf(player));
    }

    /** A replacement book (quiet when the player still has one). */
    public static void giveSpareRecipeBookIfMissing(ServerPlayer player) {
        Optional<Item> book = BuiltInRegistries.ITEM.getOptional(QUEST_BOOK);
        if (book.isPresent() && findSlot(player.getInventory(), book.get()) < 0 && giveRecipeBook(player, true) == 1) {
            player.sendSystemMessage(Component.translatable("oma_core.grandma.book_spare"));
        }
    }

    public static int giveRecipeBook(ServerPlayer player) {
        return giveRecipeBook(player, false);
    }

    /** @param quietIfPresent no message when the player already has the book (every normal right-click) */
    public static int giveRecipeBook(ServerPlayer player, boolean quietIfPresent) {
        Optional<Item> book = BuiltInRegistries.ITEM.getOptional(QUEST_BOOK);
        if (book.isEmpty()) {
            player.sendSystemMessage(Component.translatable("oma_core.grandma.book_missing_mod"));
            LOGGER.error("[oma_core] recipe book: item {} is not registered (FTB Quests missing?)", QUEST_BOOK);
            return 0;
        }
        if (findSlot(player.getInventory(), book.get()) >= 0) {
            if (!quietIfPresent) {
                player.sendSystemMessage(Component.translatable("oma_core.grandma.book_already"));
            }
            return 0;
        }
        ItemStack stack = new ItemStack(book.get());
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.oma_core.recipe_book").withStyle(s -> s.withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.oma_core.recipe_book.lore"))));
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.sendSystemMessage(Component.translatable("oma_core.grandma.book_given"));
        LOGGER.info("[oma_core] recipe book given to {}", player.getGameProfile().getName());
        return 1;
    }

    private static CookieRules.Outcome handoff(ServerPlayer player, boolean heldOnly, boolean chat) {
        MinecraftServer server = player.getServer();
        OmaWorldData data = OmaWorldData.get(server);
        UUID team = TeamResolver.teamOf(player);
        Optional<Item> cookie = BuiltInRegistries.ITEM.getOptional(COOKIE);
        Inventory inv = player.getInventory();
        Optional<Item> vanillaItem = BuiltInRegistries.ITEM.getOptional(VANILLA_COOKIE);
        int slot;
        boolean vanilla;
        if (heldOnly) {
            slot = cookie.map(item -> inv.getSelected().is(item) ? inv.selected : -1).orElse(-1);
            vanilla = vanillaItem.map(item -> inv.getSelected().is(item)).orElse(false);
        } else {
            slot = cookie.map(item -> findSlot(inv, item)).orElse(-1);
            vanilla = vanillaItem.map(item -> findSlot(inv, item) >= 0).orElse(false);
        }
        CookieRules.Outcome outcome = CookieRules.decide(data.cookies().isDelivered(team), slot >= 0, vanilla);
        switch (outcome) {
            case ALREADY_DELIVERED -> {
                if (chat) {
                    player.sendSystemMessage(Component.translatable("oma_core.cookie.already"));
                }
                return outcome;
            }
            case VANILLA_COOKIE -> {
                if (chat) {
                    player.sendSystemMessage(Component.translatable("oma_core.cookie.vanilla"));
                }
                player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 0.8f);
                return outcome;
            }
            case NO_COOKIE -> {
                if (chat) {
                    player.sendSystemMessage(Component.translatable("oma_core.cookie.none"));
                }
                return outcome;
            }
            case ACCEPT -> {
                long now = server.overworld().getGameTime();
                if (!data.tryDeliverCookie(team, player.getUUID(), now)) { // a teammate was faster this tick
                    if (chat) {
                        player.sendSystemMessage(Component.translatable("oma_core.cookie.already"));
                    }
                    return CookieRules.Outcome.ALREADY_DELIVERED;
                }
                server.overworld().getDataStorage().save(); // record first, then take the cookie
                inv.getItem(slot).shrink(1);
                for (ServerPlayer member : TeamResolver.onlineTeamMembers(player)) {
                    member.sendSystemMessage(Component.translatable("oma_core.cookie.accepted", player.getDisplayName()));
                    member.level().playSound(null, member.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                LOGGER.info("[oma_core] cookie: team {} delivered Grandma's cookie (by {})", team, player.getGameProfile().getName());
                Credits.afterDelivery(player, TeamResolver.onlineTeamMembers(player));
                return outcome;
            }
            default -> throw new IllegalStateException(outcome.name());
        }
    }

    /** The selected hotbar slot first, then the rest of the inventory; -1 if none. */
    private static int findSlot(Inventory inv, Item item) {
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
}
