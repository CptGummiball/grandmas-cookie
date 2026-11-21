package dev.cptgummiball.omacore.cookie;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * The team a player delivers for: FTB Teams' effective team (party, or the player's own team), so the
 * cookie counts once per team as FTB Quests counts progress. Without FTB Teams the player is their own team.
 * FTB Teams classes are only touched through {@link FtbTeamsBridge} when the mod is loaded.
 */
final class TeamResolver {
    private TeamResolver() {
    }

    static UUID teamOf(ServerPlayer player) {
        if (ModList.get().isLoaded("ftbteams")) {
            return FtbTeamsBridge.teamOf(player).orElse(player.getUUID());
        }
        return player.getUUID();
    }

    static Collection<ServerPlayer> onlineTeamMembers(ServerPlayer player) {
        if (ModList.get().isLoaded("ftbteams")) {
            Collection<ServerPlayer> members = FtbTeamsBridge.onlineMembers(player);
            if (!members.isEmpty()) {
                return members;
            }
        }
        return List.of(player);
    }
}
