package dev.cptgummiball.omacore.house;

import com.mojang.brigadier.CommandDispatcher;
import dev.cptgummiball.omacore.cookie.CookieService;
import dev.cptgummiball.omacore.credits.Credits;
import dev.cptgummiball.omacore.grandma.GrandmaKeeper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * {@code /oma work return} and {@code /oma credits} (once the team delivered the cookie) for every player;
 * {@code /oma house info|place}, {@code /oma repair} (Grandma and her
 * kitchen drawer),
 * {@code /oma grandma book <player>} and {@code /oma cookie handoff <player>} for operators (level 2).
 */
public final class OmaCommands {
    private OmaCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, HouseManager house, RejoinHandler rejoin,
                                GrandmaKeeper keeper) {
        dispatcher.register(Commands.literal("oma")
                .then(Commands.literal("work")
                        .then(Commands.literal("return")
                                .executes(ctx -> rejoin.workReturn(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("credits")
                        .executes(ctx -> {
                            var player = ctx.getSource().getPlayerOrException();
                            if (!CookieService.teamDelivered(player) && !ctx.getSource().hasPermission(2)) {
                                ctx.getSource().sendFailure(Component.translatable("oma_core.credits.not_yet"));
                                return 0;
                            }
                            Credits.show(player);
                            return 1;
                        }))
                .then(Commands.literal("house")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                        .then(Commands.literal("place").executes(ctx -> place(ctx.getSource(), house))))
                // Operator tools (players use right-click on Grandma, see GrandmaInteraction).
                .then(Commands.literal("grandma")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("book")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> CookieService.giveRecipeBook(EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("cookie")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("handoff")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> CookieService.handoff(EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("repair")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            Component result = keeper.ensure(ctx.getSource().getServer(), true);
                            ctx.getSource().sendSuccess(() -> result, true);
                            if (house.ensureDrawer(ctx.getSource().getServer())) {
                                OmaWorldData.get(ctx.getSource().getServer()).drawerPos().ifPresent(p -> ctx.getSource().sendSuccess(
                                        () -> Component.translatable("oma_core.drawer.placed", p.toShortString()), true));
                            }
                            return 1;
                        })));
    }

    private static int info(CommandSourceStack src) {
        OmaWorldData data = OmaWorldData.get(src.getServer());
        if (!data.houseGenerated()) {
            src.sendFailure(Component.translatable("oma_core.house.missing"));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("oma_core.house.info",
                data.houseOrigin().toShortString(), data.arrival().toShortString(), data.grandmaPos().toShortString()), false);
        data.drawerPos().ifPresent(p -> src.sendSuccess(() -> Component.translatable("oma_core.house.drawer", p.toShortString()), false));
        return 1;
    }

    /** Operator opt-in for existing worlds: builds the cottage at the operator's position (once). */
    private static int place(CommandSourceStack src, HouseManager house) {
        OmaWorldData data = OmaWorldData.get(src.getServer());
        if (data.houseGenerated()) {
            src.sendFailure(Component.translatable("oma_core.house.exists", data.houseOrigin().toShortString()));
            return 0;
        }
        if (src.getLevel() != src.getServer().overworld()) {
            src.sendFailure(Component.translatable("oma_core.house.overworld_only"));
            return 0;
        }
        BlockPos at = BlockPos.containing(src.getPosition());
        BlockPos origin = at.offset(-house.cottage().sizeX() / 2, -1, -house.cottage().sizeZ() / 2);
        house.place(src.getServer(), origin);
        src.sendSuccess(() -> Component.translatable("oma_core.house.placed", origin.toShortString()), true);
        return 1;
    }
}
