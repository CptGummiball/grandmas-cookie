package dev.cptgummiball.omacore;

import com.mojang.logging.LogUtils;
import dev.cptgummiball.omacore.cookie.GrandmaInteraction;
import dev.cptgummiball.omacore.credits.Credits;
import dev.cptgummiball.omacore.fairplay.FairPlayGuard;
import dev.cptgummiball.omacore.grandma.GrandmaKeeper;
import dev.cptgummiball.omacore.house.HouseManager;
import dev.cptgummiball.omacore.house.HouseProtection;
import dev.cptgummiball.omacore.house.KitchenDrawer;
import dev.cptgummiball.omacore.house.OmaCommands;
import dev.cptgummiball.omacore.house.RejoinHandler;
import dev.cptgummiball.omacore.story.StoryProgress;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/** Entry point of the Grandma's Cookie companion mod. */
@Mod(OmaCore.MOD_ID)
public final class OmaCore {
    public static final String MOD_ID = "oma_core";
    static final Logger LOGGER = LogUtils.getLogger();

    public OmaCore(IEventBus modBus, ModContainer container) {
        FairPlayGuard guard = FairPlayGuard.load();
        HouseManager house = HouseManager.load();
        RejoinHandler rejoin = new RejoinHandler();
        GrandmaKeeper keeper = new GrandmaKeeper();
        NeoForge.EVENT_BUS.register(guard);
        NeoForge.EVENT_BUS.register(house);
        NeoForge.EVENT_BUS.register(rejoin);
        NeoForge.EVENT_BUS.register(keeper); // after the house manager: same event, registration order
        NeoForge.EVENT_BUS.register(new GrandmaInteraction());
        NeoForge.EVENT_BUS.register(new KitchenDrawer());
        NeoForge.EVENT_BUS.register(new HouseProtection(house));
        NeoForge.EVENT_BUS.register(new StoryProgress());
        NeoForge.EVENT_BUS.register(Credits.class);
        modBus.addListener(Credits::registerPayload);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> OmaCommands.register(e.getDispatcher(), house, rejoin, keeper));
        LOGGER.info("[oma_core] {} loaded; fair-play guard with {} locked values; cottage {}x{}x{}",
                container.getModInfo().getVersion(), guard.lockCount(),
                house.cottage().sizeX(), house.cottage().sizeY(), house.cottage().sizeZ());
    }
}
