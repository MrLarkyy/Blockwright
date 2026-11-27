package dev.blockwright.agent.mixin;

import com.mojang.renderpearl.api.device.GpuBackend;
import dev.blockwright.agent.AgentHooks;
import net.minecraft.client.PreferredGraphicsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PreferredGraphicsApi.class)
abstract class PreferredGraphicsApiMixin {
    @Inject(method = "getBackendsToTry", at = @At("HEAD"), cancellable = true)
    private void blockwright$replaceBackends(CallbackInfoReturnable<GpuBackend[]> info) {
        GpuBackend[] replacement = AgentHooks.replacementBackends();
        if (replacement != null) info.setReturnValue(replacement);
    }
}
