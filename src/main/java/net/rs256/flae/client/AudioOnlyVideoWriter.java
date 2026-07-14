package net.rs256.flae.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.exporting.VideoWriter;
import net.rs256.flae.FLAEMod;
import net.rs256.flae.audio.WavPaths;
import net.rs256.flae.audio.WavWriter;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Drop-in {@code VideoWriter} used in audio-only export mode: writes the
 * lossless wav and discards every video frame. No FFmpeg process is started.
 *
 * <p>Frames still arrive here through Flashback's download pipeline (kept
 * intact on purpose — progress UI, cancellation and the export-done thumbnail
 * all stay functional), so {@link #encode} owns and must free each image.
 */
public final class AudioOnlyVideoWriter implements VideoWriter {

    private final WavWriter wavWriter;

    public AudioOnlyVideoWriter(ExportSettings settings, String tempFileName) throws IOException {
        // Safety net: MixinExportJob normally skips Flashback's Files.move(temp, output)
        // in audio-only mode, but if that path ever regresses, an existing (empty)
        // temp file keeps the move from throwing and killing the export.
        Path tempFile = Path.of(tempFileName);
        if (!Files.exists(tempFile)) {
            Files.createFile(tempFile);
        }

        Path wavPath = WavPaths.derive(settings.output());
        int channels = settings.stereoAudio() ? 2 : 1;
        this.wavWriter = new WavWriter(wavPath, channels, 48000);
        FLAEMod.LOGGER.info("[{}] Audio-only export: writing {}ch 32bit float wav to {} (video pipeline disabled)",
                FLAEMod.MOD_ID, channels, wavPath);
    }

    @Override
    public void encode(NativeImage src, FloatBuffer audioBuffer) {
        if (src != null) {
            src.close();
        }
        if (audioBuffer == null || this.wavWriter.isClosed()) {
            return;
        }
        try {
            this.wavWriter.writeSamples(audioBuffer);
        } catch (IOException e) {
            FLAEMod.LOGGER.error("[{}] Failed writing wav samples — aborting audio-only export output",
                    FLAEMod.MOD_ID, e);
            closeQuietly();
        }
    }

    @Override
    public void finish() {
        closeQuietly();
    }

    @Override
    public void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        try {
            this.wavWriter.close();
        } catch (IOException e) {
            FLAEMod.LOGGER.error("[{}] Failed to finalize wav header — file may be unreadable",
                    FLAEMod.MOD_ID, e);
        }
    }
}
