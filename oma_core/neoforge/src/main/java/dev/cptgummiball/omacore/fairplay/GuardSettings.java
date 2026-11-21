package dev.cptgummiball.omacore.fairplay;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * The guard's own settings in {@code config/oma_core/fair_play.properties}. Deliberately a plain file
 * and not a ModConfigSpec, so config GUIs (NeoForge's screen, MezzConfig GUI) do not list it (ADR-0005).
 * Only affects dedicated servers; singleplayer worlds without cheats are always guarded.
 */
public record GuardSettings(boolean enforceOnDedicatedServer) {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "enforceLockedValuesOnDedicatedServer";
    private static final String DEFAULT_FILE = """
            # Grandma's Cookie - fair-play guard (oma_core, ADR-0005)
            # Dedicated servers only: set to false to let the server operator change pack-locked config values.
            # Singleplayer worlds created without cheats are always guarded; this file cannot change that.
            enforceLockedValuesOnDedicatedServer=true
            """;

    static GuardSettings loadOrCreate() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("oma_core").resolve("fair_play.properties");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT_FILE, StandardCharsets.UTF_8);
            }
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            }
            return new GuardSettings(!"false".equalsIgnoreCase(p.getProperty(KEY, "true").strip()));
        } catch (IOException e) {
            LOGGER.error("[oma_core] cannot read {}; using safe default (enforce)", file, e);
            return new GuardSettings(true);
        }
    }
}
