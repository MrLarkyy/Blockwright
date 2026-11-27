package dev.blockwright.agent.mixin;

import dev.blockwright.agent.AgentHooks;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
    @Inject(method = "loadLibrary", at = @At("HEAD"), cancellable = true)
    private void blockwright$skipLibrary(CallbackInfo info) {
        if (AgentHooks.blocksSoundLibrary()) info.cancel();
    }
}
