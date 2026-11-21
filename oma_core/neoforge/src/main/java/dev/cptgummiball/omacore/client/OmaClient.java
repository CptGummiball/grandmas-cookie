package dev.cptgummiball.omacore.client;

import dev.cptgummiball.omacore.OmaCore;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Musics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/** Client-only hooks of oma_core. Never loaded on a dedicated server. */
@EventBusSubscriber(modid = OmaCore.MOD_ID, value = Dist.CLIENT)
public final class OmaClient {
    private OmaClient() {
    }

    /** Called from the credits payload handler on the client's main thread. */
    public static void openCredits() {
        Minecraft.getInstance().setScreen(new CreditsScreen());
    }

    /** The vanilla credits music plays while Grandma's credits are open. */
    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event) {
        if (Minecraft.getInstance().screen instanceof CreditsScreen) {
            event.setMusic(Musics.CREDITS);
        }
    }
}
