package net.rs256.flae.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.Library;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.exporting.ExportJob;
import net.rs256.flae.FLAEConfig;
import net.rs256.flae.FLAEMod;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.SOFTLoopback;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.IntBuffer;

/**
 * Sample-rate override for audio-only exports.
 *
 * <p>Flashback's own {@code MixinAudioLibrary} wraps this same
 * {@code alcCreateContext} call and, while exporting with audio, replaces the
 * attribute list with {@code {FORMAT_TYPE: FLOAT, CHANNELS: mono|stereo,
 * FREQUENCY: 48000}}. Because it builds that list inside its handler, the
 * frequency cannot be modified from outside — so this mixin registers at a
 * higher priority ({@code 1100} vs Flashback's default {@code 1000}), which
 * makes it the <em>outer</em> wrapper in the MixinExtras chain. When the
 * override is active it creates the loopback context itself (mirroring
 * Flashback's attribute list, custom frequency); otherwise it delegates to
 * {@code original.call}, i.e. Flashback's untouched behavior.
 *
 * <p>The OpenAL soft mixer then renders natively at the configured rate — no
 * resampling happens anywhere in FLAE. {@code MixinExportJob} adjusts the
 * per-frame sample count (the {@code 48000.0} constant) to match.
 */
@Mixin(value = Library.class, priority = 1100)
public abstract class MixinLibrary {

    @WrapOperation(method = "init", at = @At(value = "INVOKE", remap = false,
            target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J"))
    private long flae$customRateLoopbackContext(long deviceHandle, IntBuffer attrList, Operation<Long> original) {
        int rate = flae$customLoopbackRate();
        if (rate == 0) {
            return original.call(deviceHandle, attrList);
        }

        FLAEMod.LOGGER.info("[{}] Opening the export loopback context at {}Hz", FLAEMod.MOD_ID, rate);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int channels = Flashback.EXPORT_JOB.getSettings().stereoAudio()
                    ? SOFTLoopback.ALC_STEREO_SOFT : SOFTLoopback.ALC_MONO_SOFT;
            IntBuffer attrs = stack.callocInt(7)
                    .put(SOFTLoopback.ALC_FORMAT_TYPE_SOFT).put(SOFTLoopback.ALC_FLOAT_SOFT)
                    .put(SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT).put(channels)
                    .put(ALC10.ALC_FREQUENCY).put(rate)
                    .put(0).flip();
            return ALC10.alcCreateContext(deviceHandle, attrs);
        }
    }

    /** Returns the overridden rate, or 0 when Flashback's default path should run. */
    @Unique
    private static int flae$customLoopbackRate() {
        if (!Flashback.isExporting()) return 0;
        ExportJob job = Flashback.EXPORT_JOB;
        if (job == null || !job.getSettings().recordAudio()) return 0;

        FLAEConfig config = FLAEConfig.get();
        if (!config.audioOnlyExport || config.audioOnlySampleRate == 48000) return 0;
        return config.audioOnlySampleRate;
    }
}
