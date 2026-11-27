package dev.blockwright.agent.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.blockwright.agent.AgentHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSystem.class)
abstract class RenderSystemMixin {
    @Inject(
        method = "initBackendSystem",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLInit;SDL_Init(I)Z")
    )
    private static void blockwright$beforeSdlInit(CallbackInfoReturnable<?> info) {
        AgentHooks.beforeSdlInit();
    }
}
