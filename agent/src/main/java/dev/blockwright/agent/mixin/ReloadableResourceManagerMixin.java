package dev.blockwright.agent.mixin;

import dev.blockwright.agent.AgentHooks;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReloadableResourceManager.class)
abstract class ReloadableResourceManagerMixin {
    @Inject(method = "registerReloadListener", at = @At("HEAD"), cancellable = true)
    private void blockwright$filterListener(PreparableReloadListener listener, CallbackInfo info) {
        if (!AgentHooks.keepsReloadListener(listener)) info.cancel();
    }
}
