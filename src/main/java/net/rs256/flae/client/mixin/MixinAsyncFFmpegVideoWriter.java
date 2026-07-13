package net.rs256.flae.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.moulberry.flashback.exporting.AsyncFFmpegVideoWriter;
import com.moulberry.flashback.exporting.ExportSettings;
import net.rs256.flae.FLAEMod;
import net.rs256.flae.audio.WavWriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Path;

@Mixin(value = AsyncFFmpegVideoWriter.class, remap = false)
public abstract class MixinAsyncFFmpegVideoWriter {

    /** Flashback hard-codes recorder.setSampleRate(48000). */
    @Unique
    private static final int FLAE_SAMPLE_RATE = 48000;

    @Unique
    private WavWriter flae$wavWriter;

    @Inject(
            method = "<init>(Lcom/moulberry/flashback/exporting/ExportSettings;Ljava/lang/String;)V",
            at = @At("TAIL")
    )
    private void flae$openWav(ExportSettings settings, String filename, CallbackInfo ci) {
        if (!settings.recordAudio()) return;

        Path wavPath = flae$deriveWavPath(settings.output());
        int channels = settings.stereoAudio() ? 2 : 1;
        try {
            this.flae$wavWriter = new WavWriter(wavPath, channels, FLAE_SAMPLE_RATE);
            FLAEMod.LOGGER.info("[{}] Mirroring lossless audio ({}ch, 32bit float, {}Hz) to {}",
                    FLAEMod.MOD_ID, channels, FLAE_SAMPLE_RATE, wavPath);
        } catch (IOException e) {
            FLAEMod.LOGGER.error("[{}] Could not open {} — lossless audio disabled for this export",
                    FLAEMod.MOD_ID, wavPath, e);
            this.flae$wavWriter = null;
        }
    }

    @Inject(method = "encode", at = @At("HEAD"))
    private void flae$mirrorAudio(NativeImage src, FloatBuffer audioBuffer, CallbackInfo ci) {
        WavWriter writer = this.flae$wavWriter;
        if (writer == null || writer.isClosed() || audioBuffer == null) return;

        try {
            writer.writeSamples(audioBuffer);
        } catch (IOException e) {
            FLAEMod.LOGGER.error("[{}] Failed writing wav samples — aborting lossless audio "
                    + "(video export continues)", FLAEMod.MOD_ID, e);
            flae$closeWav();
        }
    }

    @Inject(method = "finish", at = @At("TAIL"))
    private void flae$onFinish(CallbackInfo ci) {
        flae$closeWav();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void flae$onClose(CallbackInfo ci) {
        flae$closeWav();
    }

    @Unique
    private void flae$closeWav() {
        WavWriter writer = this.flae$wavWriter;
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException e) {
            FLAEMod.LOGGER.error("[{}] Failed to finalize wav header — file may be unreadable",
                    FLAEMod.MOD_ID, e);
        }
    }

    /**
     * same directory and base name as the video output, extension swapped
     * to {@code .wav}. If that collided with the video file itself,
     * {@code .audio.wav} is appended instead of overwriting it.
     */
    @Unique
    private static Path flae$deriveWavPath(Path videoOutput) {
        String name = videoOutput.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;

        Path parent = videoOutput.getParent();
        Path wavPath = parent != null ? parent.resolve(base + ".wav") : Path.of(base + ".wav");
        if (wavPath.equals(videoOutput)) {
            wavPath = parent != null ? parent.resolve(base + ".audio.wav") : Path.of(base + ".audio.wav");
        }
        return wavPath;
    }
}
