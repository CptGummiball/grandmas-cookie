package dev.cptgummiball.omacore.cookie;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.Reader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;

/**
 * Grandma's voice (owner backlog 2026-11-21: spoken dialog lines, German and English, recorded with ElevenLabs).
 *
 * <p>When oma_core opens one of Grandma's dialogs it also plays {@code oma_core:voice.<lang>.<label>} to that player
 * only, in the {@code voice} sound category. {@code <lang>} is {@code de} for a German client and {@code en} otherwise.
 * The server only plays lines listed in the data file {@code data/oma_core/voice/lines.json}
 * ({@code {"de": ["greeting", ...], "en": [...]}}), which {@code scripts/voice_lines.py} writes from the audio files
 * actually present (together with the client's {@code sounds.json}). Without recordings nothing is played and nothing
 * is logged. A missing German line falls back to the English one.
 */
public final class GrandmaVoice {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation LINES = ResourceLocation.fromNamespaceAndPath("oma_core", "voice/lines.json");

    private GrandmaVoice() {
    }

    public static void play(ServerPlayer player, String label) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        String clientLanguage = player.clientInformation().language().toLowerCase(Locale.ROOT);
        String lang = clientLanguage.startsWith("de") && available(server, "de").contains(label) ? "de" : "en";
        if (!available(server, lang).contains(label)) {
            return;
        }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "playsound oma_core:voice." + lang + "." + label + " voice " + player.getGameProfile().getName());
    }

    private static Set<String> available(MinecraftServer server, String lang) {
        Set<String> out = new HashSet<>();
        Optional<Resource> resource = server.getResourceManager().getResource(LINES);
        if (resource.isEmpty()) {
            return out;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has(lang)) {
                for (JsonElement e : root.getAsJsonArray(lang)) {
                    out.add(e.getAsString());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[oma_core] voice lines {} unreadable: {}", LINES, e.toString());
        }
        return out;
    }
}
