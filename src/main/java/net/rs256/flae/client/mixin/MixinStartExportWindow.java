package net.rs256.flae.client.mixin;

import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.windows.StartExportWindow;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImInt;
import net.minecraft.client.resources.language.I18n;
import net.rs256.flae.FLAEConfig;
import net.rs256.flae.audio.WavFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StartExportWindow.class, remap = false)
public abstract class MixinStartExportWindow {

    @Inject(method = "render()V", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/ImGuiHelper;enumCombo(Ljava/lang/String;Ljava/lang/Enum;[Ljava/lang/Enum;)Ljava/lang/Enum;",
            shift = At.Shift.AFTER))
    private static void flae$drawAudioOnlyToggle(CallbackInfo ci) {
        FLAEConfig config = FLAEConfig.get();
        if (ImGui.checkbox(I18n.get("flae.export.audio_only"), config.audioOnlyExport)) {
            config.audioOnlyExport = !config.audioOnlyExport;
            FLAEConfig.save();
        }
        ImGuiHelper.tooltip(I18n.get("flae.export.audio_only_tooltip"));

        // wav sample format - affects the wav in both normal and audio-only exports
        flae$formatIndex.set(config.wavFormat.ordinal());
        if (ImGui.combo(I18n.get("flae.export.wav_format"), flae$formatIndex, WavFormat.labels())) {
            config.wavFormat = WavFormat.VALUES[flae$formatIndex.get()];
            FLAEConfig.save();
        }
        ImGuiHelper.tooltip(I18n.get("flae.export.wav_format_tooltip"));

        // wav sample rate — only meaningful in audio-only mode, where the loopback
        // device really renders at this rate (normal exports are fixed to 48000)
        if (config.audioOnlyExport) {
            flae$rateIndex.set(flae$rateToIndex(config.audioOnlySampleRate));
            if (ImGui.combo(I18n.get("flae.export.wav_sample_rate"), flae$rateIndex, SAMPLE_RATE_LABELS)) {
                config.audioOnlySampleRate = FLAEConfig.SUPPORTED_SAMPLE_RATES[flae$rateIndex.get()];
                FLAEConfig.save();
            }
            ImGuiHelper.tooltip(I18n.get("flae.export.wav_sample_rate_tooltip"));
        }
    }

    @Unique
    private static final ImInt flae$formatIndex = new ImInt();
    @Unique
    private static final ImInt flae$rateIndex = new ImInt();
    @Unique
    private static final String[] SAMPLE_RATE_LABELS = {"44100 Hz", "48000 Hz (native)", "96000 Hz"};

    @Unique
    private static int flae$rateToIndex(int rate) {
        for (int i = 0; i < FLAEConfig.SUPPORTED_SAMPLE_RATES.length; i++) {
            if (FLAEConfig.SUPPORTED_SAMPLE_RATES[i] == rate) {
                return i;
            }
        }
        return 1; // 48000
    }
}
