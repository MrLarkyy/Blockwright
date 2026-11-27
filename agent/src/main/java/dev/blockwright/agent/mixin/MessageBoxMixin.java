package dev.blockwright.agent.mixin;

import com.mojang.blaze3d.platform.MessageBox;
import dev.blockwright.agent.AgentHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MessageBox.class)
abstract class MessageBoxMixin {
    @Inject(method = "error", at = @At("HEAD"), cancellable = true)
    private static void blockwright$replaceErrorBox(String message, CallbackInfo info) {
        if (AgentHooks.replacesErrorBox(message)) info.cancel();
    }
}
