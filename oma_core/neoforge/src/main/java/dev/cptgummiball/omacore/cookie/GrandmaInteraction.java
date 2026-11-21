package dev.cptgummiball.omacore.cookie;

import com.mojang.logging.LogUtils;
import dev.cptgummiball.omacore.core.cookie.CookieRules;
import dev.cptgummiball.omacore.grandma.GrandmaKeeper;
import dev.cptgummiball.omacore.house.OmaWorldData;
import dev.cptgummiball.omacore.story.StoryProgress;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

/**
 * Right-clicking Grandma, handled on the server before Easy NPC sees the click ({@code Player.interactOn} fires
 * this event first; cancelling it skips the NPC's own interaction). oma_core decides what happens and then opens
 * the matching Easy NPC dialog of her preset by label, so her lines follow the player's progress:
 * <ul>
 *   <li>{@code greeting}: first visit, she hands over her recipe book (FTB Quests' quest book);</li>
 *   <li>{@code working}: book received, cookie not delivered yet (a spare book if the player lost it);</li>
 *   <li>{@code thanks}: the cookie was just handed over (exactly once per team);</li>
 *   <li>{@code delivered}: the player's team already delivered;</li>
 *   <li>{@code vanilla}: a vanilla cookie in hand.</li>
 * </ul>
 * Easy NPC command actions are not used for this: Easy NPC reduces their permission level on import (server probe
 * 2026-11-20). oma_core runs {@code /easy_npc dialog open} itself with the server's own permission.
 */
public final class GrandmaInteraction {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LEGACY_TAG = "oma_core";
    private static final String LEGACY_BOOK_GIVEN = "recipeBookGiven";

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !GrandmaKeeper.isGrandma(event.getTarget())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return; // client side: the interaction packet was already sent; the server decides
        }
        StoryProgress.award(player, StoryProgress.MET_GRANDMA);
        ItemStack held = event.getItemStack();
        if (CookieService.isAnyCookie(held)) {
            CookieRules.Outcome outcome = CookieService.handoffHeld(player);
            if (outcome == CookieRules.Outcome.VANILLA_COOKIE) {
                StoryProgress.award(player, StoryProgress.VANILLA_COOKIE_REJECTED);
            } else if (outcome == CookieRules.Outcome.ACCEPT) {
                StoryProgress.award(player, StoryProgress.COOKIE_DELIVERED);
            }
            openDialog(player, switch (outcome) {
                case ACCEPT -> "thanks";
                case ALREADY_DELIVERED -> "delivered";
                case VANILLA_COOKIE -> "vanilla";
                case NO_COOKIE -> "working";
            });
            return;
        }
        if (CookieService.teamDelivered(player)) {
            openDialog(player, "delivered");
            return;
        }
        // The story advancement is the "book given" flag: it survives death. The old persistent-data flag (lost on
        // death) is still honoured for players from earlier test builds.
        boolean legacyFlag = player.getPersistentData().getCompound(LEGACY_TAG).getBoolean(LEGACY_BOOK_GIVEN);
        if (!legacyFlag && !StoryProgress.has(player, StoryProgress.RECEIVED_RECIPE_BOOK)) {
            CookieService.giveRecipeBook(player, true);
            StoryProgress.award(player, StoryProgress.RECEIVED_RECIPE_BOOK);
            openDialog(player, "greeting");
            return;
        }
        CookieService.giveSpareRecipeBookIfMissing(player);
        StoryProgress.award(player, StoryProgress.RECEIVED_RECIPE_BOOK); // players from before story tracking
        openDialog(player, "working");
    }

    private static void openDialog(ServerPlayer player, String label) {
        MinecraftServer server = player.getServer();
        UUID grandma = OmaWorldData.get(server).grandmaUuid();
        if (grandma == null) {
            LOGGER.warn("[oma_core] Grandma dialog '{}' not opened: no Grandma recorded", label);
            return;
        }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "easy_npc dialog open " + grandma + " " + player.getGameProfile().getName() + " " + label);
        GrandmaVoice.play(player, label);
    }
}
