package net.rs256.flae.client.mixin;

import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.windows.StartExportWindow;
import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;
import net.rs256.flae.FLAEConfig;
import org.spongepowered.asm.mixin.Mixin;
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
    }
}
