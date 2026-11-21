package dev.cptgummiball.omacore.cookie;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** FTB Teams API 2101.1.x (compile-only); only loaded when FTB Teams is installed. */
final class FtbTeamsBridge {
    private FtbTeamsBridge() {
    }

    static Optional<UUID> teamOf(ServerPlayer player) {
        FTBTeamsAPI.API api = FTBTeamsAPI.api();
        if (!api.isManagerLoaded()) {
            return Optional.empty();
        }
        return api.getManager().getTeamForPlayer(player).map(Team::getId);
    }

    static Collection<ServerPlayer> onlineMembers(ServerPlayer player) {
        FTBTeamsAPI.API api = FTBTeamsAPI.api();
        if (!api.isManagerLoaded()) {
            return List.of();
        }
        return api.getManager().getTeamForPlayer(player).map(Team::getOnlineMembers).orElse(List.of());
    }
}
