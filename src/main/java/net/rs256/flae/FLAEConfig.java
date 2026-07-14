package net.rs256.flae;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tiny JSON config, persisted at {@code config/flae.json}.
 */
public final class FLAEConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static FLAEConfig instance;

    public boolean audioOnlyExport = false;

    public static FLAEConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("flae.json");
    }

    private static FLAEConfig load() {
        Path path = path();
        if (Files.exists(path)) {
            try {
                FLAEConfig loaded = GSON.fromJson(Files.readString(path), FLAEConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException | RuntimeException e) {
                FLAEMod.LOGGER.error("[{}] Failed to read {}, using defaults", FLAEMod.MOD_ID, path, e);
            }
        }
        return new FLAEConfig();
    }

    public static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(get()));
        } catch (IOException e) {
            FLAEMod.LOGGER.error("[{}] Failed to save {}", FLAEMod.MOD_ID, path, e);
        }
    }

    private FLAEConfig() {}
}
