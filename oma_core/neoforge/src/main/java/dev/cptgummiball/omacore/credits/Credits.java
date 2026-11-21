package dev.cptgummiball.omacore.credits;

import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/**
 * The credits after Grandma accepts THE COOKIE (owner backlog: "Credits or a title card when the cookie is finally
 * handed over", kept for the very end).
 * <ul>
 *   <li>The player who handed it over sees the credits screen about 13 s later, after Grandma's "thanks" line.</li>
 *   <li>Online teammates get a chat line with a clickable "[Watch the credits]".</li>
 *   <li>{@code /oma credits} replays them for every player whose team delivered (and for operators).</li>
 * </ul>
 * The screen is client code (client/CreditsScreen); the server only sends {@link CreditsPayload}. The channel is
 * optional, so a client without oma_core can still join; it gets a title card instead.
 */
public final class Credits {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DELAY_TICKS = 260;
    private static final Map<UUID, Long> PENDING = new HashMap<>();

    private Credits() {
    }

    /** Mod bus: registers the payload; the client handler is only reached on a client. */
    public static void registerPayload(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(CreditsPayload.TYPE, CreditsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> dev.cptgummiball.omacore.client.OmaClient.openCredits()));
    }

    /** Called once when a team's cookie was accepted. */
    public static void afterDelivery(ServerPlayer deliverer, Collection<ServerPlayer> team) {
        MinecraftServer server = deliverer.getServer();
        PENDING.put(deliverer.getUUID(), (long) server.getTickCount() + DELAY_TICKS);
        for (ServerPlayer member : team) {
            if (member != deliverer) {
                member.sendSystemMessage(Component.translatable("oma_core.credits.team_notice", deliverer.getDisplayName())
                        .append(" ").append(watchLink()));
            }
        }
    }

    /** A clickable chat link that runs {@code /oma credits}. */
    public static Component watchLink() {
        return Component.translatable("oma_core.credits.watch").withStyle(style -> style
                .withColor(ChatFormatting.GOLD).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/oma credits"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("oma_core.credits.watch.hover"))));
    }

    /** Opens the credits for this player now (screen, or a title card on a client without oma_core). */
    public static void show(ServerPlayer player) {
        if (player.connection.hasChannel(CreditsPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new CreditsPayload());
        } else {
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("oma_core.credits.card.title")));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("oma_core.credits.card.subtitle")));
        }
        LOGGER.info("[oma_core] credits shown to {}", player.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        for (Iterator<Map.Entry<UUID, Long>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            if (e.getValue() > now) {
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player != null) {
                show(player);
            }
        }
    }
}
