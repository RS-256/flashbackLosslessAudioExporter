package net.rs256.flae;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.rs256.flae.audio.WavFormat;

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

    /** On-disk wav sample format; applies to both the passive tap and audio-only exports. */
    public WavFormat wavFormat = WavFormat.PCM_F32;

    /**
     * Loopback render rate for audio-only exports. Must be one of
     * {@link #SUPPORTED_SAMPLE_RATES}; normal (video) exports always run at
     * Flashback's native 48000Hz regardless of this setting.
     */
    public int audioOnlySampleRate = 48000;

    public static final int[] SUPPORTED_SAMPLE_RATES = {44100, 48000, 96000};

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
                    return loaded.sanitize();
                }
            } catch (IOException | RuntimeException e) {
                FLAEMod.LOGGER.error("[{}] Failed to read {}, using defaults", FLAEMod.MOD_ID, path, e);
            }
        }
        return new FLAEConfig();
    }

    /** Repairs values a hand-edited or outdated config file may contain. */
    private FLAEConfig sanitize() {
        if (this.wavFormat == null) {
            this.wavFormat = WavFormat.PCM_F32;
        }
        boolean rateSupported = false;
        for (int rate : SUPPORTED_SAMPLE_RATES) {
            rateSupported |= rate == this.audioOnlySampleRate;
        }
        if (!rateSupported) {
            this.audioOnlySampleRate = 48000;
        }
        return this;
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
