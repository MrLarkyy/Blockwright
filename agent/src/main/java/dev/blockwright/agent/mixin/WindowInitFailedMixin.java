package dev.blockwright.agent.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.blockwright.agent.AgentHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.WindowInitFailed.class)
abstract class WindowInitFailedMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void blockwright$onCreated(String message, CallbackInfo info) {
        AgentHooks.onWindowInitFailed(message);
    }
}
