package net.rs256.flae.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.exporting.VideoWriter;
import net.minecraft.client.Minecraft;
import net.rs256.flae.FLAEConfig;
import net.rs256.flae.FLAEMod;
import net.rs256.flae.client.AudioOnlyVideoWriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Path;

//? if <26.1 {
/*import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
*///?} else {
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.DeltaTracker;
//?}

@Mixin(value = ExportJob.class, remap = false)
public abstract class MixinExportJob {

    /** True only while this job actually runs with an {@link AudioOnlyVideoWriter}. */
    @Unique
    private boolean flae$audioOnlyUsed;

    /**
     * The render skip and the video-move skip must key on whether the audio-only
     * writer is REALLY in use — not just on the config flag — so that a fallback
     * to a normal FFmpeg export (e.g. wav open failure) still renders and moves
     * its video. createVideoWriter runs before doExport, so the flag is always
     * settled before the first render call.
     */
    @Unique
    private boolean flae$audioOnlyActive() {
        return this.flae$audioOnlyUsed;
    }

    @WrapOperation(method = "run", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/exporting/ExportJob;createVideoWriter(Lcom/moulberry/flashback/exporting/ExportSettings;Ljava/lang/String;)Lcom/moulberry/flashback/exporting/VideoWriter;"))
    private VideoWriter flae$substituteWriter(ExportSettings settings, String tempFileName,
                                              Operation<VideoWriter> original) {
        if (FLAEConfig.get().audioOnlyExport) {
            if (!settings.recordAudio()) {
                FLAEMod.LOGGER.warn("[{}] audio-only export is enabled but 'Record Audio' is off — "
                        + "doing a normal video export instead", FLAEMod.MOD_ID);
            } else {
                try {
                    AudioOnlyVideoWriter writer = new AudioOnlyVideoWriter(settings, tempFileName);
                    this.flae$audioOnlyUsed = true;
                    return writer;
                } catch (IOException e) {
                    FLAEMod.LOGGER.error("[{}] Could not start audio-only export, falling back to normal export",
                            FLAEMod.MOD_ID, e);
                }
            }
        }
        return original.call(settings, tempFileName);
    }

    @WrapOperation(method = "run", at = @At(value = "INVOKE",
            target = "Ljava/nio/file/Files;move(Ljava/nio/file/Path;Ljava/nio/file/Path;[Ljava/nio/file/CopyOption;)Ljava/nio/file/Path;"))
    private Path flae$suppressEmptyVideo(Path source, Path target, CopyOption[] options,
                                         Operation<Path> original) {
        if (this.flae$audioOnlyUsed) {
            FLAEMod.LOGGER.info("[{}] Audio-only export: not writing the (empty) video file {}",
                    FLAEMod.MOD_ID, target);
            return source;
        }
        return original.call(source, target, options);
    }

    //? if <26.1 {
    /*@WrapOperation(method = "doExport", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void flae$skipRender(GameRenderer instance, DeltaTracker deltaTracker, boolean renderLevel, Operation<Void> original) {
        if (!flae$audioOnlyActive()) {
            original.call(instance, deltaTracker, renderLevel);
            return;
        }
        // Skip the world render, but re-do the camera setup GameRenderer#renderLevel
        // would have done: the OpenAL listener is fed from this camera in
        // updateSoundSource, so it must keep following the tracked position.
        Minecraft mc = Minecraft.getInstance();
        Entity cameraEntity = mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity();
        if (mc.level != null && cameraEntity != null) {
            mc.gameRenderer.getMainCamera().setup(mc.level, cameraEntity,
                    !mc.options.getCameraType().isFirstPerson(),
                    mc.options.getCameraType().isMirrored(),
                    deltaTracker.getGameTimeDeltaPartialTick(true));
        }
    }
    *///?} else {
    @WrapOperation(method = "doExport", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/exporting/ExportJob;render(Lcom/mojang/blaze3d/pipeline/RenderTarget;Lnet/minecraft/client/DeltaTracker$Timer;)V"))
    private void flae$skipRender(RenderTarget renderTarget, DeltaTracker.Timer timer, Operation<Void> original) {
        if (!flae$audioOnlyActive()) {
            original.call(renderTarget, timer);
            return;
        }
        // Skip the world render entirely, but keep the pieces the audio path needs:
        // level.update() drains per-frame client bookkeeping, and the camera update
        // keeps the OpenAL listener (fed from this camera in updateSoundSource)
        // following the keyframed/tracked position.
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.update();
        }
        mc.gameRenderer.getMainCamera().update(timer);
    }
    //?}
}
