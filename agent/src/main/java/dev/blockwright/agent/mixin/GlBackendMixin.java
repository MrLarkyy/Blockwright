package dev.blockwright.agent.mixin;

import com.mojang.renderpearl.backend.opengl.GlBackend;
import dev.blockwright.agent.AgentHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlBackend.class)
abstract class GlBackendMixin {
    @Inject(method = "createWindow", at = @At("RETURN"), cancellable = true)
    private void blockwright$retryWithoutSrgb(String title, int width, int height, long flags, CallbackInfoReturnable<Long> info) {
        if (info.getReturnValueJ() != 0L) return;
        long window = AgentHooks.openGlWindowWithoutSrgb(title, width, height, flags);
        if (window != 0L) info.setReturnValue(window);
    }
}
