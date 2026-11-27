package dev.blockwright.agent.mixin;

import dev.blockwright.agent.AgentHooks;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "extract(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"), cancellable = true)
    private void blockwright$skipExtract(CallbackInfo info) {
        if (AgentHooks.skipsRender()) {
            AgentHooks.onSkippedExtract();
            info.cancel();
        }
    }

    @Inject(method = "render()V", at = @At("HEAD"), cancellable = true)
    private void blockwright$skipRender(CallbackInfo info) {
        if (AgentHooks.skipsRender()) info.cancel();
    }
}
