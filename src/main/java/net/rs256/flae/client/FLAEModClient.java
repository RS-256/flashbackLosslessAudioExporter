package net.rs256.flae.client;

import net.fabricmc.api.ClientModInitializer;
import net.rs256.flae.FLAEMod;

public class FLAEModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FLAEMod.LOGGER.info("[{}] flashbackLosslessAudioExporter v{} loaded (mc{})",
                FLAEMod.MOD_ID, FLAEMod.VERSION, FLAEMod.MINECRAFT);
    }
}
